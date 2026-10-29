#!/usr/bin/env bash
# Static guardrails. Fails if the launcher ever gains network access, extra permissions,
# unauthorized third-party dependencies, dynamic code loading, extra exported components or binary blobs.
set -u
cd "$(dirname "$0")/.." || exit 1
fail=0
bad() { echo "FAIL: $*"; fail=1; }
ok()  { echo "ok:   $*"; }
M=app/src/main/AndroidManifest.xml

extra=$(grep -o 'android.permission\.[A-Z_]*' "$M" | sort -u | grep -v -e ACCESS_NETWORK_STATE -e ACCESS_WIFI_STATE -e SET_WALLPAPER -e QUERY_ALL_PACKAGES || true)
[ -z "$extra" ] && ok "permissions limited to launcher requirements (ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, SET_WALLPAPER, QUERY_ALL_PACKAGES)" || bad "unexpected permissions: $extra"
grep -q 'permission.INTERNET' "$M" && bad "INTERNET permission present" || ok "no INTERNET permission (app cannot reach the network)"

n=$(grep -c 'android:exported="true"' "$M")
[ "$n" -eq 1 ] && ok "exactly one exported component" || bad "expected 1 exported component, found $n"
grep -qE '<(service|receiver|provider)' "$M" && bad "service/receiver/provider declared" || ok "no services, receivers or providers"
grep -q 'android:allowBackup="false"' "$M" && ok "allowBackup=false" || bad "allowBackup must be false"
grep -q 'android:debuggable' "$M" && bad "debuggable flag set in manifest" || ok "not marked debuggable"

pat='java\.net\.|HttpURLConnection|okhttp|retrofit|WebView|ProcessBuilder|Runtime\.getRuntime|DexClassLoader|PathClassLoader|System\.load|loadLibrary|Class\.forName|java\.lang\.reflect|getDeclared|android\.util\.Base64|java\.util\.Base64|sendBroadcast|BroadcastReceiver|SmsManager|TelephonyManager|AccountManager|LocationManager|Camera|AudioRecord|MediaRecorder|AccessibilityService|SYSTEM_ALERT_WINDOW'
hits=$(grep -rEn "$pat" app/src/main/java || true)
[ -z "$hits" ] && ok "no network, reflection, dynamic-code, native or sensor APIs in source" || { bad "forbidden API use:"; echo "$hits"; }

thirdparty=$(grep -nE '^\s*(implementation|api)\b' app/build.gradle.kts | grep -v -E '(androidx\.|platform\(|composeBom)' || true)
[ -z "$thirdparty" ] && ok "zero unauthorized third-party dependencies (only official AndroidX / Compose TV framework)" || { bad "unauthorized third-party dependencies found:"; echo "$thirdparty"; }

blobs=$(find . -path ./.git -prune -o -path '*/build' -prune -o -path './.gradle' -prune -o -type f \( -name '*.jar' -o -name '*.so' -o -name '*.dex' -o -name '*.apk' -o -name '*.aar' -o -name '*.class' -o -name '*.exe' -o -name '*.dll' -o -name '*.bin' \) -print)
[ -z "$blobs" ] && ok "no binary blobs committed" || { bad "binary files in repo:"; echo "$blobs"; }

for pf in tools/packages*.txt; do
  badpk=$(tr -d '\r' < "$pf" | grep -v '^[[:space:]]*#' | grep -v '^[[:space:]]*$' | grep -vE '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$' || true)
  [ -z "$badpk" ] && ok "$pf contains only valid package names" || { bad "invalid lines in $pf:"; echo "$badpk"; }
done
net=$(grep -nEi 'curl|wget|\bnc\b|netcat|powershell|Invoke-WebRequest|certutil|bitsadmin|eval |base64 -d|bash -c|sh -c' tools/firetv-tool.sh tools/firetv-tool.bat || true)
[ -z "$net" ] && ok "tools only run adb (no downloads, no eval)" || { bad "suspicious commands in tools:"; echo "$net"; }

for pf in tools/packages*.txt; do
  nonamazon=$(tr -d '\r' < "$pf" | grep -v '^[[:space:]]*#' | grep -v '^[[:space:]]*$' | grep -v '^com\.amazon\.' || true)
  [ -z "$nonamazon" ] && ok "$pf only touches com.amazon.* packages" || bad "non-Amazon packages in $pf: $nonamazon"
done

[ $fail -eq 0 ] && echo "AUDIT PASSED" || { echo "AUDIT FAILED"; exit 1; }
