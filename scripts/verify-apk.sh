#!/usr/bin/env bash
# Usage: scripts/verify-apk.sh path/to/app.apk  - checks the BUILT apk, not just the source.
set -eu
APK=${1:?usage: verify-apk.sh app.apk}
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}

AAPT_BIN=""
for b in "$SDK"/build-tools/*/aapt2 "$SDK"/build-tools/*/aapt; do
  if [ -x "$b" ]; then
    AAPT_BIN="$b"
    break
  fi
done

[ -n "$AAPT_BIN" ] || { echo "Neither aapt2 nor aapt found (set ANDROID_HOME)"; exit 1; }

echo "Using tool: $AAPT_BIN"
echo "--- permissions in $APK"

# Dump permissions from APK
"$AAPT_BIN" dump permissions "$APK" > /tmp/perms.txt 2>&1 || true
cat /tmp/perms.txt

# Extract permission names matching android.permission.*
found_perms=$(grep -oE 'android\.permission\.[A-Z_]+' /tmp/perms.txt | sort -u || true)

echo "--- Extracted permissions:"
echo "$found_perms"

# Verify INTERNET is not present
if echo "$found_perms" | grep -q 'android\.permission\.INTERNET'; then
  echo "FAIL: INTERNET permission in built APK"
  exit 1
fi

# Verify only allowed permissions are present
extra=$(echo "$found_perms" | grep -vE 'android\.permission\.(ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|SET_WALLPAPER|QUERY_ALL_PACKAGES)' || true)
if [ -n "$extra" ]; then
  echo "FAIL: unexpected permissions:"
  echo "$extra"
  exit 1
fi

# Verify expected permissions are present
if ! echo "$found_perms" | grep -q 'android\.permission\.ACCESS_NETWORK_STATE'; then
  echo "FAIL: expected ACCESS_NETWORK_STATE permission not found in APK"
  exit 1
fi

echo "APK PERMISSION CHECK PASSED"
