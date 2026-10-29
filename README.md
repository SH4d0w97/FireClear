# Lumen TV — Production Android TV & Tablet Home Launcher

A lightweight, minimal, 10-foot dark-only Android TV home launcher inspired by Apple tvOS design principles. Built strictly with Kotlin 2.x and Jetpack Compose for TV / Leanback standards.

---

## 1. Architecture & Deliverables Overview

- **Package**: `com.lumen.tv`
- **Minimum SDK**: 29 (Android 10)
- **Target SDK**: 35 (Android 15)
- **Canvas Base**: 1080p @ density 2 = 960×540dp. Adapts smoothly to 4K and 720p (compact tier).

### Codebase Organization

```
app/src/main/java/com/lumen/tv/
├── MainActivity.kt                      // Entry point, window flags, BackHandler, page crossfading
├── focus/
│   ├── FocusEngine.kt                   // Page focus restoration & key tracking
│   └── FocusSpec.kt                     // Geometric spatial nav algorithm & shelf dimming
├── data/
│   ├── WallpaperRepository.kt           // 4 ambient gradient presets + Dim Wallpaper toggle (DataStore)
│   ├── FavouritesStore.kt               // Package queries (LAUNCHER & LEANBACK), max 6 pinned apps, LRU cache
│   ├── SettingsStateHolder.kt           // Optimistic connection state + system panel intents
│   └── DemoContent.kt                   // Demo continue watching repository
├── ui/
│   ├── theme/
│   │   ├── Tokens.kt                    // Exact tvOS colors, typography, radii, switches & motion tokens
│   │   └── BlurCompat.kt                // RenderEffect blur on API 31+, graceful scrim fallback on API 29-30
│   ├── chrome/
│   │   ├── Scrim.kt                     // 84dp top header scrim with 14dp blur
│   │   ├── WallpaperLayer.kt            // Dual-radial ambient gradient drawing with 500ms crossfade
│   │   └── TopChrome.kt                 // 64dp header: 15s live clock, tabs (Home/Apps/Settings), Wi-Fi chip
│   ├── home/
│   │   ├── HomeScreen.kt                // Viewport-fixed dashboard (NO vertical scrolling), dynamic clamps
│   │   ├── ContinueCard.kt              // 1.05 scale, lift, 80% sibling dimming, 3dp progress bar
│   │   └── FavouritesRow.kt             // Exactly max 6 squircle tiles with real app icons/monograms
│   ├── drawer/
│   │   └── AppsScreen.kt                // 118dp auto-fill app grid, long-press favorite pinning
│   └── settings/
│       ├── GroupCard.kt                 // 18dp radius glass card, hairline border, 20dp blur
│       ├── SettingsRow.kt               // Inset 54dp separators, 28dp chip with 0.5dp inner ring
│       ├── SwitchRow.kt                 // Custom 46×27dp tvOS switch track & 22dp knob
│       ├── DisclosureRow.kt             // Expandable 4-swatch wallpaper picker with rotating chevron
│       └── SettingsScreen.kt            // Real-time search filter, single/dual column responsive layout
```

---

## 2. Build & Run Instructions

### Prerequisites
- JDK 17
- Android SDK 35 with build-tools 35.0.0+

### Compiling and Installing via CLI
```bash
# Debug APK Build
./gradlew assembleDebug

# Install on connected Android TV / Tablet
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch Lumen TV
adb shell am start -n com.lumen.tv/.MainActivity
```

---

## 3. Setting as Default TV Home

Lumen TV declares `android.intent.category.HOME` and `android.intent.category.LEANBACK_LAUNCHER`.

### Via In-App Prompt (Android 10+)
On first launch, Lumen TV requests the default home role via `RoleManager.ROLE_HOME`. Select **Lumen TV** and choose **Always**.

### Manual Fallback
If dismissed or unsupported:
1. Open TV **Settings** → **Apps** → **Special app access** (or **Default apps**).
2. Select **Home app**.
3. Choose **Lumen TV**.

### Via ADB
```bash
adb shell cmd package set-home-activity com.lumen.tv/.MainActivity
```

---

## 4. Remote Control Testing (ADB Keyevents)

Lumen TV is **100% operable with only a D-pad remote**. No touch is required.

| Action | ADB Command | Key Code |
| :--- | :--- | :--- |
| **Up** | `adb shell input keyevent 19` | `KEYCODE_DPAD_UP` |
| **Down** | `adb shell input keyevent 20` | `KEYCODE_DPAD_DOWN` |
| **Left** | `adb shell input keyevent 21` | `KEYCODE_DPAD_LEFT` |
| **Right** | `adb shell input keyevent 22` | `KEYCODE_DPAD_RIGHT` |
| **Select / Enter** | `adb shell input keyevent 23` | `KEYCODE_DPAD_CENTER` |
| **Alternative Enter**| `adb shell input keyevent 66` | `KEYCODE_ENTER` |
| **Back** | `adb shell input keyevent 4` | `KEYCODE_BACK` |

