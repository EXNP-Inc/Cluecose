#!/usr/bin/env bash
# Build, install, and LAUNCH Cluecose on the test phone (default: DS iPhone Duo).
# Usage:  scripts/install-launch-duo.sh [device-name-or-UDID]
set -euo pipefail
cd "$(dirname "$0")/.."

DEVICE="${1:-DS iPhone Duo}"
./scripts/build-run-ios.sh "$DEVICE"
echo "── Launching com.dsikic.cluecose on $DEVICE ─────────────────────────"
xcrun devicectl device process launch --device "$DEVICE" com.dsikic.cluecose
echo "✓ launched"
