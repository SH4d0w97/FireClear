<div align="center">

  <img src="Glossy Layered Flame Icon.png" alt="FireClear Logo" width="76" height="76" />

  # FireClear

  **A focused, privacy-first home launcher and reversible debloat toolkit for Amazon Fire TV.**

  [![Build Status](https://github.com/SH4d0w97/FireClear/actions/workflows/build.yml/badge.svg)](https://github.com/SH4d0w97/FireClear/actions/workflows/build.yml)
  [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
  ![Platform](https://img.shields.io/badge/Platform-Fire%20OS%207%2B%20(Android%209%2B)-orange)
  ![Network Permission](https://img.shields.io/badge/Network%20Access-None-brightgreen)
  ![Dependencies](https://img.shields.io/badge/Dependencies-0-brightgreen)

  [**Download Latest Release**](../../releases/latest) &nbsp;•&nbsp; [**Setup Guide**](#-setup-guide) &nbsp;•&nbsp; [**Documentation**](docs/GUIDE.md) &nbsp;•&nbsp; [**Security Model**](docs/SECURITY.md)

</div>

---

## Overview

Modern streaming devices are increasingly overwhelmed by sponsored autoplay videos, full-screen banner advertisements, and background telemetry services. **FireClear** restores your Fire TV to an intentional, responsive streaming interface in two complementary parts:

1. **The Launcher (`app.fireclear.launcher`)**  
   A refined, tvOS-inspired home screen engineered with native Android framework components. Features a floating 4-app favorites dock, smooth 60 FPS hardware animations, multi-row app grids, and a strict sub-15 MB RAM footprint. It contains **no network permission**—guaranteeing that it cannot transmit data or serve advertisements.

2. **The Debloat Toolkit (`firetv-tool`)**  
   An interactive, menu-driven script for Windows, macOS, Linux, and Android (Termux). It safely disables Amazon telemetry, ad engines, and unused system background packages using standard ADB user profiles. No root required, zero binary payloads, and **every change is 100% reversible**.

---

## Interface & Navigation

```
 20:45                           ┌───────────────────────────────┐                             📶
 Monday, 26 October              │  [Favourites]  All Apps  Settings │
                                 └───────────────────────────────┘

                                (Atmospheric wallpaper space)

 ┌──────────────────────────────────────────────────────────────────────────────────────────────┐
 │       [ YouTube ]           [[ Prime Video ]]            [ Spotify ]           [ Netflix ]   │  ← 4-App Dock
 └──────────────────────────────────────────────────────────────────────────────────────────────┘
                                  Prime Video (ambient bloom & 1.06x focus)
```

- **Independently Centered Glass Pill:** Smoky charcoal translucent capsule (`#20252D` at ~67% opacity with a subtle white border) perfectly centered horizontally across the display. Houses three tabs: **Favourites**, **All Apps**, and **Settings**.
- **Cinematic Negative Space:** The Home screen (*Favourites*) contains exclusively your 4 pinned apps in the dock. No recommendations, banners, or section headings—the wallpaper breathes naturally.
- **Adaptive Ambient Glow:** A soft, low-opacity radial gradient near the bottom dock smoothly blooms with the focused app's signature brand color (e.g., ruby for YouTube, emerald for Spotify, sapphire for Prime Video).
- **All Applications View:** A dedicated 5-column grid for all installed applications with custom brand-tinted cards, top specular sheens, and inner glass rims.
- **Quick Settings Hub:** Streamlined 2×3 control center tiles (*Wi-Fi*, *Bluetooth*, *Wallpaper*, *Sound*, *Display*, *Network*) plus a full-width *All Settings* gateway with high-contrast active states.
- **Restrained Focus Physics:** Focused tiles scale slightly (1.06×), project a clean soft-white outline, and cast subtle depth shadows with a smooth 240ms transition.

---

## Core Principles

| Principle | Implementation |
|---|---|
| **Zero Network Exposure** | `android.permission.INTERNET` is intentionally omitted from the manifest. The operating system prevents the app from opening network sockets. |
| **Zero Dependencies** | Written purely against standard Android SDK APIs. No third-party libraries, analytics SDKs, WebViews, or dynamic code execution. |
| **Minimal Footprint** | Dynamic tile art is rasterized on a single background thread and cached in a strict 12 MB LRU memory cache, maintaining sub-15 MB active RAM. |
| **Non-Destructive Debloat** | The toolkit disables packages via `pm disable-user --user 0`. System partitions remain untouched; every package can be restored with a single click. |
| **Automatic Safety Guards** | Private DNS ad-blocking performs automated round-trip connectivity tests and rolls back immediately if your connection drops. |

---

## 📋 Setup Guide

> **Prerequisite:** Your Fire TV Stick and your computer (or phone/tablet) must be connected to the **same local Wi-Fi network**.

---

### Step 1 — Prepare ADB on Your Host Device

Choose your platform to verify ADB is ready:

<details>
<summary>🪟 Windows</summary>

1. Download the official [Google SDK Platform-Tools for Windows](https://developer.android.com/studio/releases/platform-tools).
2. Extract the archive (for example, to `C:\platform-tools`).
3. Open PowerShell or Command Prompt in that folder and verify:
   ```cmd
   adb version
   ```

</details>

<details>
<summary>🍎 macOS</summary>

Install via Homebrew:
```bash
brew install android-platform-tools
adb version
```

</details>

<details>
<summary>🐧 Linux</summary>

Install via your package manager:
```bash
sudo apt install adb   # Debian / Ubuntu / Mint
sudo dnf install adb   # Fedora
adb version
```

</details>

<details>
<summary>📱 Android Phone or Tablet (No PC Required)</summary>

1. Install **F-Droid** from [f-droid.org](https://f-droid.org).
2. Install **Termux** from F-Droid (do not use the obsolete Play Store version).
3. In Termux, run:
   ```bash
   pkg update -y && pkg install -y git android-tools
   adb version
   ```

</details>

---

### Step 2 — Enable Developer Options on Fire TV

1. On your Fire TV, go to **Settings → My Fire TV → About**.
2. Highlight your device name (e.g. *Fire TV Stick 4K*) and press the remote's **Select** button **7 times**.
   * A toast notification will appear: *"No need, you are already a developer."*
3. Press **Back**, open **Developer Options**, and set:
   - **ADB Debugging** → **ON**
   - **Apps from Unknown Sources** → **ON**
4. Check your Fire TV's IP address under **Settings → My Fire TV → About → Network** (e.g. `192.168.1.50`).

---

### Step 3 — Connect via ADB

In your terminal or command prompt:

```bash
adb connect YOUR_FIRE_TV_IP:5555
# Example: adb connect 192.168.1.50:5555
```

> **First connection:** A prompt will appear on your TV asking *"Allow USB debugging?"* Check **"Always allow from this computer"** and select **OK**.

Verify the connection status:
```bash
adb devices
# Expected output: 192.168.x.x:5555    device
```

---

### Step 4 — Run the FireClear Toolkit

Clone or download this repository, navigate to the `tools/` directory, and launch the script:

```bash
# macOS / Linux / Termux:
cd tools
bash firetv-tool.sh

# Windows:
cd tools
firetv-tool.bat
```

You will be greeted by the interactive management console:

```
┌──────────────────────────────────────────────────────────────┐
│  FireClear Toolkit                                           │
│                                                              │
│  1  Remove ads only          (safest tier — start here)      │
│  2  Full debloat             (disables telemetry & bloat)    │
│  3  Full debloat, aggressive (deep package cleanup)          │
│  4  Ad-blocking DNS ON       (system-wide ad blocking)       │
│  5  Ad-blocking DNS OFF      (restore default DNS)           │
│  6  Undo debloat             (restore all disabled packages) │
│  7  Install APK              (sideload FireClear launcher)   │
│  8  Set FireClear as Home    (enable custom launcher)        │
│  9  Restore Amazon Home      (re-enable stock launcher)      │
│  0  Quit                                                     │
└──────────────────────────────────────────────────────────────┘
```

---

### Step 5 — Recommended Deployment Workflow

For the cleanest and most stable experience, proceed in this order:

1. **Install the Launcher (`Option 7`)**  
   Download the latest release APK from the [Releases](../../releases/latest) page. In the menu, select `7` and provide the file path. Test launching FireClear from your TV's app drawer to confirm normal operation.

2. **Remove Advertising Services (`Option 1`)**  
   Select `1` to disable the recommendation and sponsored banner services, Automatic Content Recognition (ACR screen analysis), and usage telemetry.

3. **Enable System-Wide Ad-Blocking DNS (`Option 4`)**  
   Configures encrypted private DNS (AdGuard DNS by default or your preferred provider like Cloudflare/NextDNS). The toolkit performs an automated connectivity handshake; if the DNS host is unreachable, changes are immediately rolled back.

4. **Reboot the Device**  
   Run `adb reboot` from your terminal to allow system caches to settle. Once rebooted, reconnect via `adb connect YOUR_FIRE_TV_IP:5555`.

5. **Set FireClear as Home (`Option 8`)**  
   Configures your remote's Home button to navigate directly to FireClear.

6. **Disable Manual Privacy Toggles (On TV Settings)**  
   Navigate to your Fire TV settings to toggle the final firmware-level analytics switches:
   - **Settings → Preferences → Privacy Settings:**
     - *Device Usage Data* → **OFF**
     - *Collect App Usage Data* → **OFF**
     - *Interest-based Ads* → **OFF**
   - **Settings → Preferences → Featured Content:**
     - *Allow Video Autoplay* → **OFF**
     - *Allow Audio Autoplay* → **OFF**

7. **Re-secure Developer Options**  
   When your configuration is complete, return to **Settings → My Fire TV → Developer Options** and toggle **ADB Debugging** back to **OFF**.

---

## Remote Control Navigation

| Key | Context | Action |
|---|---|---|
| **Select / OK** | App tile | Launches the selected application |
| **Select / OK** | Navigation tab | Switches active view (*Favourites*, *All Apps*, *Settings*) |
| **Menu (☰) or Long-press OK** | Pinned dock tile | Reorder left/right, remove from dock, or open app info |
| **Menu (☰) or Long-press OK** | All Apps tile | Pin to Favourites (slot 1–4), hide from launcher, or open app info |
| **Up** | Favourites dock / Grid / Settings | Moves focus up to the centered Navigation Pill |
| **Down** | Navigation Pill | Enters the active view's content |
| **Left / Right** | Navigation Pill | Switches between *Favourites*, *All Apps*, and *Settings* |
| **Home** | Anywhere | Immediately returns to the Favourites Home screen |

---

## ↩️ Rollback & Restoration

Every modification made by FireClear is reversible without reinstalling firmware or losing personal app data:

| Desired Action | Resolution |
|---|---|
| **Restore All Disabled Packages** | Run toolkit → Choose **`6` Undo debloat** |
| **Disable Ad-Blocking DNS** | Run toolkit → Choose **`5` Ad-blocking DNS OFF** |
| **Restore Stock Amazon Home** | Run toolkit → Choose **`9` Restore Amazon Home** |
| **Uninstall FireClear** | Standard TV Settings → Applications → FireClear → Uninstall |
| **Full Emergency Factory State** | Fire TV Settings → My Fire TV → Reset to Factory Defaults |

---

## Security & Verification

FireClear enforces strict static build guardrails verified via automated CI on every commit:

- **Manifest Audit:** Strictly bounded to `ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE`.
- **Zero Third-Party Code:** No external Maven/Gradle libraries or pre-compiled binaries are committed to the repository.
- **Source Inspection:** Automated regex validation prohibits reflection, dynamic code class-loaders, WebViews, or background service declarations.
- **Deterministic Verification:** Run the static audit script locally at any time:
  ```bash
  bash scripts/audit.sh
  ```

For a comprehensive review of package safety classifications, refer to [docs/SECURITY.md](docs/SECURITY.md) and [docs/DEBLOAT.md](docs/DEBLOAT.md).

---

## Project Structure

```
├── app/                  # Native Android launcher (Kotlin, zero dependencies)
│   └── src/main/java/    # Clean architecture UI and rendering pipeline
├── tools/                # firetv-tool.sh, firetv-tool.bat, and package lists
├── scripts/              # Static security audit and APK verification scripts
├── docs/                 # Guides (GUIDE.md, DEBLOAT.md, ADS.md, BUILD.md, SECURITY.md)
└── .github/workflows/    # CI builds, CodeQL security scanning, and automated releases
```

---

## License & Notice

This project is licensed under the [MIT License](LICENSE).

*Disclaimer: FireClear is an independent open-source utility and is not affiliated with, endorsed by, or associated with Amazon.com, Inc. or Apple Inc. Mention of device names or visual styles is for descriptive compatibility purposes only. Modifying installed system packages is performed at the user's discretion.*