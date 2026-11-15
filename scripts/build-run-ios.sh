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
    # Glob (not `[[ =~ ]]` nor `grep -E`): macOS bash 3.2 and BSD grep 2.6
    # both mishandle a quantified class followed by a dash, which made the
    # UDID fall through to name resolution. Accept any hex+dash token 10-40
    # chars (covers 25-char and 36-char UDID forms).
    if [[ ${#DEVICE} -ge 10 && ${#DEVICE} -le 40 && -z ${DEVICE//[0-9A-Fa-f-]/} && "$DEVICE" == *-* ]]; then
        UDID=$DEVICE
    else
        # `|| true` on every grep so a no-match can't trip `set -e`+`pipefail`
        # and kill the script silently (this exact bug hid a parse failure).
        ROW=$(xcrun devicectl list devices 2>/dev/null | grep -E "^[[:space:]]*$DEVICE[[:space:]]" | head -1 || true)
        if [[ -z "$ROW" ]]; then
            echo "✗ device '$DEVICE' not found in 'devicectl list devices'"
            exit 1
        fi
        # Grab the token immediately before " (UDID)" — works for BOTH the
        # 25-char (8-16) physical UDID and the 36-char (8-4-4-4-12) form.
        # (A fixed 8-4-4-4-12 regex here silently failed on the 25-char form.)
        UDID=$(printf '%s\n' "$ROW" | sed -E 's/.* ([0-9A-Fa-f-]+) \(UDID\).*/\1/')
        if [[ -z "$UDID" ]]; then
            echo "✗ could not parse a UDID from the '$DEVICE' row:"
            printf '  %s\n' "$ROW"
            exit 1
        fi
        STATE=$(printf '%s\n' "$ROW" | grep -Eo 'unavailable|connected|available \(paired\)|unpaired|shutdown' | head -1 || true)
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

# Inject the (gitignored) LLM key into the bundle resource, then verify the
# finished app actually carries it — a missing key silently disables food
# analysis, so make that visible at build time.
./scripts/refresh-food-key.sh
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
if [[ -f "$APP/FoodLLMKey.json" ]]; then
    echo "✓ bundle carries FoodLLMKey.json (food analysis enabled)"
else
    echo "⚠ bundle does NOT carry FoodLLMKey.json — food analysis will be DISABLED in this build (log the key at /Users/dsikic/cluecose-android/.food_key and rebuild)."
fi
echo "✓ built $APP"

if [[ -n "$DEVICE" ]]; then
    echo "── Installing on $DEVICE ─────────────────────────────────────────"
    xcrun devicectl device install app --device "$DEVICE" "$APP"
    echo "✓ installed"
else
    xcrun devicectl list devices || true
fi
