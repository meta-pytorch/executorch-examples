#!/usr/bin/env bash
#
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.
#
# Verify that an ExecuWhisper DMG mounts cleanly and that the bundled app
# and helpers all carry valid codesign signatures.

set -euo pipefail

DMG_PATH="${1:-}"

if [[ -z "${DMG_PATH}" ]]; then
  echo "Usage: $(basename "$0") /path/to/ExecuWhisper.dmg" >&2
  exit 1
fi

if [[ ! -f "${DMG_PATH}" ]]; then
  echo "Error: DMG not found: ${DMG_PATH}" >&2
  exit 1
fi

hdiutil verify "${DMG_PATH}" >/dev/null

ATTACH_OUTPUT="$(hdiutil attach -readonly -nobrowse -noautoopen "${DMG_PATH}")"
DEVICE="$(printf "%s\n" "${ATTACH_OUTPUT}" | awk -F'\t' '/\/Volumes\// {print $1; exit}' | xargs)"
MOUNT="$(printf "%s\n" "${ATTACH_OUTPUT}" | awk -F'\t' '/\/Volumes\// {print $NF; exit}' | sed 's/^[[:space:]]*//')"
cleanup() {
  if [[ -n "${DEVICE:-}" ]]; then
    hdiutil detach "${DEVICE}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

APP_PATH="${MOUNT}/ExecuWhisper.app"
if [[ ! -d "${APP_PATH}" ]]; then
  echo "Error: ExecuWhisper.app missing from DMG" >&2
  exit 1
fi

codesign --verify --deep --strict --verbose=2 "${APP_PATH}" >/dev/null

echo "App signature:"
codesign -dv "${APP_PATH}" 2>&1 | grep -E "Identifier|Authority|TeamIdentifier|Runtime" || true

for entry in \
  "${APP_PATH}/Contents/Resources/parakeet_helper" \
  "${APP_PATH}/Contents/Resources/lfm25_formatter_helper" \
  "${APP_PATH}/Contents/Resources/libomp.dylib" \
  "${APP_PATH}/Contents/Resources/ExecuWhisper Paste Helper.app"; do
  if [[ ! -e "${entry}" ]]; then
    echo "Error: Missing signed entry: ${entry}" >&2
    exit 1
  fi
  codesign --verify --strict --verbose=2 "${entry}" >/dev/null
  echo "Signed entry: $(basename "${entry}")"
  codesign -dv "${entry}" 2>&1 | grep -E "Identifier|Authority|TeamIdentifier|Runtime" || true
done

echo "Release verification passed: ${DMG_PATH}"
