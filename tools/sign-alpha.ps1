param(
    [Parameter(Mandatory = $true)][string]$KeyStore,
    [string]$Alias = "doom-alpha",
    [string]$Sdk = $env:ANDROID_HOME
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($env:DOOM_ALPHA_SIGNING_PASSWORD)) {
    throw "Set DOOM_ALPHA_SIGNING_PASSWORD for this shell."
}
if ([string]::IsNullOrWhiteSpace($Sdk)) { throw "Set ANDROID_HOME or pass -Sdk." }
$root = Split-Path -Parent $PSScriptRoot
$build = Join-Path $root "app/build/outputs/apk"
$signer = Join-Path $Sdk "build-tools/34.0.0/apksigner.bat"
$inputApk = Join-Path $build "release/app-release-unsigned.apk"
$outputFolder = Join-Path $build "alpha"
$releaseApk = Join-Path $outputFolder "DOOM-0.1.0-alpha.apk"
$store = (Resolve-Path -LiteralPath $KeyStore).Path
New-Item -ItemType Directory -Force $outputFolder | Out-Null
& $signer sign --ks $store --ks-key-alias $Alias --ks-pass env:DOOM_ALPHA_SIGNING_PASSWORD --key-pass env:DOOM_ALPHA_SIGNING_PASSWORD --out $releaseApk $inputApk
if ($LASTEXITCODE) { throw "APK signing failed." }
& $signer verify --verbose --print-certs $releaseApk
if ($LASTEXITCODE) { throw "APK signature verification failed." }
Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256
