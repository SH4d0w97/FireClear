# FireClear: complete install and cleanup guide

Target: Fire TV Stick 3rd gen (Fire OS 7). The stick and your computer or tablet must be on the **same Wi-Fi**.

**Contents:** [1 Prepare](#1-prepare-your-computer-or-tablet) · [2 Enable ADB](#2-enable-adb-on-the-fire-tv) · [3 Get the APK](#3-get-the-apk) · [4 Connect](#4-connect-and-run-the-tool) · [5 Recommended order](#5-recommended-order) · [6 Using FireClear](#6-using-fireclear) · [7 Lock down](#7-lock-it-down-again) · [Undo](#undo-and-rollback) · [Troubleshooting](#troubleshooting)

## 1. Prepare your computer or tablet

**Windows:** download Google *SDK Platform-Tools*, unzip it, add the folder to PATH (or copy this repo's `tools` folder next to `adb.exe`).
**macOS:** `brew install android-platform-tools`
**Linux:** `sudo apt install adb`
**Android tablet (no PC needed):**
1. Install **F-Droid** from f-droid.org (allow installs from your browser once), then install **Termux** from F-Droid. Do not use the Play Store build.
2. In Termux:
   ```bash
   pkg update -y && pkg upgrade -y
   pkg install -y git gh unzip android-tools
   termux-setup-storage          # tap Allow
   ```
3. Check: `git --version && gh --version && adb version`

## 2. Enable ADB on the Fire TV
1. **Settings > My Fire TV > About**, highlight the device name and press **Select 7 times**.
2. Back to **My Fire TV > Developer Options**: **ADB debugging ON**, **Apps from Unknown Sources ON**.
3. Note the IP: **Settings > My Fire TV > About > Network**.

## 3. Get the APK
- **Release (easiest):** [Releases](../../../releases/latest). Download `FireClear-vX.Y.apk` and `SHA256SUMS.txt`, then verify:
  ```bash
  sha256sum -c SHA256SUMS.txt        # Windows: certutil -hashfile FireClear-v2.3.apk SHA256
  ```
- **Build it yourself (most trustworthy):** see [BUILD.md](BUILD.md). GitHub Actions can build it for free from your tablet.

Put the APK and this repo's `tools` folder somewhere you can reach. On Termux: `cd ~/storage/downloads`.

## 4. Connect and run the tool
```bash
cd tools
bash firetv-tool.sh          # Windows: firetv-tool.bat
```
Enter the Fire TV IP. The first time, the TV asks **Allow USB debugging?**: tick **Always allow**, press OK, run the tool again.
Manual check: `adb connect IP:5555 && adb devices` should list the stick as `device`.

| # | Option | Notes |
|---|---|---|
| 1 | Remove ads only | Ad ID, recommendations, tracking, shopping. **Safest.** |
| 2 | Full debloat | Everything in `packages.txt`. Disable only. |
| 3 | Full debloat, harder | Also removes, for your user, packages that refuse to be disabled. |
| 4 | Ad-blocking DNS ON | Hostname default `dns.adguard-dns.com`; you can type another. Auto-reverts if the internet breaks. |
| 5 | Ad-blocking DNS OFF | |
| 6 | Undo debloat | Re-enables or restores every listed package. |
| 7 | Install APK | Asks for the path to the `.apk`. |
| 8 | Home opens FireClear | Disables Amazon's home. Refuses if FireClear isn't installed. |
| 9 | Back to Amazon home | |
| 0 | Quit | Disconnects ADB. |

## 5. Recommended order
1. **7** install FireClear, then open it from the app list and **test it** (apps open, Settings tiles work). Don't touch Home yet.
2. **1** remove ads (or **2** for the full cleanup). Look for `[off]` lines; `[skip]` just means that package isn't on your stick.
3. **4** ad-blocking DNS. Wait for "OK".
4. `adb reboot`, then check that everything still works.
5. **8** make Home open FireClear. If refused, use a Home-button remapper app and map Home to FireClear.
6. Do the by-hand privacy toggles: [ADS.md](ADS.md#layer-4--privacy-toggles).

Checks: `adb shell pm list packages -d | grep amazon` (what's disabled) and `adb shell settings get global private_dns_mode` (should say `hostname`).

## 6. Using FireClear
- **OK** opens an app. **Hold OK** or press the **menu (≡)** button on an app: favourite, move left/right, hide, app info/uninstall.
- **Rows:** Favorites, Recent, Apps, Settings. Press **Up** from the first row to reach Wi-Fi, Customize and Settings in the top bar.
- **Settings row:** Wi-Fi, Bluetooth, Display, Sound, Apps, Storage, Date & Time, Language, Keyboard, Accessibility, Privacy, Developer, About. A tile only shows if your Fire OS can open that page. **All Settings** always opens the full Fire TV settings.
- **Customize:** 5 wallpapers, **Custom photo**, **Unhide all apps**, **Animations On/Off**.
- **Home** while already home glides back to the first tile. **Back** does nothing.
- Custom wallpaper: `adb push photo.jpg /sdcard/Android/data/app.fireclear.launcher/files/wallpaper.jpg`, then Customize > Custom photo (JPG/PNG, up to 25 MB, 1920×1080 ideal).
- Not available: Amazon's Find and Library tabs. Sleep: hold **Select + Play/Pause** on the remote.

## 7. Lock it down again
Menu **0**, then on the TV turn **ADB debugging OFF** and **Apps from Unknown Sources OFF**. ADB over Wi-Fi is a debug door into the stick; don't leave it open.

## Undo and rollback
| Want to undo | Do this |
|---|---|
| Debloat | Menu **6** |
| DNS | Menu **5** or `adb shell settings put global private_dns_mode off` |
| Home | Menu **9** or `adb shell pm enable --user 0 com.amazon.tv.launcher` |
| FireClear | `adb uninstall app.fireclear.launcher` |
| Everything | Settings > My Fire TV > Reset to Factory Defaults |

## Troubleshooting
| Problem | Fix |
|---|---|
| `unauthorized` / `failed to authenticate` | Accept the prompt on the TV, tick Always allow, run again |
| `cannot connect` | Same Wi-Fi? ADB debugging on? Correct IP? Toggle ADB debugging off and on |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Debug builds change signature: `adb uninstall app.fireclear.launcher`, then install again (favourites reset) |
| Menu 8 refused | Home-button remapper app (Button Mapper style) mapped to FireClear |
| Black screen / no home | `adb connect IP:5555` then `adb shell pm enable --user 0 com.amazon.tv.launcher` |
| No internet after DNS on | `adb shell settings put global private_dns_mode off` |
| Casting from phone stopped | Re-enable `com.amazon.whisperplay.contracts` ([DEBLOAT.md](DEBLOAT.md)) |
| Ads or slowness back after an update | Amazon re-enabled packages: run menu 1 or 2 and 8 again |
| Settings tile opens the main settings | Your Fire OS has no direct page for that tile |
| Apps missing | Hold OK on any app, or Customize > Unhide all apps |
