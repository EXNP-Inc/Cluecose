#!/usr/bin/env bash
# Make sure the xcodegen binary is present, then regenerate the Xcode project.
set -euo pipefail
cd "$(dirname "$0")/.."

XCODEGEN_VERSION=2.46.0

if [[ ! -x tools/xcodegen/bin/xcodegen ]]; then
    echo "── downloading xcodegen $XCODEGEN_VERSION ────────────────────────"
    mkdir -p tools
    curl -sL --max-time 120 -o tools/xcodegen.zip \
        "https://github.com/yonaskolb/XcodeGen/releases/download/$XCODEGEN_VERSION/xcodegen.zip"
    unzip -o -q tools/xcodegen.zip -d tools
    rm tools/xcodegen.zip
fi

tools/xcodegen/bin/xcodegen generate
echo "✓ generated Cluecose.xcodeproj"
