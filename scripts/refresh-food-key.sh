#!/usr/bin/env bash
# Inject the nutrition-estimation LLM key into the build — NEVER committed.
#
# The key lives in a gitignored file outside the repo:
#   /Users/dsikic/cluecose-android/.food_key   (one line, no newline)
#
# This script writes it into the two build inputs:
#   • source/Cluecose/Resources/FoodLLMKey.json   (iOS bundle resource;
#     read by FoodLLMConfig.bundledKey())
#   • android/app/src/main/assets/food_llm_key.json (Android app asset;
#     read by FoodLLMConfig.load())
#
# Both targets are gitignored (see .gitignore) so a `git add -A` — including
# one made by another agent sharing this working tree — can never pick the
# key up. If the key file is missing, the scripts print a warning and the
# builds ship WITHOUT a key: the food-log analysis then fails gracefully
# (the meal is logged with blank values + a visible "couldn't analyze"
# notice) instead of fabricating numbers.
set -uo pipefail
cd "$(dirname "$0")/.."

KEY_FILE="/Users/dsikic/cluecose-android/.food_key"
IOS_RES="source/Cluecose/Resources/FoodLLMKey.json"
ANDROID_ASSET="android/app/src/main/assets/food_llm_key.json"

if [[ ! -f "$KEY_FILE" ]]; then
    echo "⚠ food key file not found at $KEY_FILE — builds will ship without an LLM key (food analysis disabled, logged meals save with blank values)."
    exit 0
fi

KEY=$(head -1 "$KEY_FILE" | tr -d '\r\n')
umask 077

mkdir -p "$(dirname "$IOS_RES")" "$(dirname "$ANDROID_ASSET")"
printf '{"apiKey":"%s"}\n' "$KEY" > "$IOS_RES"
chmod 600 "$IOS_RES"
cp "$IOS_RES" "$ANDROID_ASSET"
chmod 600 "$ANDROID_ASSET"
echo "✓ food key injected (gitignored): $IOS_RES, $ANDROID_ASSET"
