#!/usr/bin/env bash
# Bootstraps, builds, installs, and launches the Android app on a physical
# phone connected over USB. Requires a physical phone — BLE does not work in
# the emulator. Uses `adb reverse` so the app's default
# BACKEND_BASE_URL=http://localhost:8080 reaches your backend without any
# WiFi/IP setup — just run scripts/run-backend.sh first.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
adb="$HOME/Android/Sdk/platform-tools/adb"

if [[ ! -x "$adb" ]]; then
  echo "error: $adb not found — is the Android SDK installed at ~/Android/Sdk?" >&2
  exit 1
fi

devices="$("$adb" devices | awk 'NR>1 && $2=="device" {print $1}')"
if [[ -z "$devices" ]]; then
  cat >&2 <<'EOF'
error: no device found via `adb devices`.

Before running this script:
  1. Enable USB debugging on the phone (Settings -> About -> tap Build
     number 7x -> Developer options -> USB debugging).
  2. Plug it in and accept the RSA prompt on the phone.
  3. Re-run this script (or check `adb devices` yourself).
EOF
  exit 1
fi
echo "+ device connected: $devices"

if [[ ! -f "$repo_root/app/local.properties" ]]; then
  echo "+ writing app/local.properties"
  echo "sdk.dir=$HOME/Android/Sdk" > "$repo_root/app/local.properties"
fi

echo "+ adb reverse tcp:8080 tcp:8080"
"$adb" reverse tcp:8080 tcp:8080

cd "$repo_root/app"
echo "+ ./gradlew assembleDebug"
./gradlew assembleDebug

apk="$(find build/outputs/apk/debug -maxdepth 1 -name '*.apk' -print -quit)"
if [[ -z "$apk" ]]; then
  echo "error: no APK found under build/outputs/apk/debug after assembleDebug" >&2
  exit 1
fi
echo "+ adb install -r $apk"
"$adb" install -r "$apk"

echo "+ adb shell am start -n io.streetsense.app/.MainActivity"
"$adb" shell am start -n io.streetsense.app/.MainActivity

cat <<'EOF'

Launched. Before tapping Start on the phone:
  - Turn Location on at the system level and grant Precise (not
    Approximate) when prompted.
EOF
