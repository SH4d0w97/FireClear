# Changelog

## 2.3
- Tool: **Remove ads only** (new `packages-ads.txt`), **Ad-blocking DNS on/off** with connectivity test and automatic rollback; menu renumbered
- Tool: hostnames validated before reaching the device shell; Windows script input handling hardened
- Audit now covers every `tools/packages*.txt`
- Docs rewritten: README, GUIDE, ADS, BUILD
- App code unchanged since 2.2

## 2.2
- tvOS-style home: gliding focus, lift/scale/shadow, centred rows, fade-in
- Settings row with shortcuts to every system settings page the device can open
- Art rendered once at tile size on a background thread, 12 MB cache, rounded corners baked in
- Animations On/Off switch

## 2.1
- Renamed to FireClear (`app.fireclear.launcher`); no internet permission; backups off
- Wallpaper size and dimension validation
- CI: audit, lint, build, APK permission check, CodeQL, Dependabot; release workflow with optional signing

## 2.0
- Favorites, Recent, wallpapers, Wi-Fi pill, debloat tool
