#!/usr/bin/env bash
# Wait for the target device to become available, then build+install+launch.
# Usage: scripts/wait-install-launch.sh <device-name-or-UDID>
set -uo pipefail
cd "$(dirname "$0")/.."
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer

TARGET="${1:-00008160-001C44440E80000A}"

for i in $(seq 1 120); do
    ROW=$(xcrun devicectl list devices 2>/dev/null | grep "$TARGET" | head -1)
    STATE=$(printf '%s\n' "$ROW" | grep -Eo 'unavailable|connected|available \(paired\)|unpaired|shutdown' | head -1)
    if [[ "$STATE" == "connected" || "$STATE" == "available (paired)" ]]; then
        echo "✓ device '$TARGET' is available (attempt $i) — building, installing, launching…"
        if bash scripts/install-launch-duo.sh "$TARGET"; then
            echo "✓ done. Process check:"
            xcrun devicectl device info processes --device "$TARGET" 2>/dev/null | grep -i cluecose || echo "(process list empty; app may need manual launch)"
            exit 0
        else
            echo "✗ install/launch failed"
            exit 1
        fi
    fi
    sleep 30
done
echo "✗ device '$TARGET' still unavailable after 60 min of waiting"
exit 1
