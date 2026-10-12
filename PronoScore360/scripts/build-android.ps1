# Compila Android en Windows. Uso:
#   powershell -File scripts\build-android.ps1 debug
#   $env:API_BASE_URL="http://192.168.1.50:8000/api/v1/"; powershell -File scripts\build-android.ps1 debug   (teléfono físico)
#   powershell -File scripts\build-android.ps1 release    (AAB firmado; requiere android\keystore.properties)
param([string]$Variant = "debug")
$ErrorActionPreference = "Stop"
Set-Location (Join-Path (Split-Path -Parent $PSScriptRoot) "android")
$extra = @()
if ($env:API_BASE_URL) { $extra += "-PapiBaseUrl=$($env:API_BASE_URL)" }
if ($env:VERSION_CODE) { $extra += "-PversionCode=$($env:VERSION_CODE)" }
if ($Variant -eq "debug") { .\gradlew.bat assembleDebug @extra; Write-Host "APK: android\app\build\outputs\apk\debug\app-debug.apk" }
elseif ($Variant -eq "release") { if (-not (Test-Path keystore.properties)) { Write-Host "Falta android\keystore.properties" -ForegroundColor Red; exit 1 }
  .\gradlew.bat bundleRelease @extra; Write-Host "AAB: android\app\build\outputs\bundle\release\app-release.aab" }
else { Write-Host "uso: build-android.ps1 debug|release"; exit 1 }
