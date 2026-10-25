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

## 🚀 B. Publishing a Release

Releases are triggered automatically when you push a version tag starting with `v` (e.g. `v2.3`):

### 1. Create and push a tag
```bash
git tag v2.3
git push origin v2.3
```

### 2. What the Release Workflow does
The `release.yml` workflow:
- Runs security audits.
- Signs the APK with your release key (if repository secrets are configured) or produces a debug-signed build as fallback.
- Verifies permissions on the final APK.
- Generates `SHA256SUMS.txt`.
- Creates a new GitHub Release with release notes automatically attached, hosting `FireClear-v2.3.apk` and `SHA256SUMS.txt`.

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
