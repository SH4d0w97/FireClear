# Removing ads from a Fire TV Stick

There is no single switch. FireClear stacks four layers; each helps on its own.

| Layer | What it removes | How |
|---|---|---|
| 1. Replace the home screen | Banner, autoplay and sponsored rows on Amazon's home | Install FireClear, tool menu **8** |
| 2. Switch off ad packages | Ad ID, recommendations, ACR, telemetry, shopping | Tool menu **1** (ads only) or **2** (full) |
| 3. Ad-blocking DNS | Known ad and tracker domains, for every app | Tool menu **4** |
| 4. Privacy toggles | Personalised ads, usage reporting, autoplay | By hand, below |

## Layer 2: what menu 1 switches off
`tools/packages-ads.txt`: `com.amazon.tv.acr` (reports what's on screen), `hybridadidservice` and `media.recommendations` (ad ID and recommendations),
`device.telemetry.emitter`, `dp.logger`, `perfcollection`, `wirelessmetrics.service` (telemetry and diagnostics), `shoptv.client` (shopping).
Descriptions come from a community list tested on Fire OS. Fully reversible with menu 6.

## Layer 3: ad-blocking DNS
Menu **4** runs these (the tool does it for you, tests the internet, and reverts if it fails):
```bash
adb shell settings put global private_dns_mode hostname
adb shell settings put global private_dns_specifier dns.adguard-dns.com
adb shell settings put global private_dns_mode off      # undo
```
- Fire OS has no screen for Private DNS, so ADB is the way in.
- **Privacy trade-off:** the DNS provider you choose sees which domains your TV looks up. AdGuard DNS is the default; type another hostname to use your own.
- **Router alternative:** set your router's DNS to AdGuard's addresses (`94.140.14.14` and `94.140.15.15`) to cover every device. Don't combine both unless you know why.
- A user reported a Fire TV 4K Max losing connectivity with this method. That is why it is opt-in and self-reverting.
- Check: `adb shell settings get global private_dns_mode` should print `hostname`.

## Layer 4: privacy toggles
**Settings > Preferences > Privacy Settings:** Device Usage Data **OFF**, Collect App Usage Data **OFF**, Interest-based Ads **OFF**.
**Settings > Preferences > Featured Content:** Allow Video Autoplay **OFF**, Allow Sound Autoplay **OFF**.
(With FireClear as Home you rarely see Featured Content, but keep it off.)

## What this cannot do
- Ads **inside** apps (YouTube, Prime Video, free streaming apps) are served by the app. DNS blocking catches some third-party ad networks, not ads delivered from the same servers as the video.
- Sideloaded ad-block apps (VPN-based blockers) exist; FireClear doesn't ship or require one.
- Amazon may re-enable packages after a system update. Re-run menu 1 or 2.
