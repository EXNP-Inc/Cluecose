#!/usr/bin/env bash
#
# build-all.sh — build + test Cluecose on BOTH platforms in parallel.
#
# Verification is SIMULATOR / no-device only (never a physical device):
#   iOS     — swift test (shared CluecoseCore golden tests)
#             + xcodebuild for the iOS SIMULATOR
#             + simctl install + launch on a booted iPhone simulator
#   Android — gradle :core:test (shared golden tests, same fixtures)
#             + gradle :app:assembleDebug
#
# Both sides run concurrently; the script exits non-zero if EITHER side fails.
#
# Usage:  scripts/build-all.sh
#
# Environment overrides:
#   XCODE            — Xcode.app path (default /Applications/Xcode.app)
#   JAVA_HOME        — JDK (default /opt/homebrew/opt/openjdk@17)
#   GRADLE           — gradle binary (default $HOME/tools/gradle-8.10.2/bin/gradle)
#   IOS_SIM_NAME     — iPhone simulator name (default "iPhone 17")
#
set -u

cd "$(dirname "$0")/.."
ROOT="$(pwd)"

XCODE="${XCODE:-/Applications/Xcode.app}"
export DEVELOPER_DIR="$XCODE/Contents/Developer"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export JAVA_HOME
GRADLE="${GRADLE:-$HOME/tools/gradle-8.10.2/bin/gradle}"
IOS_SIM_NAME="${IOS_SIM_NAME:-iPhone 17}"

APP_BUNDLE_ID="com.dsikic.cluecose"

log()  { printf '\n\033[1;34m[build-all]\033[0m %s\n' "$*"; }
fail() { printf '\n\033[1;31m[build-all] FAIL\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m[build-all] OK\033[0m %s\n' "$*"; }

mkdir -p build/parallel
IOS_LOG="build/parallel/ios.log"
AND_LOG="build/parallel/android.log"

# ---------------------------------------------------------------------------
# iOS: shared-core tests + simulator build + install/launch on the simulator.
# ---------------------------------------------------------------------------
ios_side() {
  log "iOS side: starting (simulator '$IOS_SIM_NAME')"
  {
    echo "== swift test (CluecoseCore shared core) =="
    ( cd "$ROOT/CluecoseCore" && swift test ) || { echo "SWIFT_TEST_FAILED"; exit 1; }

    echo "== xcodebuild (iOS simulator) =="
    xcodebuild \
      -project "$ROOT/Cluecose.xcodeproj" \
      -scheme Cluecose \
      -destination "platform=iOS Simulator,name=$IOS_SIM_NAME" \
      -derivedDataPath "$ROOT/build/dd-sim" \
      -configuration Debug \
      CODE_SIGNING_ALLOWED=NO build || { echo "XCODEBUILD_FAILED"; exit 1; }

    APP="$ROOT/build/dd-sim/Build/Products/Debug-iphonesimulator/Cluecose.app"
    if [ ! -d "$APP" ]; then echo "NO_APP_PRODUCT"; exit 1; fi

    echo "== simctl install + launch =="
    # Bootstrap/boot the simulator if it isn't already (best effort).
    xcrun simctl bootstatus "$IOS_SIM_NAME" >/dev/null 2>&1 || xcrun simctl boot "$IOS_SIM_NAME" >/dev/null 2>&1
    xcrun simctl install "$IOS_SIM_NAME" "$APP" || { echo "SIM_INSTALL_FAILED"; exit 1; }
    xcrun simctl launch "$IOS_SIM_NAME" "$APP_BUNDLE_ID" || { echo "SIM_LAUNCH_FAILED"; exit 1; }
    echo "SIM_OK"
  } 2>&1
}

# ---------------------------------------------------------------------------
# Android: shared-core golden tests + assembleDebug.
# ---------------------------------------------------------------------------
android_side() {
  log "Android side: starting (gradle $GRADLE)"
  {
    if [ ! -x "$GRADLE" ]; then echo "GRADLE_MISSING: $GRADLE"; exit 1; fi
    cd "$ROOT/android"

    echo "== gradle :core:test (shared golden tests) =="
    "$GRADLE" :core:test || { echo "CORE_TEST_FAILED"; exit 1; }

    echo "== gradle :app:assembleDebug =="
    "$GRADLE" :app:assembleDebug || { echo "ASSEMBLE_FAILED"; exit 1; }

    APK=$(ls app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1)
    if [ -z "$APK" ]; then echo "NO_APK"; exit 1; fi
    echo "APK: $APK"
    echo "ANDROID_OK"
  } 2>&1
}

# ---------------------------------------------------------------------------
# Run both sides in parallel; fail if either fails.
# ---------------------------------------------------------------------------
log "Running iOS and Android in parallel (logs: build/parallel/{ios,android}.log)"

ios_side   > "$IOS_LOG"   &
IOS_PID=$!
android_side > "$AND_LOG" &
AND_PID=$!

IOS_RC=0
AND_RC=0
wait "$IOS_PID"   || IOS_RC=$?
wait "$AND_PID"   || AND_RC=$?

tail -n 20 "$IOS_LOG"
tail -n 20 "$AND_LOG"

IOS_MARK="SIM_OK";  [ $IOS_RC -ne 0 ]   && IOS_MARK=""
AND_MARK="ANDROID_OK"; [ $AND_RC -ne 0 ] && AND_MARK=""

if [ $IOS_RC -ne 0 ] || $AND_RC -ne 0; then
  [ -n "$IOS_MARK" ]   || fail "iOS side (rc=$IOS_RC) — see $IOS_LOG"
  [ -n "$AND_MARK" ] || fail "Android side (rc=$AND_RC) — see $AND_LOG"
  exit 1
fi

ok "iOS side: built for simulator + installed + launched"
ok "Android side: :core tests green + debug APK built"
ok "build-all: BOTH PLATFORMS GREEN (simulator / no-device)"
