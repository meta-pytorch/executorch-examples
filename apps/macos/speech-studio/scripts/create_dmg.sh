#!/usr/bin/env bash
set -euo pipefail

APP_PATH="${1:-}"
DMG_PATH="${2:-}"
VOLUME_NAME="${3:-Speech Studio}"

if [[ -z "${APP_PATH}" || -z "${DMG_PATH}" ]]; then
  echo "Usage: $(basename "$0") /path/to/App.app /path/to/output.dmg [Volume Name]" >&2
  exit 1
fi

if [[ ! -d "${APP_PATH}" ]]; then
  echo "App not found: ${APP_PATH}" >&2
  exit 1
fi

APP_NAME="$(basename "${APP_PATH}")"
WORK_DIR="$(mktemp -d)"
STAGING_DIR="${WORK_DIR}/staging"
DMG_RW="${WORK_DIR}/tmp.dmg"

mkdir -p "${STAGING_DIR}"
cp -R "${APP_PATH}" "${STAGING_DIR}/"
ln -s /Applications "${STAGING_DIR}/Applications"

hdiutil create -volname "${VOLUME_NAME}" -srcfolder "${STAGING_DIR}" -ov -format UDRW "${DMG_RW}" >/dev/null

DEVICE="$(hdiutil attach -readwrite -noverify -noautoopen "${DMG_RW}" | awk 'NR==1{print $1}')"

osascript <<EOF
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

hdiutil detach "${DEVICE}" >/dev/null
hdiutil convert "${DMG_RW}" -format UDZO -o "${DMG_PATH}" >/dev/null
rm -rf "${WORK_DIR}"

echo "Created ${DMG_PATH}"
