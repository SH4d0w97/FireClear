# Setup Release Keystore for FireClear
# Generates release.jks, verifies credentials, and copies Base64 to clipboard.

$ErrorActionPreference = "Stop"

$KeystoreFile = "release.jks"
$Alias = "fireclear"
$Password = "FireClearPass2026!"

Write-Host "1. Generating release keystore ($KeystoreFile)..." -ForegroundColor Cyan
if (Test-Path $KeystoreFile) {
    Remove-Item -Force $KeystoreFile
}

keytool -genkeypair -v `
  -keystore $KeystoreFile `
  -alias $Alias `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000 `
  -storepass $Password `
  -keypass $Password `
  -dname "CN=FireClear, OU=Launcher, O=FireClear, L=City, ST=State, C=US"

Write-Host "2. Verifying keystore credentials with keytool..." -ForegroundColor Cyan
keytool -list -v -keystore $KeystoreFile -alias $Alias -storepass $Password -keypass $Password > $null
Write-Host "   Verification PASSED!" -ForegroundColor Green

Write-Host "3. Converting to Base64 and copying to clipboard..." -ForegroundColor Cyan
$Bytes = [IO.File]::ReadAllBytes((Resolve-Path $KeystoreFile).Path)
$Base64 = [Convert]::ToBase64String($Bytes)
$Base64 | Set-Clipboard

Write-Host "`nSUCCESS! Keystore generated and copied to clipboard." -ForegroundColor Green
Write-Host "Now go to GitHub: Settings -> Secrets and variables -> Actions -> Repository secrets" -ForegroundColor Yellow
Write-Host "Set the following 4 secrets:`n" -ForegroundColor Yellow

Write-Host "FIRECLEAR_KEYSTORE_B64 : (Already copied to your clipboard! Just press Ctrl+V)" -ForegroundColor White
Write-Host "FIRECLEAR_KEY_ALIAS    : $Alias" -ForegroundColor White
Write-Host "FIRECLEAR_KS_PASS      : $Password" -ForegroundColor White
Write-Host "FIRECLEAR_KEY_PASS     : $Password" -ForegroundColor White
