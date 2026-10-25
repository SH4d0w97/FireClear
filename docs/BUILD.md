# Building, Releasing and Publishing

This document describes how to build FireClear APKs locally or via GitHub Actions, how to configure automated releases, and how to set up release signing.

---

## 🏗️ A. Automatic Builds via GitHub Actions

Every push to `main` automatically triggers the **build** workflow:
1. **Static security audit** (`scripts/audit.sh`) — validates zero dependencies, zero network calls, zero unapproved permissions, and no binary blobs.
2. **Android Lint** — checks for code quality and compatibility warnings.
3. **Assemble Debug APK** — builds `FireClear-debug.apk`.
4. **Binary APK Permission Check** (`scripts/verify-apk.sh`) — inspects the compiled APK binary to prove no `INTERNET` permission was injected.
5. **Checksum generation** — generates `SHA256SUMS.txt`.
6. **Artifact upload** — attaches the APK and checksums to the workflow run.

### Downloading the Build Artifacts
Via GitHub CLI (`gh`):
```bash
gh run list --workflow=build.yml --limit 5
gh run watch <RUN_ID>
gh run download <RUN_ID> -n FireClear-debug-apk -D ~/apk
cd ~/apk && sha256sum -c SHA256SUMS.txt
```
Or via GitHub Web:
Go to **Actions** → select **build** → click the latest run → scroll down to **Artifacts** → click **FireClear-debug-apk**.

---

## 🚀 B. Publishing a Release (Manual + Safe)

Releases are created only by manually running **Actions → release → Run workflow**.

### 1. Choose `bump_type`
The workflow requires one input:
- `patch`: `x.y.z -> x.y.(z+1)`
- `minor`: `x.y.z -> x.(y+1).0`
- `major`: `(x+1).0.0`

If `versionName` has fewer than 3 parts (for example `2.3`), it is normalized to `2.3.0` before the bump is applied. `versionCode` is always incremented by exactly 1.

### 2. What the release workflow does
`release.yml` performs these steps in order:
1. Reads current `versionName`/`versionCode` from `app/build.gradle.kts`.
2. Computes the next version from `bump_type`.
3. Fails early if `v<version>` tag or matching GitHub Release already exists.
4. Updates `app/build.gradle.kts` and prepends a dated release entry to `CHANGELOG.md`.
5. Commits those changes to the default branch as `github-actions[bot]`.
6. Creates and pushes tag `v<version>`.
7. Runs `scripts/audit.sh`, builds APK, and verifies it with `scripts/verify-apk.sh`.
8. Publishes a GitHub Release with:
   - `FireClear-v<version>.apk`
   - `SHA256SUMS.txt`
   - generated release notes.

### 3. Signing behavior
- If all signing secrets are present (`FIRECLEAR_KEYSTORE_B64`, `FIRECLEAR_KS_PASS`, `FIRECLEAR_KEY_ALIAS`, `FIRECLEAR_KEY_PASS`), the workflow builds a signed **release** APK.
- Otherwise it builds a **debug-signed** APK and labels the release body as testing/debug-signed.

### 4. Downloading the APK
Download release APKs from **GitHub Releases**: [../../releases/latest](../../releases/latest)

---

## 🔐 C. Stable Release Signing (Optional)

By default, builds without signing secrets are signed with Android's default debug key. Debug keys change across rebuilds and environments, which causes `INSTALL_FAILED_UPDATE_INCOMPATIBLE` when updating without uninstalling first.

For seamless in-place updates, create a keystore once and add it to your GitHub Repository Secrets:

### Step 1: Generate a Keystore

<details>
<summary>🪟 Windows (PowerShell / Command Prompt)</summary>

```powershell
keytool -genkeypair -v -keystore release.jks -alias fireclear -keyalg RSA -keysize 2048 -validity 10000
certutil -encode release.jks tmp.b64
# Copy the clean base64 content (excluding header/footer) from tmp.b64
```

</details>

<details>
<summary>🐧 Linux / macOS / Android Termux</summary>

```bash
keytool -genkeypair -v -keystore release.jks -alias fireclear -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks > ks.b64
```

</details>

> ⚠️ **IMPORTANT**: Never commit `release.jks` or `ks.b64` to git. `scripts/audit.sh` strictly fails the build if any `.jks`, `.keystore`, or binary file is checked in.

### Step 2: Configure GitHub Secrets

Navigate to: **GitHub Repo → Settings → Secrets and variables → Actions → New repository secret**

Add these 4 secrets:

| Secret Name | Value |
|---|---|
| `FIRECLEAR_KEYSTORE_B64` | Full base64-encoded string of `release.jks` |
| `FIRECLEAR_KS_PASS` | Password for the keystore |
| `FIRECLEAR_KEY_ALIAS` | `fireclear` (or the alias you specified) |
| `FIRECLEAR_KEY_PASS` | Password for the key alias |

---

## 💻 D. Building Locally

### Prerequisites
- **JDK:** OpenJDK 17
- **Android SDK:** Platform 34, Build-Tools 34+
- **Gradle:** 8.11.1

### Build commands
From the root directory:
```bash
# Security check
bash scripts/audit.sh

# Build debug APK
gradle assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk

# Verify permissions
bash scripts/verify-apk.sh app/build/outputs/apk/debug/app-debug.apk
```

---

## 📝 E. Changelog & Release History

See [CHANGELOG.md](../CHANGELOG.md) for full version history and changes per release.
