#!/usr/bin/env bash
#
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.
#
set -euo pipefail

APP_PATH="${1:-}"
DMG_PATH="${2:-}"
VOLUME_NAME="${3:-ExecuWhisper}"

if [[ -z "${APP_PATH}" || -z "${DMG_PATH}" ]]; then
  echo "Usage: $(basename "$0") /path/to/ExecuWhisper.app /path/to/output.dmg [Volume Name]" >&2
  exit 1
fi

if [[ ! -d "${APP_PATH}" ]]; then
  echo "Error: App not found: ${APP_PATH}" >&2
  exit 1
fi

RESOURCES="${APP_PATH}/Contents/Resources"
REQUIRED_FILES=(
  "parakeet_helper"
  "libomp.dylib"
)

MISSING=()
for file in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${RESOURCES}/${file}" ]]; then
    MISSING+=("${file}")
  fi
done

if [[ ${#MISSING[@]} -gt 0 ]]; then
  echo "Error: The following required files are missing from ${RESOURCES}:" >&2
  for file in "${MISSING[@]}"; do
    echo "  - ${file}" >&2
  done
  exit 1
fi

HAS_MODEL=false
if [[ -f "${RESOURCES}/model.pte" || -f "${RESOURCES}/tokenizer.model" ]]; then
  HAS_MODEL=true
  for file in model.pte tokenizer.model; do
    if [[ ! -f "${RESOURCES}/${file}" ]]; then
      echo "Error: ${file} is missing from ${RESOURCES}." >&2
      echo "The app appears to be a bundled-model build, so both model files must be present." >&2
      exit 1
    fi
  done
fi

if [[ "${HAS_MODEL}" == true ]]; then
  echo "✓ Creating self-contained bundled-model DMG"
else
  echo "✓ Creating lightweight DMG (model downloads on first launch)"
fi

APP_NAME="$(basename "${APP_PATH}")"
WORK_DIR="$(mktemp -d)"
STAGING_DIR="${WORK_DIR}/staging"
DMG_RW="${WORK_DIR}/tmp.dmg"

cleanup() {
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

mkdir -p "${STAGING_DIR}"
cp -R "${APP_PATH}" "${STAGING_DIR}/"
ln -s /Applications "${STAGING_DIR}/Applications"

hdiutil create -volname "${VOLUME_NAME}" -srcfolder "${STAGING_DIR}" -ov -format UDRW "${DMG_RW}" >/dev/null

DEVICE=""
MOUNTED=false
if ATTACH_OUTPUT="$(hdiutil attach -readwrite -noverify -noautoopen "${DMG_RW}" 2>/dev/null)"; then
  DEVICE="$(printf "%s\n" "${ATTACH_OUTPUT}" | awk 'NR==1{print $1}')"
  if [[ -n "${DEVICE}" ]]; then
    MOUNTED=true
    osascript <<EOF 2>/dev/null && echo "✓ DMG window layout configured" || echo "· Skipped DMG window layout (Finder unavailable)"
tell application "Finder"
  tell disk "${VOLUME_NAME}"
    open
    set current view of container window to icon view
    set toolbar visible of container window to false
    set statusbar visible of container window to false
    set the bounds of container window to {100, 100, 700, 420}
    set icon size of icon view options of container window to 128
    set arrangement of icon view options of container window to not arranged
    set position of item "${APP_NAME}" of container window to {150, 200}
    set position of item "Applications" of container window to {500, 200}
    update without registering applications
    delay 1
    close
  end tell
end tell
EOF
    hdiutil detach "${DEVICE}" >/dev/null 2>&1 || true
  fi
else
  echo "· Skipped DMG layout configuration (could not attach read-write image in this context)"
fi

if [[ -e "${DMG_PATH}" ]]; then
  rm -f "${DMG_PATH}"
fi

hdiutil convert "${DMG_RW}" -format UDZO -o "${DMG_PATH}" >/dev/null

DMG_SIZE="$(du -sh "${DMG_PATH}" | cut -f1)"
echo "✓ Created ${DMG_PATH} (${DMG_SIZE})"