### Testing Focus & Navigation Flows
1. **Home Screen Focus**: On launch, focus defaults to the `[Play]` button.
   - D-pad Down moves to the **Continue Watching** shelf. Sibling cards dim to 80% brightness while the focused card scales 1.05× and lifts.
   - D-pad Down again moves to **Favourites** (max 6 pinned apps).
   - D-pad Up returns to Hero actions or Top Chrome.
2. **Page Navigation**:
   - Focus any tab in Top Chrome (`Home`, `Apps`, `Settings`) and press `DPAD_CENTER` to switch pages.
   - Focus the **Wi-Fi Chip** on the right of Top Chrome and press `DPAD_CENTER` to instantly navigate to Settings with the Wi-Fi row focused.
3. **Back Key Hierarchy**:
   - Pressing `KEYCODE_BACK` from `Settings` or `Apps` returns to `Home`.
   - Pressing `KEYCODE_BACK` on `Home` is ignored (root launcher cannot exit).
4. **App Drawer & Long-Press**:
   - Navigate to `Apps`.
   - Press `DPAD_CENTER` to launch the app.
   - Long-press `DPAD_CENTER` (or send `adb shell input keyevent --longpress 23`) on any tile to toggle it in **Favourites** (max 6).

---

## 5. Platform Truths & Permission Behaviors

- **Wi-Fi & Bluetooth**: Android 10+ restricts third-party launchers from programmatically disabling Wi-Fi/Bluetooth. Lumen TV reflects real system states via `WifiManager` and `ConnectivityManager`, performs optimistic UI toggles, and routes users to `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` or `Settings.ACTION_BLUETOOTH_SETTINGS`.
- **Airplane Mode**: Handled via `Settings.Global.AIRPLANE_MODE_ON` broadcasts with direct intent fallback to `Settings.ACTION_AIRPLANE_MODE_SETTINGS`.
- **Blur Compatibility**: Devices on Android 12+ (API 31+) use hardware-accelerated `RenderEffect.createBlurEffect`. Devices on API 29–30 degrade gracefully via `BlurCompat` to high-performance tinted translucent scrims with no crashes.
- **Package Visibility**: Queries both `CATEGORY_LAUNCHER` and `CATEGORY_LEANBACK_LAUNCHER` under `QUERY_ALL_PACKAGES` to index all sideloaded, phone, and native Leanback TV apps while filtering out the launcher itself.

---

## 6. Self-Check Acceptance Criteria Verification

| Requirement | Spec Criterion | Status | Implementation Details |
| :--- | :--- | :--- | :--- |
| **D-Pad Operability** | 100% operable with D-pad remote only | **PASS** | Spatial geometric navigation, explicit focus requesters, and zero touch dependencies. |
| **Single Active Focus** | Exactly one focused element visible at all times | **PASS** | High-contrast borders, tvOS scale (1.05/1.06), and lift across all components. |
| **Fixed Home Viewport** | Zero vertical scroll on Home at 1080p, 4K & 720p | **PASS** | Dynamic height percentages with dp clamps in `HomeScreen.kt` (`BoxWithConstraints`). |
| **Shelf Focus & Sibling Dimming**| Sibling cards dim to 80% brightness | **PASS** | Implemented via `LocalShelfFocusActive` in `ContinueCard.kt`. |
| **Drawer App Query** | Real installed apps, excludes self, restores focus | **PASS** | Dual-category query (`LAUNCHER` + `LEANBACK_LAUNCHER`) in `FavouritesStore.kt`. |
| **tvOS Settings Grouping** | ACCOUNT, CONNECTIONS, LAUNCHER, DEVICE groups | **PASS** | Complete grouped layout with search filtering, custom switches, and disclosure. |
| **Inline Wallpaper Disclosure** | Rotating chevron + 4 gradient swatches | **PASS** | Graphite, Dune, Deep Sea, Ember with focus glow ring in `DisclosureRow.kt`. |
| **DataStore Persistence** | Wallpaper, Dim, Favourites persist | **PASS** | Stored in `lumen_launcher_prefs.preferences_pb`. |
| **Top Chrome & Scrim** | 64dp chrome, 15s ticker clock, 84dp scrim | **PASS** | Built in `TopChrome.kt` and `Scrim.kt` with 14dp blur. |
| **Adaptive Icons** | Crisp layered flame artwork | **PASS** | Integrated into `mipmap-*` and `drawable/ic_launcher_*`. |
