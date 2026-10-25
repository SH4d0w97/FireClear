# Changelog

## 4.0.0 - 2026-10-25
- Automated major release bump from 3.0.2 (versionCode 11) to 4.0.0 (versionCode 12).

## 3.0.0 - 2026-10-25
- Automated major release bump from 2.3.3 (versionCode 8) to 3.0.0 (versionCode 9).

## 2.3.3 - 2026-10-25
- Automated patch release bump from 2.3.2 (versionCode 7) to 2.3.3 (versionCode 8).

## 2.3.2 - 2026-10-25
- Automated patch release bump from 2.3.1 (versionCode 6) to 2.3.2 (versionCode 7).

## 2.3.1 - 2026-10-25
- Automated patch release bump from 2.3 (versionCode 5) to 2.3.1 (versionCode 6).

## 2.3
- **Smart TV Launcher Redesign**:
  - **Hero Showcase Header on Home**: Dynamic cinematic typography on the Favourites view that smoothly cross-fades the focused app's title, category metadata tag, and action hint over the atmospheric wallpaper.
  - **Interactive Wi-Fi System Pill**: Clickable and focusable top-right pill displaying the live Wi-Fi SSID/connection status that directly opens network settings on select/click.
  - **Refined Control Center (Settings View)**: 6 distinct, non-redundant quick setting tiles (*Network & Wi-Fi*, *Remotes & Bluetooth*, *Wallpaper & Themes*, *Applications*, *Display & Sounds*, *Device & About*) featuring circular frosted icon badges and informative live subtitles, alongside the full-width *All Settings* gateway.
  - **Prominent Card Anatomy**: Expanded app icons to 60% card height with multi-stop brand gradient backdrops, top specular glass sheens, and inner glass rims.
  - **Full Immersive Fullscreen**: Edge-to-edge layout hiding distracting system gesture and status bars on tablets and TV displays.
  - **Independently Centered Glass Navigation Pill**: Translucent smoky charcoal capsule (`#20252D` at ~67% opacity with a subtle white border) centered horizontally across the display. Switches seamlessly between **Favourites**, **All Apps**, and **Settings** with white pill active highlights.
  - **Adaptive Ambient Glow**: Broad, soft, low-opacity radial bloom near the bottom dock that smoothly cross-fades (300ms) to reflect the focused app's dominant signature brand color.
  - **Restrained Focus Physics**: Pinned tiles scale slightly (1.06×), project a clean soft-white outline, and cast subtle depth shadows with a smooth 240ms transition.
  - **Clean Header Layout**: Top-left digital clock and cool gray date (`#C3CAD3`) without background containers, balanced with the interactive Wi-Fi system pill on the top-right.
  - **Design Token Palette**: Standardized around Midnight Black (`#0B0F14`), Smoky Charcoal (`#20252D`), Soft White (`#FFFFFF`), Cool Gray (`#C3CAD3`), and Muted Blue (`#597A91`).
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
