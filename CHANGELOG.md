# Changelog

## 6.0.1 - 2026-10-27
- Automated patch release bump from 6.0.0 (versionCode 16) to 6.0.1 (versionCode 17).

## 6.0.0 - 2026-10-27
- Automated major release bump from 5.0.1 (versionCode 15) to 6.0.0 (versionCode 16).

## 5.2.0 - 2026-10-27
- **Apple tvOS-Inspired Pure Cinematic Home & Functional Quick Settings**:
  - **Clean Home Screen (Zero Dummy Content)**: Removed placeholder featured hero card and dummy continue-watching rows. The Home screen now lets the atmospheric wallpaper breathe with prominent hero typography and quick action hints.
  - **Bottom-Aligned Pinned Dock**: Positioned the 4-app Favorites dock cleanly at the bottom of the screen (48dp bottom margin), eliminating awkward dead space and matching tvOS ergonomics.
  - **Adaptive Ambient Glow**: Soft, broad radial glow positioned behind the dock that dynamically blooms in the focused app's dominant signature brand color.
  - **Functional Quick Settings Hub**: Replaced dummy toggle switches with 6 real, functional system preference cards (Network & Wi-Fi, Remotes & Bluetooth, Wallpaper & Style, Applications, Display & Sounds, Device & About) plus a full-width All Settings button that launch actual Android/Fire TV system settings.
  - **Overflow & Spacing Hardening**: Set clipChildren=false and clipToPadding=false across all containers, ensuring 1.05x tile elevation and focus rings never clip or glitch.
  - **Top Bar Alignment**: Balanced top-left clock/date, strictly centered floating glass pill (Favourites, All Apps, Settings), and interactive top-right Wi-Fi pill.

## 5.0.1 - 2026-10-25
- Automated patch release bump from 5.0.0 (versionCode 14) to 5.0.1 (versionCode 15).

## 5.1.0 - 2026-10-26
- **tvOS-Inspired Visual Polish & Adaptive Launcher Icon**:
  - **Official Adaptive & Legacy App Icons**: Integrated 432x432 foreground flame layer (ic_launcher_foreground.png), dark solid background layer (ic_launcher_background.png), API 26+ <adaptive-icon> XML definition, and pre-Oreo multi-density mipmaps (mdpi through xxxhdpi).
  - **Deep Midnight-Navy Atmosphere**: Refined background palettes and contrast gradient overlays (#080C14 / #070A10 to #191D3A) for a quiet, cinematic foundation that lets app artwork shine.
  - **Fluid 180ms Focus Transitions**: Calibrated focus animations across tiles, navigation tabs, Wi-Fi pill, and settings cards to ~180ms with DecelerateInterpolator(1.8f) for responsive 60 FPS motion without jitter.
  - **Restrained Elevation & Focus Geometry**: Tuned tile focus scale to 1.05x (dock) and 1.04x (grid) with crisp 1.8dp soft-white focus rings, 14dp/11dp corner radii, and 12dp elevation shadows.
  - **Refined Card Material**: Multi-stop brand-tinted gradient backing, top specular glass sheen, and 1.2dp translucent inner rim stroke with 58% icon scale.
  - **High-Contrast Settings Cards**: Added subtle borders, frosted circular glyph insets, and high-contrast soft-white active focus states across all 6 quick setting tiles and the All Settings gateway.
  - **Strict Security & Zero Dependencies**: 100% passed static security guardrails (scripts/audit.sh), zero external dependencies, and minimal network permission footprint.

## 5.0.0 - 2026-10-25
- Automated major release bump from 4.0.1 (versionCode 13) to 5.0.0 (versionCode 14).

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
