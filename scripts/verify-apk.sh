#!/usr/bin/env bash
# Usage: scripts/verify-apk.sh path/to/app.apk  — checks the BUILT apk, not just the source.
set -eu
APK=${1:?usage: verify-apk.sh app.apk}
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
AAPT=$(ls "$SDK"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)
[ -x "$AAPT" ] || { echo "aapt2 not found (set ANDROID_HOME)"; exit 1; }
echo "--- permissions in $APK"
"$AAPT" dump permissions "$APK" | tee /tmp/perms.txt
extra=$(grep '^uses-permission' /tmp/perms.txt | grep -vE 'ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE' || true)
if [ -n "$extra" ]; then echo "FAIL: unexpected permissions:"; echo "$extra"; exit 1; fi
if grep -q 'INTERNET' /tmp/perms.txt; then echo "FAIL: INTERNET permission in built APK"; exit 1; fi
echo "APK PERMISSION CHECK PASSED"
