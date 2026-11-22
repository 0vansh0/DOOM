param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [string]$Sdk = $env:ANDROID_HOME,
    [ValidateRange(3, 50)][int]$Iterations = 10
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Sdk)) { throw "Set ANDROID_HOME or pass -Sdk." }
$adb = Join-Path $Sdk "platform-tools/adb.exe"
$package = "org.doom.app"
$launch = @()
$memory = @()
for ($i = 0; $i -lt $Iterations; $i++) {
    & $adb -s $Serial shell am force-stop $package
    if ($LASTEXITCODE) { throw "Could not stop the app." }
    $result = & $adb -s $Serial shell am start -W -n "$package/.MainActivity"
    if ($LASTEXITCODE -or ($result -join " ") -notmatch 'TotalTime:\s+(\d+)') { throw "Missing launch time." }
    $launch += [int]$Matches[1]
    $result = (& $adb -s $Serial shell dumpsys meminfo $package) -join "`n"
    if ($result -notmatch 'TOTAL PSS:\s+(\d+)') { throw "Missing process PSS." }
    $memory += [int]$Matches[1]
}
function Percentile($values, [double]$percent) {
    $sorted = @($values | Sort-Object)
    return $sorted[[Math]::Ceiling($percent * $sorted.Count) - 1]
}
[ordered]@{
    device = (& $adb -s $Serial shell getprop ro.product.model).Trim()
    android = (& $adb -s $Serial shell getprop ro.build.version.release).Trim()
    api = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
    build = (& $adb -s $Serial shell getprop ro.build.display.id).Trim()
    coldStartMs = @{ runs = $launch; p50 = (Percentile $launch .50); p95 = (Percentile $launch .95) }
    afterLaunchPssKb = @{ runs = $memory; peak = ($memory | Measure-Object -Maximum).Maximum }
} | ConvertTo-Json -Depth 5
