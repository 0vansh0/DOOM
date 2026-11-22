param(
  [string]$ReportPath = ''
)

$ErrorActionPreference = 'Continue'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$results = [System.Collections.Generic.List[object]]::new()

function Invoke-Check {
  param(
    [string]$Name,
    [string]$WorkingDirectory,
    [string]$Executable,
    [string[]]$Arguments
  )
  Write-Host "`n=== $Name ===" -ForegroundColor Cyan
  Push-Location $WorkingDirectory
  $timer = [System.Diagnostics.Stopwatch]::StartNew()
  try {
    $output = & $Executable @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($null -eq $exitCode) { $exitCode = 0 }
    $output | ForEach-Object { Write-Host $_ }
  } catch {
    Write-Host $_ -ForegroundColor Red
    $exitCode = 1
  } finally {
    $timer.Stop()
    Pop-Location
  }
  $combined = ($output | Out-String)
  $hasSkippedTests = $combined -match '(?im)\b[1-9]\d*\s+skipped\b|\bskipped\s+[1-9]\d*\b'
  $status = if ($exitCode -ne 0) { 'FAIL' } elseif ($hasSkippedTests) { 'PARTIAL' } else { 'PASS' }
  $results.Add([pscustomobject]@{ Name = $Name; Status = $status; ExitCode = $exitCode; Seconds = [math]::Round($timer.Elapsed.TotalSeconds, 1) })
  $color = if ($status -eq 'PASS') { 'Green' } elseif ($status -eq 'PARTIAL') { 'Yellow' } else { 'Red' }
  Write-Host "$status ($exitCode) - $Name" -ForegroundColor $color
}

Invoke-Check 'Documentation local-link validation' $repository 'node' @('tools/maintenance/check-doc-links.mjs')
Invoke-Check 'Maintenance tool tests' $repository 'node' @('--test', 'tools/maintenance/*.test.mjs')

foreach ($project in @('doom-cli', 'visual-builder', 'marketplace-api', 'developer-portal')) {
  Invoke-Check "npm dependency audit: $project" (Join-Path $repository $project) 'npm' @('audit', '--audit-level=high')
}

# These projects share CLI build output; keep them serialized to avoid concurrent writes.
foreach ($project in @('doom-cli', 'visual-builder', 'marketplace-api', 'developer-portal', 'website')) {
  Invoke-Check "project tests: $project" (Join-Path $repository $project) 'npm' @('test')
  if ($project -in @('visual-builder', 'developer-portal', 'website')) {
    Invoke-Check "project production build: $project" (Join-Path $repository $project) 'npm' @('run', 'build')
  }
}

Invoke-Check 'Android mod-engine tests' $repository '.\gradlew.bat' @(':mod-engine:test', '--rerun-tasks')
Invoke-Check 'Android app unit tests' $repository '.\gradlew.bat' @(':app:testDebugUnitTest')
Invoke-Check 'Android debug APK build' $repository '.\gradlew.bat' @(':app:assembleDebug')

$results | Format-Table -AutoSize
if ($ReportPath) {
  $generated = [DateTimeOffset]::Now.ToString('yyyy-MM-dd HH:mm:ss zzz')
  $report = @('# DOOM local regression run', '', ('Generated: ' + $generated), '', '| Check | Result | Exit | Seconds |', '|---|---:|---:|---:|')
  foreach ($result in $results) { $report += ('| {0} | {1} | {2} | {3} |' -f $result.Name, $result.Status, $result.ExitCode, $result.Seconds) }
  $report += '', 'PARTIAL means a command exited successfully but reported skipped tests. Review full command output; skipped integration/device cases are not pass results.'
  Set-Content -LiteralPath $ReportPath -Value ($report -join [Environment]::NewLine) -Encoding utf8
  Write-Host "Wrote summary: $ReportPath"
}

if ($results.Where({ $_.Status -eq 'FAIL' }).Count -gt 0) { exit 1 }
exit 0
