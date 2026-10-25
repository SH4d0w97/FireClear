# Security

**Claim:** FireClear is built so there is very little that *can* go wrong, and you can check that yourself.
**Not a claim:** nobody can prove "zero bugs". What follows is what the code can't do, what is enforced automatically, and what remains.

## What the app cannot do (checked by `scripts/audit.sh` + `scripts/verify-apk.sh`)
- **No internet.** The manifest has no `INTERNET` permission, so Android blocks all network traffic. Nothing can be uploaded.
- **Two normal permissions only:** `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` (to show Wi-Fi signal). No storage, location, camera, mic, contacts, accounts, overlay or accessibility access.
- **Zero third-party libraries**, no native code, no WebView, no reflection or dynamic class loading, no shell/process execution, no broadcast receivers, services or content providers.
- **One exported component** (the launcher screen). It ignores intent extras, so other apps can't feed it data.
- `allowBackup=false`, `usesCleartextTraffic=false`, not debuggable.
- **Data stored:** your favourites, recents, hidden apps and wallpaper choice, in the app's private storage. Nothing else.
- The only files it reads from outside itself: `wallpaper.(jpg|jpeg|png)` in its own app folder (size-capped at 25 MB, dimensions validated, decoded downsampled).
- When you open an app it sends an explicit launch request for an app already installed; it never installs, downloads or modifies other apps.

## What the tools can do
`firetv-tool` runs only `adb` commands against the IP you type. It contains no downloads, no `eval`, no encoded payloads.
Every package name is validated (`^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$`) before it reaches the device shell, which prevents command injection through a tampered `packages.txt`.
`packages.txt` and `packages-ads.txt` may only contain `com.amazon.*` names (enforced by the audit). Debloating is reversible.
The optional **DNS** menu changes only the system Private DNS setting, to a hostname that is validated first (default `dns.adguard-dns.com`). It tests the internet afterwards and switches itself off if that fails.
Privacy note: whichever DNS provider you choose can see the domain names your TV looks up.

## Automated checks on every push
`scripts/audit.sh` (see above) · built-APK permission check · Android lint · **CodeQL** · **Dependabot** for Actions and Gradle plugins.

## Verify yourself
```
bash scripts/audit.sh
aapt2 dump permissions app-debug.apk        # expect only the two ACCESS_* permissions
sha256sum FireClear-*.apk                    # compare with SHA256SUMS.txt on the release
```
Best practice: build the APK yourself from source (GUIDE Step 2B/2C) so you don't have to trust a binary.

## Known limitations / residual risks
- **Debug-signed releases** unless the maintainer adds signing secrets: the signature changes per build (uninstall before updating).
- **ADB over Wi-Fi** is a network-exposed debug interface while enabled. Turn it off after use.
- Another app with storage-write permission could place a file in FireClear's wallpaper folder; size/dimension checks limit the impact to "wallpaper not shown".
- Build tooling (Android Gradle Plugin, Kotlin plugin, GitHub Actions) is third-party; versions are pinned and watched by Dependabot, but consider pinning Actions to commit SHAs in your fork.
- Disabling Amazon system packages is unsupported by Amazon and may affect features (casting, Alexa notifications, Kids).

## Reporting a vulnerability
GitHub **Security tab > Report a vulnerability** (private). Please don't file public issues for security bugs.
