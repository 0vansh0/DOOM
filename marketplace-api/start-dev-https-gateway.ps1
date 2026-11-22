param([int]$Port = 9443, [string]$Upstream = 'http://127.0.0.1:8787')

$ErrorActionPreference = 'Stop'
$upstreamUri = [Uri]$Upstream
if ($upstreamUri.Scheme -ne 'http' -or $upstreamUri.Host -notin @('127.0.0.1', 'localhost')) {
    throw 'Development gateway upstream must use HTTP on the local loopback interface.'
}
$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($listener) { throw "Port $Port already has a listener; refusing to replace it." }

$certificate = Get-ChildItem Cert:\CurrentUser\My |
    Where-Object { $_.Subject -eq 'CN=localhost' -and $_.Issuer -eq 'CN=DOOM Marketplace Debug CA 2026-11' -and $_.HasPrivateKey -and $_.NotAfter -gt (Get-Date) } |
    Sort-Object NotAfter -Descending | Select-Object -First 1
if (-not $certificate) { throw 'The local localhost gateway certificate/private key is unavailable or expired.' }

$directory = Join-Path $env:LOCALAPPDATA 'DOOM\MarketplaceDevHttps'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$pfxPath = Join-Path $directory 'localhost-server.pfx'
$passphrase = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$securePassphrase = ConvertTo-SecureString $passphrase -AsPlainText -Force
Export-PfxCertificate -Cert $certificate -FilePath $pfxPath -Password $securePassphrase -ChainOption BuildChain -Force | Out-Null

$env:DOOM_GATEWAY_PFX_PATH = $pfxPath
$env:DOOM_GATEWAY_PFX_PASSPHRASE = $passphrase
$env:DOOM_GATEWAY_PORT = [string]$Port
$env:DOOM_GATEWAY_UPSTREAM = $upstreamUri.AbsoluteUri.TrimEnd('/')
$stdout = Join-Path $directory 'gateway.stdout.log'
$stderr = Join-Path $directory 'gateway.stderr.log'
$node = (Get-Command node.exe).Source
$process = Start-Process -FilePath $node -ArgumentList 'marketplace-api/dev-https-gateway.mjs' `
    -WorkingDirectory (Resolve-Path (Join-Path $PSScriptRoot '..')).Path -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr
Start-Sleep -Seconds 2
if ($process.HasExited) {
    Get-Content -LiteralPath $stderr -ErrorAction SilentlyContinue
    throw 'The local HTTPS gateway exited during startup.'
}

[pscustomobject]@{ ProcessId = $process.Id; Listen = "127.0.0.1:$Port"; Upstream = $env:DOOM_GATEWAY_UPSTREAM; CertificateExpires = $certificate.NotAfter }
