# Building, releasing and publishing

## A. Build free on GitHub (works from a tablet)
Every push to `main` starts the **build** workflow: security audit, Android lint, debug APK, built-APK permission check, SHA-256.
```bash
gh run list --limit 5
gh run watch <RUN_ID>
gh run view <RUN_ID> --log-failed        # if it fails: read this
gh run download <RUN_ID> -n FireClear-debug-apk -D ~/apk
cd ~/apk && sha256sum -c SHA256SUMS.txt  # expect: FireClear-debug.apk: OK
```
Or use the website: repo > **Actions** > **build** > latest run > **Artifacts**.

## B. Publish a release
```bash
git tag v2.3 && git push origin v2.3
```
The **release** workflow builds the APK, verifies it and attaches `FireClear-v2.3.apk` and `SHA256SUMS.txt` to a GitHub Release.

## C. Stable signing (optional)
Without a keystore every build is debug-signed with a fresh key, so updates fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (uninstall first).
For updates that install over each other, create a keystore once and store it as repo secrets. **Keep the `.jks` private; never commit it.**
```bash
pkg install -y openjdk-17          # Termux; on a PC use your JDK
keytool -genkeypair -keystore fireclear.jks -alias fireclear -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 fireclear.jks > ks.b64
gh secret set FIRECLEAR_KEYSTORE_B64 < ks.b64
gh secret set FIRECLEAR_KS_PASS
gh secret set FIRECLEAR_KEY_ALIAS
gh secret set FIRECLEAR_KEY_PASS
rm ks.b64
```
Back up `fireclear.jks` and its passwords somewhere safe: lose them and you can't ship updates that install over old ones.

## D. Build locally
- **Android Studio:** open the folder, accept creating the Gradle wrapper, **Build > Build APK(s)**.
- **Command line:** JDK 17, Android SDK (platform 34), Gradle 8.7: `gradle assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## E. Publish your own copy (Termux or any terminal)
```bash
pkg install -y git gh unzip
unzip FireClear.zip && cd FireClear
bash scripts/audit.sh                 # must print AUDIT PASSED
git config --global user.name "Your Name"
git config --global user.email "ID+username@users.noreply.github.com"   # GitHub > Settings > Emails
gh auth login                         # GitHub.com > HTTPS > browser code
git init -b main && git add . && git commit -m "FireClear"
gh repo create fireclear --public --source=. --remote=origin --push
```
Then in the repo's **Settings > Code security** enable private vulnerability reporting, Dependabot alerts and secret scanning, and replace `YOUR_USER` in the README badges.

## F. Updating
```bash
git add . && git commit -m "change" && git push
```
