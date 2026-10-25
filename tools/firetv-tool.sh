#!/usr/bin/env bash
# FireClear tool for macOS / Linux / Termux (Android tablet). Needs `adb` in PATH.
# It only ever runs `adb` commands against the device IP you type in.
set -u
cd "$(dirname "$0")" || exit 1

LAUNCHER="app.fireclear.launcher"
STOCK="com.amazon.tv.launcher"
DEFAULT_DNS="dns.adguard-dns.com"
FALLBACK=n

command -v adb >/dev/null 2>&1 || { echo "adb not found. See docs/GUIDE.md."; exit 1; }

read -rp "Fire TV IP address (e.g. 192.168.1.50): " IP
[[ $IP =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}(:[0-9]{1,5})?$ ]] || { echo "That doesn't look like an IP address."; exit 1; }
[[ $IP == *:* ]] || IP="$IP:5555"

A() { adb -s "$IP" "$@"; }
adb connect "$IP"
echo "If the TV asks 'Allow USB debugging?', tick 'Always allow' and press OK. Then re-run this tool."

# Only plain Android package names are ever passed to the device shell (prevents command injection).
pkgs() {
  tr -d '\r' < "$1" | grep -v '^[[:space:]]*#' | grep -E '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$'
}

# Only plain hostnames are ever passed to the device shell.
valid_host() {
  [[ $1 =~ ^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$ ]]
}

off() {
  local p=$1 out
  out=$(A shell pm disable-user --user 0 "$p" </dev/null 2>&1)
  if grep -qi 'disabled' <<<"$out"; then echo "[off]   $p"; return; fi
  if [[ $FALLBACK == y ]]; then
    out=$(A shell pm uninstall -k --user 0 "$p" </dev/null 2>&1)
    if grep -qi 'success' <<<"$out"; then echo "[gone]  $p"; return; fi
  fi
  echo "[skip]  $p"
}

on() {
  A shell pm enable --user 0 "$1" </dev/null >/dev/null 2>&1
  A shell cmd package install-existing "$1" </dev/null >/dev/null 2>&1
  echo "[back]  $1"
}

apply() {
  A shell pm list packages </dev/null > backup-before.txt
  echo "Saved your package list to backup-before.txt"
  while read -r p; do off "$p"; done < <(pkgs "$1")
  echo "Done. Reboot the stick (adb reboot) for the full effect."
}

dns_off() {
  A shell settings put global private_dns_mode off </dev/null
  A shell settings delete global private_dns_specifier </dev/null >/dev/null 2>&1
  echo "Private DNS is off."
}

dns_on() {
  local h
  read -rp "DNS hostname (Enter = $DEFAULT_DNS): " h
  h=${h:-$DEFAULT_DNS}
  valid_host "$h" || { echo "Invalid hostname."; return; }
  A shell settings put global private_dns_mode hostname </dev/null
  A shell settings put global private_dns_specifier "$h" </dev/null
  echo "Testing internet through $h (a few seconds)..."
  sleep 6
  if A shell ping -c 2 example.com </dev/null >/dev/null 2>&1; then
    echo "OK: ad-blocking DNS is on. Reboot the stick for the full effect."
  else
    echo "No internet with this DNS, switching it off again."
    dns_off
    echo "If the TV still has no internet, run: adb shell settings put global private_dns_mode off"
  fi
}

while true; do
  echo
  echo " 1) Remove ads only (safest)"
  echo " 2) Full debloat: ads, telemetry, junk (reversible)"
  echo " 3) Full debloat, harder: also remove what can't be disabled"
  echo " 4) Ad-blocking DNS ON"
  echo " 5) Ad-blocking DNS OFF"
  echo " 6) Undo debloat (bring everything back)"
  echo " 7) Install FireClear APK"
  echo " 8) Make the Home button open FireClear (disables Amazon home)"
  echo " 9) Back to Amazon home"
  echo " 0) Quit"
  read -rp "> " c
  case "$c" in
    1) FALLBACK=n; apply packages-ads.txt ;;
    2) FALLBACK=n; apply packages.txt ;;
    3) FALLBACK=y; apply packages.txt ;;
    4) dns_on ;;
    5) dns_off ;;
    6) for f in packages.txt packages-ads.txt; do
         while read -r p; do on "$p"; done < <(pkgs "$f")
       done ;;
    7) read -rp "Path to the FireClear .apk: " f
       A install -r "${f//\"/}" ;;
    8) if ! A shell pm list packages "$LAUNCHER" </dev/null | grep -q "$LAUNCHER"; then
         echo "Install FireClear first (option 7)."
       else
         A shell pm disable-user --user 0 "$STOCK" </dev/null
         A shell am start -a android.intent.action.MAIN -c android.intent.category.HOME </dev/null
         echo "If that was refused, use a Home-button remapper app instead (docs/GUIDE.md)."
       fi ;;
    9) A shell pm enable --user 0 "$STOCK" </dev/null ;;
    0) adb disconnect "$IP" >/dev/null 2>&1
       echo "Now turn ADB debugging and Apps from Unknown Sources OFF on the Fire TV."
       exit 0 ;;
  esac
done
