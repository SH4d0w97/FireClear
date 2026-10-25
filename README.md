<div align="center">

# 🔥 FireClear

**A clean, ad-free tvOS-style home screen and one-menu debloat tool for Fire TV Stick**

[![build](https://github.com/SH4d0w97/FireClear/actions/workflows/build.yml/badge.svg)](https://github.com/SH4d0w97/FireClear/actions/workflows/build.yml)
![license](https://img.shields.io/badge/license-MIT-blue)
![Fire OS](https://img.shields.io/badge/Fire%20OS-7-orange)
![no internet](https://img.shields.io/badge/internet%20permission-none-brightgreen)
![zero dependencies](https://img.shields.io/badge/dependencies-0-brightgreen)

[**⬇ Download APK**](../../releases/latest) · [Full Guide](docs/GUIDE.md) · [Ad Removal Details](docs/ADS.md) · [Build It Yourself](docs/BUILD.md) · [Security](docs/SECURITY.md)

</div>

---

## What is FireClear?

Amazon's Fire TV home screen is full of autoplay video ads, banners, and background services you never asked for. **FireClear fixes that in two parts:**

| Part | What it does |
|---|---|
| 🏠 **Launcher** | Replaces Amazon's home screen with a calm, fast, tvOS-style interface — no ads, no banners |
| 🧹 **Debloat Tool** | A simple menu-driven script that disables Amazon's ad, tracking, and junk packages via ADB. **Everything is reversible.** |

---

## What the launcher looks like

```
 9:41                              [ Wi-Fi ]  [ Customize ]  [ Settings ]
 Sunday, 25 October

 FAVORITES (DOCK)
 ┌──────────────────────────────────────────────────────────────┐
 │  [ YouTube ]   [[ Prime Video ]]   [ Plex ]   [ Netflix ]    │  ← 4-App Dock Shelf
 └──────────────────────────────────────────────────────────────┘
                     Prime Video (lifts, scales & glows)

 APPS
 [ Kodi ]  [ Downloader ]  [ Photos ]  [ Browser ]  [ Files ]  ...
```

No libraries. No network. No background services. Zero dependencies.

---

## Quick Feature Overview

- ✅ **Ad-free home screen** — Amazon's ad-heavy launcher replaced entirely
- ✅ **One-click debloat** — disable ad IDs, telemetry, recommendations, junk apps
- ✅ **Ad-blocking DNS** — blocks ad/tracker domains for every app on the stick
- ✅ **Privacy toggles guide** — turn off the last data collection switches by hand
- ✅ **Full undo** — every single change can be reversed from the same menu
- ✅ **No internet permission** — the app literally cannot upload anything
- ✅ **Tablet-friendly** — works from an Android tablet with Termux, no PC needed

---

## 📋 Complete Step-by-Step Guide

> **Requirements:** Your Fire TV Stick and your computer/tablet must be on the **same Wi-Fi network.**

---

### Step 1 — Prepare your computer (or tablet)

Pick your platform and install ADB:

<details>
<summary>🪟 Windows</summary>

1. Download [SDK Platform-Tools](https://developer.android.com/studio/releases/platform-tools) from Google.
2. Unzip it anywhere (e.g. `C:\platform-tools`).
3. Open **Command Prompt** in that folder (Shift + Right-click → "Open command window here").
4. Test it: `adb version` — you should see a version number.

</details>

<details>
<summary>🍎 macOS</summary>

```bash
brew install android-platform-tools
adb version   # confirm it works
```

</details>

<details>
<summary>🐧 Linux</summary>

```bash
sudo apt install adb
adb version   # confirm it works
```

</details>

<details>
<summary>📱 Android Tablet (no PC needed — Termux)</summary>

1. Install **F-Droid** from [f-droid.org](https://f-droid.org) (allow installs from your browser once).
2. From F-Droid, install **Termux**. *(Do not use the Play Store version.)*
3. In Termux, run:
   ```bash
   pkg update -y && pkg upgrade -y
   pkg install -y git unzip android-tools
   termux-setup-storage    # tap Allow when prompted
   adb version             # confirm it works
   ```

</details>

---

### Step 2 — Enable ADB on the Fire TV

1. On your Fire TV: go to **Settings → My Fire TV → About**
2. Highlight the **device name** (e.g. "Fire TV Stick") and press **Select 7 times** rapidly
   - You'll see a toast: *"No need, you are already a developer"* — that means it worked ✅
3. Go back to **My Fire TV → Developer Options** and turn on:
   - ✅ **ADB Debugging** → ON
   - ✅ **Apps from Unknown Sources** → ON
4. Note your Fire TV's IP address: **Settings → My Fire TV → About → Network**
   - It looks like `192.168.x.x`

---

### Step 3 — Get the FireClear APK

**Option A — Download (easiest):**
Go to [Releases](../../releases/latest) and download `FireClear-vX.Y.apk` + `SHA256SUMS.txt`.

Verify the download hasn't been tampered with:
```bash
# macOS/Linux:
sha256sum -c SHA256SUMS.txt

# Windows:
certutil -hashfile FireClear-v2.3.apk SHA256
# compare the output against the hash in SHA256SUMS.txt
```

**Option B — Build it yourself (most trustworthy):**
See [docs/BUILD.md](docs/BUILD.md). GitHub Actions can build it for free — even from a tablet.

---

### Step 4 — Connect ADB to your Fire TV

Open a terminal/command prompt and run:
```bash
adb connect YOUR_TV_IP:5555
# example: adb connect 192.168.1.42:5555
```

**First time only:** a dialog will pop up on your TV asking *"Allow USB debugging?"*
→ Check **"Always allow from this computer"** → Press **OK**

Then confirm it's connected:
```bash
adb devices
# should show:  192.168.x.x:5555    device
```
If it shows `unauthorized`, the dialog appeared on your TV — go approve it there.

---

### Step 5 — Run the FireClear Tool

Navigate to the `tools` folder of this repo and run the script:

```bash
# macOS / Linux / Termux:
cd tools
bash firetv-tool.sh

# Windows:
cd tools
firetv-tool.bat
```

You'll see this menu:

```
┌──────────────────────────────────────────────────────┐
│  FireClear Tool                                      │
│                                                      │
│  1  Remove ads only          (safest — start here)   │
│  2  Full debloat             (disables junk apps)    │
│  3  Full debloat, harder     (removes if needed)     │
│  4  Ad-blocking DNS ON                               │
│  5  Ad-blocking DNS OFF                              │
│  6  Undo debloat             (restore everything)    │
│  7  Install APK                                      │
│  8  Set FireClear as Home                            │
│  9  Restore Amazon Home                              │
│  0  Quit                                             │
└──────────────────────────────────────────────────────┘
```

---

### Step 6 — Follow this order (recommended)

> Follow these steps in order for the best result. Each step can be undone.

#### 6a. Install the launcher
Choose **`7` Install APK** and enter the path to the `.apk` file you downloaded.

Then, on your TV, find FireClear in your app list and **open it to test it** — check that apps launch and Settings tiles work before going further.

---

#### 6b. Remove ads and debloat
Choose **`1` Remove ads only** first.

This is the safest option. It disables:
- Ad ID and recommendation engines (the banners and autoplay video)
- Screen-tracking (Automatic Content Recognition / ACR)
- Telemetry and diagnostics reporting
- Shopping from the TV

You'll see `[off]` for each package that was disabled. `[skip]` means that package isn't present on your stick — that's fine.

> **Want to go further?** Choose `2` (Full debloat) to also disable Alexa notifications, Amazon Photos/Music sync, Kids/FreeTime, and other junk. See [docs/DEBLOAT.md](docs/DEBLOAT.md) for the full list of what gets disabled and why.

---

#### 6c. Enable ad-blocking DNS
Choose **`4` Ad-blocking DNS ON**.

This sets your Fire TV to use AdGuard's DNS, which blocks known ad and tracker domains for **every app on the stick** — not just the home screen.

The tool automatically tests your internet connection after enabling it. **If the internet breaks, it reverts itself automatically.**

> 💡 You can enter a custom DNS hostname if you prefer a different provider (e.g. `1dot1dot1dot1.cloudflare-dns.com` for Cloudflare).

---

#### 6d. Reboot the Fire TV
```bash
adb reboot
```
Wait ~60 seconds, then reconnect:
```bash
adb connect YOUR_TV_IP:5555
```
Check everything still works normally before the next step.

---

#### 6e. Set FireClear as the Home screen
Choose **`8` Set FireClear as Home**.

From now on, pressing the **Home** button on your remote will open FireClear instead of Amazon's ad-filled launcher.

> ⚠️ If the tool says it was refused, use a Home-button remapper app and map the Home button to FireClear manually. See [docs/GUIDE.md#troubleshooting](docs/GUIDE.md#troubleshooting).

---

#### 6f. Turn off the last data collection switches (by hand)

These must be toggled manually on the TV — ADB can't do it:

1. **Settings → Preferences → Privacy Settings:**
   - Device Usage Data → **OFF**
   - Collect App Usage Data → **OFF**
   - Interest-based Ads → **OFF**

2. **Settings → Preferences → Featured Content:**
   - Allow Video Autoplay → **OFF**
   - Allow Sound Autoplay → **OFF**

---

### Step 7 — Lock it down again

Once everything is working, turn off the developer settings you enabled earlier:

On your Fire TV: **Settings → My Fire TV → Developer Options**
- ADB Debugging → **OFF**
- Apps from Unknown Sources → **OFF**

Then quit the tool: press **`0`** in the menu.

---

## ↩️ How to Undo Everything

Changed your mind? Every single step is reversible:

| What to undo | How |
|---|---|
| Debloat / ad removal | Run the tool → choose **`6` Undo debloat** |
| Ad-blocking DNS | Run the tool → choose **`5` Ad-blocking DNS OFF** |
| Restore Amazon Home | Run the tool → choose **`9` Restore Amazon Home** |
| Remove FireClear | Standard TV settings → Apps → FireClear → Uninstall |
| Nuclear option | Factory reset the Fire TV |

---

## 🔍 Troubleshooting

<details>
<summary>ADB says "unauthorized" or connection refused</summary>

- Make sure ADB Debugging is ON in Developer Options.
- A dialog may be waiting on your TV screen — look for *"Allow USB debugging?"* and tap **Always Allow**.
- Try: `adb kill-server` then `adb connect YOUR_TV_IP:5555` again.

</details>

<details>
<summary>Black screen after setting FireClear as Home</summary>

From any computer on the same Wi-Fi:
```bash
adb connect YOUR_TV_IP:5555
adb shell pm enable --user 0 com.amazon.tv.launcher
```

</details>

<details>
<summary>Menu 8 (Set as Home) was refused</summary>

Install a Home-button remapper app (e.g. from the Appstore) and map the Home button to FireClear. See [docs/GUIDE.md#troubleshooting](docs/GUIDE.md#troubleshooting).

</details>

<details>
<summary>No internet after enabling DNS</summary>

The tool auto-reverts, but if you need to fix it manually:
```bash
adb shell settings put global private_dns_mode off
```

</details>

<details>
<summary>Casting from my phone stopped working</summary>

Re-enable the casting service:
```bash
adb shell pm enable --user 0 com.amazon.whisperplay.contracts
```

</details>

<details>
<summary>Amazon updated the stick and the ads came back</summary>

Updates can re-enable packages. Just run the tool again and choose **`1`** (and **`8`** if the home screen reverted too).

</details>

<details>
<summary>FireClear custom wallpaper</summary>

```bash
adb push photo.jpg /sdcard/Android/data/app.fireclear.launcher/files/wallpaper.jpg
```
Then in FireClear: **Customize → Custom photo**. JPG or PNG, up to 25 MB, 1920×1080 ideal.

</details>

---

## 🏠 Using the FireClear Launcher

| Action | What it does |
|---|---|
| **OK** on an app | Opens it |
| **Hold OK** on an app | Options: Favourite, Move, Hide, App Info, Uninstall |
| **Menu (☰) button** on an app | Same as Hold OK |
| **Up** from the top row | Reaches the top bar: Wi-Fi, Customize, Settings |
| **Home** while already home | Glides back to the first tile |

**Layout:** 4-App Favorites Dock (Top Shelf) → Apps Grid

**Dock Management:** Hold **OK** or press **Menu (☰)** on any app to pin it to a dock slot (1–4), move it left/right within the dock, or remove it.

**System Settings:** Available at any time via the **Settings** pill in the top status bar (one-click access to full Fire TV settings).

**Customize menu:** 5 built-in wallpapers, Custom photo, Unhide all apps, Animations On/Off.

---

## 🔒 Security at a Glance

- **No `INTERNET` permission** — Android blocks all network traffic from the app. It cannot upload anything, ever.
- **Zero libraries** — no third-party code, no native code, no WebView, no reflection.
- **CI-audited** — the build pipeline fails if any of this changes. See [`scripts/audit.sh`](scripts/audit.sh).
- **Tool safety** — the script only runs `adb` commands against the IP you enter, and validates every package name before it reaches the device.

Full threat model: [docs/SECURITY.md](docs/SECURITY.md). Verify yourself in 30 seconds: `bash scripts/audit.sh`.

---

## 📦 Latest Release: v2.3

| | |
|---|---|
| Version | **2.3** (versionCode 5) — [CHANGELOG](CHANGELOG.md) |
| Package | `app.fireclear.launcher` |
| Target device | Fire TV Stick 3rd gen, Fire OS 7 (Android 9) |
| Android levels | minSdk 25, targetSdk 28 |
| Permissions | `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` only |
| Dependencies | **0** |

**What's new in 2.3:** tvOS-style 4-app Favorites Dock with interactive slot management, white glow focus animations, clutter-free home screen, tool menu with **Remove ads only** and **Ad-blocking DNS on/off** (with automatic rollback), and hardened release workflow.

---

## 📁 Repository Layout

```
app/                  The launcher (Kotlin, zero dependencies)
tools/                firetv-tool.sh / .bat + package lists
scripts/              audit.sh, verify-apk.sh (also run in CI)
docs/                 GUIDE, ADS, BUILD, DEBLOAT, SECURITY
.github/workflows/    build, release, CodeQL
```

---

## 🤝 Contributing & License

PRs are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). Device reports (model, Fire OS version, what worked or didn't) are especially helpful as GitHub Issues.

Licensed under [MIT](LICENSE).

> Not affiliated with Amazon or Apple. "tvOS-style" describes the layout only — no Apple assets are used. Disabling system packages is at your own risk; it is unsupported by Amazon but fully reversible.


