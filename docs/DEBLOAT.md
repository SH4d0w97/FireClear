# What gets switched off

Two lists: [`tools/packages-ads.txt`](../tools/packages-ads.txt) (ads, recommendations, tracking only: tool menu 1) is a subset of [`tools/packages.txt`](../tools/packages.txt) (full debloat: menu 2 and 3).

Method: `pm disable-user --user 0 <package>` (option 1). Nothing is deleted from the system partition and every change is reversible
(`pm enable --user 0 <package>`, or tool option 3). Option 2 additionally uses `pm uninstall -k --user 0` when disabling is refused;
on Android 9 / Fire OS 7 `cmd package install-existing <package>` brings such a package back.

Source of the base list: a 26-package list tested on a Fire OS device (Tecnobits, Oct 2026). Not every package exists on every device; missing ones are skipped.
Descriptions marked *(source)* come from that article; the rest are listed there without an explanation, so treat them as "community-reported junk".

| Package | What it is |
|---|---|
| com.amazon.spiderpork | Scans/controls sideloaded apps remotely *(source)*; switching it off protects sideloaded apps like FireClear |
| com.amazon.tv.acr | Automatic Content Recognition: reports what's on screen *(source)* |
| com.amazon.hybridadidservice, com.amazon.media.recommendations | Ad ID and recommendation engines behind home-screen banners *(source)* |
| com.amazon.device.telemetry.emitter, dp.logger, perfcollection, wirelessmetrics.service | Telemetry and diagnostics *(source)* |
| com.amazon.shoptv.client | Shopping from the TV *(source)* |
| com.amazon.tahoe | Amazon Kids / FreeTime *(source)*; skip if children use the TV |
| com.amazon.tv.matter, smarthomemapviewapp, tv.alexanotifications | Smart-home and Alexa notification services *(source)* |
| com.amazon.bueller.photos, bueller.music | Amazon Photos / Music background sync *(source)* |
| com.amazon.ods.kindleconnect | Kindle integration *(source)* |
| com.amazon.whisperplay.contracts | Amazon's casting framework, to my knowledge. If casting from a phone stops working, re-enable it |
| com.amazon.tv.easyupgrade, democontent.provider, sneakpeek, storm.lightning.tutorial, audiohome, minitv.android.app, kso.blackbird, gamehub, hedwig | Listed as safe in the source without a description |

## Keep list (not touched)
Wi-Fi, HDMI, audio, Settings, the Appstore, and the apps you installed. `com.amazon.tv.launcher` is only disabled by option 5, on purpose.

## Add or remove entries
Edit `tools/packages.txt` (and `packages-ads.txt` if it is ad-related): one package per line, `#` comments. Lines must be plain package names (letters, digits, `_`, `.`) and start with `com.amazon.`;
`scripts/audit.sh` and the tools reject anything else. Find candidates: `adb shell pm list packages -e | grep amazon`.
Change one package at a time and test the stick before adding more.
