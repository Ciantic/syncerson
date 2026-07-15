#!/usr/bin/env bash
set -euo pipefail

echo "=== Building APK ==="
./gradlew assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"

echo "=== Sharing via KDE Connect ==="
DEVICE=$(kdeconnect-cli -l 2>/dev/null | head -1 | cut -d' ' -f2 | tr -d ':')
if [ -z "${DEVICE}" ]; then
    echo "ERROR: No KDE Connect device found"
    exit 1
fi
kdeconnect-cli -n "${DEVICE}" --share "${APK}"

echo "=== Done ==="
