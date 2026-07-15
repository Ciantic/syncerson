#!/usr/bin/env bash
set -euo pipefail

SDK_DIR="${HOME}/android/sdk"
AVD="Pixel_10_-_36.1"

# Check if already running
if "${SDK_DIR}/platform-tools/adb" devices 2>/dev/null | grep -q 'emulator-5554.*device'; then
    echo "=== Emulator already running ==="
    exit 0
fi

echo "=== Starting emulator: ${AVD} ==="
"${SDK_DIR}/emulator/emulator" -avd "${AVD}" -gpu host &

echo "=== Waiting for boot ==="
while [ "$("${SDK_DIR}/platform-tools/adb" -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" != "1" ]; do
    sleep 2
done

echo "=== Emulator ready ==="
