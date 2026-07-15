#!/usr/bin/env bash
set -euo pipefail

SDK_DIR="${HOME}/android/sdk"
EMULATOR="emulator-5554"

echo "=== Building APK ==="
./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"

echo "=== Installing to ${EMULATOR} ==="
"${SDK_DIR}/platform-tools/adb" -s "${EMULATOR}" install -r "${APK}"

echo "=== Launching app ==="
"${SDK_DIR}/platform-tools/adb" -s "${EMULATOR}" shell am force-stop com.oksidi.syncerson
"${SDK_DIR}/platform-tools/adb" -s "${EMULATOR}" shell am start -n com.oksidi.syncerson/.MainActivity

echo "=== Done ==="
