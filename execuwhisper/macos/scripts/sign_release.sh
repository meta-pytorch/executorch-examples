#!/usr/bin/env bash
#
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.
#
# Re-sign an ExecuWhisper.app bundle and the helpers it ships with using a
# given codesign identity. Used by scripts/build.sh after xcodebuild for
# Developer ID / Apple Development signing flows; safe to re-run.

set -euo pipefail

APP_PATH="${1:-}"
IDENTITY="${2:-}"

if [[ -z "${APP_PATH}" || -z "${IDENTITY}" ]]; then
  echo "Usage: $(basename "$0") /path/to/ExecuWhisper.app SIGNING_IDENTITY_OR_SHA" >&2
  exit 1
fi

if [[ ! -d "${APP_PATH}" ]]; then
  echo "Error: App not found: ${APP_PATH}" >&2
  exit 1
fi

RESOURCES="${APP_PATH}/Contents/Resources"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
HELPER_ENT="$(mktemp /tmp/execuwhisper_helper_entitlements.XXXXXX.plist)"
cleanup() {
  rm -f "${HELPER_ENT}"
}
trap cleanup EXIT

cat > "${HELPER_ENT}" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>com.apple.security.cs.disable-library-validation</key>
  <true/>
</dict>
</plist>
PLIST

sign_if_present() {
  local path="$1"
  shift
  if [[ -e "${path}" ]]; then
    codesign --force --options runtime "$@" --sign "${IDENTITY}" "${path}"
  fi
}

sign_if_present "${RESOURCES}/libomp.dylib"
sign_if_present "${RESOURCES}/parakeet_helper" --entitlements "${HELPER_ENT}"
sign_if_present "${RESOURCES}/lfm25_formatter_helper" --entitlements "${HELPER_ENT}"
sign_if_present "${RESOURCES}/ExecuWhisper Paste Helper.app" --identifier "org.pytorch.executorch.ExecuWhisper.PasteHelper"

codesign \
  --force \
  --options runtime \
  --entitlements "${ROOT_DIR}/ExecuWhisper/ExecuWhisper.entitlements" \
  --sign "${IDENTITY}" \
  "${APP_PATH}"

codesign --verify --deep --strict --verbose=2 "${APP_PATH}"
