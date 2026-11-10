#!/usr/bin/env bash
# Build Cluecose and install it on a device.
# Usage:
#   scripts/build-run-ios.sh                  # build for a generic device, list devices
#   scripts/build-run-ios.sh "DS iPhone Duo"  # build + install to the named device
#   scripts/build-run-ios.sh <UDID>           # build + install to a specific UDID
set -euo pipefail
cd "$(dirname "$0")/.."

export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
PROJECT=Cluecose.xcodeproj
SCHEME=Cluecose
CONFIG=${CONFIG:-Debug}
DERIVED=build/dd-ios

if [[ $# -ge 1 ]]; then
    DEVICE=$1
    # Resolve a friendly name to the UDID; xcodebuild `id=` needs the UDID.
    if [[ "$DEVICE" =~ ^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}- ]]; then
        UDID=$DEVICE
    else
        ROW=$(xcrun devicectl list devices 2>/dev/null | grep -E "^[[:space:]]*$DEVICE[[:space:]]" | head -1)
        UDID=$(printf '%s\n' "$ROW" | grep -Eo '[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}' | head -1)
        if [[ -z "$UDID" ]]; then
            echo "✗ device '$DEVICE' not found in 'devicectl list devices'"
            exit 1
        fi
        STATE=$(printf '%s\n' "$ROW" | grep -Eo 'unavailable|connected|available \(paired\)|unpaired|shutdown' | head -1)
        if [[ "$STATE" != "connected" && "$STATE" != "available (paired)" ]]; then
            echo "✗ device '$DEVICE' is not connected (state: ${STATE:-unknown})."
            echo "  Unlock the phone and check the USB/Wi-Fi connection, then retry."
            exit 1
        fi
    fi
    DEST="platform=iOS,id=$UDID"
else
    DEVICE=""
    DEST="generic/platform=iOS"
fi

[[ -x tools/xcodegen/bin/xcodegen ]] || ./scripts/bootstrap.sh

echo "── Building $SCHEME ($CONFIG, destination: $DEST) ──────────────────"
xcodebuild \
    -project "$PROJECT" \
    -scheme "$SCHEME" \
    -configuration "$CONFIG" \
    -destination "$DEST" \
    -derivedDataPath "$DERIVED" \
    -allowProvisioningUpdates \
    build 2>&1 | tail -8

APP="$DERIVED/Build/Products/$CONFIG-iphoneos/Cluecose.app"
if [[ ! -d "$APP" ]]; then
    echo "✗ build product not found at $APP"
    exit 1
fi
echo "✓ built $APP"

if [[ -n "$DEVICE" ]]; then
    echo "── Installing on $DEVICE ─────────────────────────────────────────"
    xcrun devicectl device install app --device "$DEVICE" "$APP"
    echo "✓ installed"
else
    xcrun devicectl list devices || true
fi
