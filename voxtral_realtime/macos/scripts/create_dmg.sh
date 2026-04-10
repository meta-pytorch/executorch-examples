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
VOLUME_NAME="${3:-Voxtral Realtime}"

if [[ -z "${APP_PATH}" || -z "${DMG_PATH}" ]]; then
  echo "Usage: $(basename "$0") /path/to/App.app /path/to/output.dmg [Volume Name]" >&2
  exit 1
fi

if [[ ! -d "${APP_PATH}" ]]; then
  echo "Error: App not found: ${APP_PATH}" >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Validate bundled resources — refuse to create a DMG with missing files
# ---------------------------------------------------------------------------
RESOURCES="${APP_PATH}/Contents/Resources"
REQUIRED_FILES=(
  "voxtral_realtime_runner"
  "libomp.dylib"
  "libc++.1.dylib"
  "model-metal-int4.pte"
  "preprocessor.pte"
  "tekken.json"
)

MISSING=()
for f in "${REQUIRED_FILES[@]}"; do
  if [[ ! -f "${RESOURCES}/${f}" ]]; then
    MISSING+=("${f}")
  fi
done

if [[ ${#MISSING[@]} -gt 0 ]]; then
  echo "Error: The following required files are missing from ${RESOURCES}:" >&2
  for f in "${MISSING[@]}"; do
    echo "  - ${f}" >&2
  done
  echo "" >&2
  echo "The DMG must be self-contained. Make sure you:" >&2
  echo "  1. Downloaded model files:  hf download mistralai/Voxtral-Mini-4B-Realtime-2602-Executorch --local-dir ~/voxtral_realtime_quant_metal" >&2
  echo "  2. Built the runner:        conda activate et-metal && cd ~/executorch && make voxtral_realtime-metal" >&2
  echo "  3. Installed libomp:        brew install libomp" >&2
  echo "  4. Built in Release mode:   xcodebuild -scheme VoxtralRealtime -configuration Release" >&2
  exit 1
fi

echo "✓ All required files present in app bundle"

# ---------------------------------------------------------------------------
# Create DMG with drag-to-Applications layout
# ---------------------------------------------------------------------------
APP_NAME="$(basename "${APP_PATH}")"
WORK_DIR="$(mktemp -d)"
STAGING_DIR="${WORK_DIR}/staging"
DMG_RW="${WORK_DIR}/tmp.dmg"

mkdir -p "${STAGING_DIR}"
cp -R "${APP_PATH}" "${STAGING_DIR}/"
ln -s /Applications "${STAGING_DIR}/Applications"

hdiutil create -volname "${VOLUME_NAME}" -srcfolder "${STAGING_DIR}" -ov -format UDRW "${DMG_RW}" >/dev/null

DEVICE="$(hdiutil attach -readwrite -noverify -noautoopen "${DMG_RW}" | awk 'NR==1{print $1}')"

osascript <<EOF 2>/dev/null && echo "✓ DMG window layout configured" || echo "· Skipped DMG window layout (Finder not available in this context)"
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
hdiutil convert "${DMG_RW}" -format UDZO -o "${DMG_PATH}" >/dev/null
rm -rf "${WORK_DIR}"

DMG_SIZE=$(du -sh "${DMG_PATH}" | cut -f1)
echo "✓ Created ${DMG_PATH} (${DMG_SIZE})"
