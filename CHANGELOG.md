# Changelog

## 3.0.0 - 2026-10-25
- Automated major release bump from 2.3.3 (versionCode 8) to 3.0.0 (versionCode 9).

## 2.3.3 - 2026-10-25
- Automated patch release bump from 2.3.2 (versionCode 7) to 2.3.3 (versionCode 8).

## 2.3.2 - 2026-10-25
- Automated patch release bump from 2.3.1 (versionCode 6) to 2.3.2 (versionCode 7).

## 2.3.1 - 2026-10-25
- Automated patch release bump from 2.3 (versionCode 5) to 2.3.1 (versionCode 6).

## 2.3
- **tvOS Launcher Redesign**:
  - **Top-Right Status Cluster**: Clean, uncluttered status bar with date tracking on the left and a unified frosted glass capsule cluster on the top-right containing Time, Wi-Fi indicator, Wallpaper customizer, and system Settings.
  - **4-App Favorites Dock**: Floating frosted dark-glass capsule shelf (`0x40101626` with specular glass rim highlight) holding 4 favorite apps in the lower screen with cinematic negative space above.
  - **Below-the-Fold Peek**: Responsive viewport geometry dynamically positioned so the top ~34dp of the installed apps row peeks through from the bottom edge of the display, signaling additional rows below without text clutter.
  - **Multi-Row Apps Grid**: 5-column centered grid for all installed apps revealed upon scrolling down with fluid cubic-bezier decelerated scrolling.
  - **Interactive Dock Management**: Reorder dock slots with Move Left / Move Right, or pin any app from the grid below into any dock slot (1–4) via Menu key or long-press.
  - **Clutter-Free Canvas**: Completely removed redundant settings rows from the home canvas.
  - **tvOS 3D Focus Animations**: `-14dp` dock lift (`-6dp` grid), `1.15x` scale, `32dp` hardware drop shadow elevation, `3.5dp` solid white glowing focus halo, and smooth label fade-in.
  - **Curated Wallpaper Presets**: 5 cinematic gradient presets inspired by tvOS aerials (*Cosmos*, *Sunset*, *Pacific*, *Aurora*, *Slate*) plus custom photo support with subtle vignette shading.
  - **Ultra-Low RAM Footprint**: 100% native Android framework implementation with zero third-party dependencies, 60 FPS hardware layers, and a 12 MB LRU cache keeping active memory under 15 MB.
- **Debloat Tool Updates**:
  - Added **Remove ads only** (backed by new `packages-ads.txt`) — the safest debloat tier.
  - Added **Ad-blocking DNS on/off** (default: AdGuard DNS) with automated round-trip connectivity test and immediate rollback if offline.
  - Enhanced device shell input sanitization and regex validation for IP addresses and hostnames.
  - Numbered menu layout with clear categories for ad removal, debloat, DNS, APK install, and home screen toggles.
- **CI / Build Modernization**:
  - Updated toolchain to Gradle 8.11.1, Android Gradle Plugin (AGP) 8.7.3, and Kotlin 2.0.21.
  - Streamlined GitHub Actions workflows (`build.yml`, `codeql.yml`, `release.yml`) using standard v4 actions with automated tool provisioning.
  - Upgraded compiled APK permission verification in `scripts/verify-apk.sh` with dual `aapt`/`aapt2` fallback and regex extraction.
  - Hardened automated release workflow with dedicated `scripts/bump-version.py`.
- **Audit & Security**:
  - Expanded `scripts/audit.sh` static analysis across all package lists (`packages-ads.txt` and `packages.txt`).
- **Documentation**:
  - Completely rewritten `README.md` with structured step-by-step installation walkthroughs for Windows, macOS, Linux, and Android Termux.
  - Updated `docs/BUILD.md`, `docs/GUIDE.md`, `docs/ADS.md`, and `docs/DEBLOAT.md`.

## 2.2
- tvOS-style home: gliding focus, lift/scale/shadow, centred rows, fade-in.
- Settings row with shortcuts to every system settings page the device can open.
- Art rendered once at tile size on a background thread, 12 MB cache, rounded corners baked in.
- Animations On/Off switch.

## 2.1
- Renamed to FireClear (`app.fireclear.launcher`); no internet permission; backups off.
- Wallpaper size and dimension validation.
- CI: audit, lint, build, APK permission check, CodeQL, Dependabot; release workflow with optional signing.

## 2.0
- Favorites, Recent, wallpapers, Wi-Fi pill, debloat tool.
