# Changelog

## 2.3.2 - 2026-10-25
- Automated patch release bump from 2.3.1 (versionCode 6) to 2.3.2 (versionCode 7).

## 2.3.1 - 2026-10-25
- Automated patch release bump from 2.3 (versionCode 5) to 2.3.1 (versionCode 6).

## 2.3
- **tvOS Launcher Redesign**:
  - **4-App Favorites Dock (Top Shelf)**: Centered 4-app favorites dock on a translucent glass shelf pedestal with auto-fill symmetry for empty slots.
  - **Interactive Dock Management**: Reorder dock items with Move Left / Move Right, or pin any app from the grid below into a chosen dock slot (1–4).
  - **Clutter-Free Home Screen**: Removed the settings tile row from the home screen canvas; full system settings remains accessible with one click via the top-bar status pill.
  - **tvOS Focus & Depth Animations**: Smooth `1.15x` scale, `-8dp` vertical lift, `28dp` drop shadow elevation, high-contrast white glowing focus ring, and floating title fade-in with a custom cubic deceleration curve.
  - **Ultra-Low RAM Footprint**: Native Android framework implementation with zero third-party dependencies and a 12 MB LRU bitmap cache for steady 60 FPS performance on Fire TV Sticks.
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
