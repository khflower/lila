[CmdletBinding()]
param(
  [ValidateSet('start', 'status', 'stop')]
  [string]$Action = 'start',

  [ValidateSet('local', 'cloudflare')]
  [string]$Mode = 'local',

  [string]$WslDistro = 'Ubuntu',
  [string]$LilaDir = '/root/work/lila',
  [string]$LilaWsDir = '/root/work/lila-ws',
  [int]$ProxyPort = 9777,
  [switch]$SkipDocker,
  [string]$CloudflaredPath
)

$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$StateDir = Join-Path $env:TEMP 'omok-dev-starter'
$AppConfWin = "\\wsl$\$WslDistro\root\work\lila\conf\application.conf"
$WsConfWin = "\\wsl$\$WslDistro\root\work\lila-ws\src\main\resources\application.conf"
$ProxyScript = Join-Path $RepoRoot 'tools\quick-proxy\server.js'
$ProxyPidFile = Join-Path $StateDir 'quick-proxy.pid'
$ProxyLog = Join-Path $StateDir 'quick-proxy.log'
$CloudflarePidFile = Join-Path $StateDir 'cloudflared.pid'
$CloudflareOutLog = Join-Path $StateDir 'cloudflared.out.log'
$CloudflareErrLog = Join-Path $StateDir 'cloudflared.err.log'
$CloudflareUrlFile = Join-Path $StateDir 'cloudflare-url.txt'
$AppPidFile = Join-Path $StateDir 'lila-app.pid'
$WsPidFile = Join-Path $StateDir 'lila-ws.pid'
$DockerDesktopExe = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'

if (-not $CloudflaredPath) {
  $CloudflaredPath = Join-Path (Split-Path $RepoRoot -Parent) 'tools\cloudflared.exe'
}

New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

function Write-Step([string]$Message) {
  Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Set-Utf8NoBom([string]$Path, [string]$Content) {
  $enc = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($Path, $Content, $enc)
}

function Invoke-Wsl([string]$Command) {
  & wsl -d $WslDistro bash -lc $Command
}

function Get-WslPortPid([int]$Port) {
  $result = Invoke-Wsl "ss -ltnp | grep ':$Port ' | sed -nE 's/.*pid=([0-9]+).*/\1/p' | head -n 1"
  if ($LASTEXITCODE -ne 0) { return $null }
  $procId = ($result | Out-String).Trim()
  if ([string]::IsNullOrWhiteSpace($procId)) { return $null }
  return $procId
}

function Stop-WslPort([int]$Port) {
  $procId = Get-WslPortPid $Port
  if ($procId) {
    Write-Step "Stopping WSL process on port $Port (pid $procId)"
    & wsl kill $procId | Out-Null
    Start-Sleep -Seconds 1
  }
}

function Test-TcpPort([string]$HostName, [int]$Port, [int]$TimeoutMs = 1500) {
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $iar = $client.BeginConnect($HostName, $Port, $null, $null)
    if (-not $iar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) {
      $client.Close()
      return $false
    }
    $client.EndConnect($iar)
    $client.Close()
    return $true
  } catch {
    try { $client.Close() } catch {}
    return $false
  }
}

function Wait-TcpPort([string]$HostName, [int]$Port, [int]$TimeoutSec = 90) {
  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    if (Test-TcpPort $HostName $Port) { return }
    Start-Sleep -Milliseconds 500
  }
  throw "Timed out waiting for ${HostName}:$Port"
}

function Wait-WslPort([int]$Port, [int]$TimeoutSec = 120) {
  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    $procId = Get-WslPortPid $Port
    if ($procId) { return }
    Start-Sleep -Milliseconds 700
  }
  throw "Timed out waiting for WSL port $Port"
}

function Wait-HttpOk([string]$Url, [int]$TimeoutSec = 90) {
  $deadline = (Get-Date).AddSeconds($TimeoutSec)
  while ((Get-Date) -lt $deadline) {
    try {
      $response = Invoke-WebRequest -Uri $Url -Method Head -TimeoutSec 10 -UseBasicParsing
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) { return }
    } catch {}
    Start-Sleep -Milliseconds 700
  }
  throw "Timed out waiting for $Url"
}

function Read-PidFile([string]$Path) {
  if (-not (Test-Path $Path)) { return $null }
  $procId = (Get-Content $Path -Raw).Trim()
  if ([string]::IsNullOrWhiteSpace($procId)) { return $null }
  return [int]$procId
}

function Stop-TrackedProcess([string]$PidFile) {
  $procId = Read-PidFile $PidFile
  if (-not $procId) { return }
  $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
  if ($proc) {
    Write-Step "Stopping process $procId"
    Stop-Process -Id $procId -Force
  }
  Remove-Item $PidFile -ErrorAction SilentlyContinue
}

function Start-TrackedProcess([string]$FilePath, [string[]]$ArgumentList, [string]$StdOutLog, [string]$StdErrLog, [string]$PidFile) {
  Stop-TrackedProcess $PidFile
  if (Test-Path $StdOutLog) { Remove-Item $StdOutLog -Force }
  if (Test-Path $StdErrLog) { Remove-Item $StdErrLog -Force }
  $proc = Start-Process -FilePath $FilePath -ArgumentList $ArgumentList -RedirectStandardOutput $StdOutLog -RedirectStandardError $StdErrLog -PassThru -WindowStyle Hidden
  Set-Utf8NoBom $PidFile ($proc.Id.ToString())
  return $proc
}

function Ensure-Docker {
  if ($SkipDocker) { return }
  Write-Step 'Ensuring Docker Desktop support containers are available'
  try {
    docker version | Out-Null
  } catch {
    if (Test-Path $DockerDesktopExe) {
      Start-Process -FilePath $DockerDesktopExe | Out-Null
    }
    $deadline = (Get-Date).AddMinutes(2)
    while ((Get-Date) -lt $deadline) {
      try {
        docker version | Out-Null
        break
      } catch {
        Start-Sleep -Seconds 2
      }
    }
    docker version | Out-Null
  }

  if ((Test-TcpPort '127.0.0.1' 27017) -and (Test-TcpPort '127.0.0.1' 6379)) {
    Write-Step 'MongoDB and Redis ports are already live, keeping existing support containers'
    return
  }

  docker compose -f (Join-Path $RepoRoot 'docker-compose.omok-dev.yml') up -d | Out-Host
  if ($LASTEXITCODE -ne 0 -and ((Test-TcpPort '127.0.0.1' 27017) -and (Test-TcpPort '127.0.0.1' 6379))) {
    Write-Step 'Compose reported a port collision, but MongoDB and Redis are already up, so continuing'
  }
}

function Set-OrAppendLine([string]$Text, [string]$Pattern, [string]$Replacement) {
  if ([regex]::IsMatch($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::Multiline)) {
    $escapedReplacement = $Replacement -replace '\$', '$$'
    return [regex]::Replace($Text, $Pattern, $escapedReplacement, [System.Text.RegularExpressions.RegexOptions]::Multiline)
  }
  return ($Text.TrimEnd() + "`n" + $Replacement + "`n")
}

function Update-AppConfig([string]$HostName, [string]$Scheme) {
  Write-Step "Updating lila app config for ${Scheme}://${HostName}"
  $text = Get-Content $AppConfWin -Raw
  $text = Set-OrAppendLine $text '^net\.domain\s*=.*$' ('net.domain = "' + $HostName + '"')
  $text = Set-OrAppendLine $text '^net\.socket\.domains\s*=.*$' ('net.socket.domains = ["' + $HostName + '"]')
  $text = Set-OrAppendLine $text '^net\.asset\.domain\s*=.*$' 'net.asset.domain = ${net.domain}'
  $text = Set-OrAppendLine $text '^net\.asset\.base_url\s*=.*$' ('net.asset.base_url = "' + $Scheme + '://"${net.asset.domain}')
  $text = Set-OrAppendLine $text '^net\.base_url\s*=.*$' ('net.base_url = "' + $Scheme + '://"${net.domain}')
  Set-Utf8NoBom $AppConfWin $text
}

function Update-WsConfig([string]$PrimaryOrigin, [string[]]$ExtraOrigins) {
  Write-Step "Updating lila-ws CSRF origin allowlist"
  $text = Get-Content $WsConfWin -Raw
  $extraOrigins = $ExtraOrigins | Select-Object -Unique
  $extraBlock = "csrf.extraOrigins = [`n" + (($extraOrigins | ForEach-Object { '  "' + $_ + '"' }) -join ",`n") + "`n]"
  $text = Set-OrAppendLine $text '^csrf\.origin\s*=.*$' ('csrf.origin = "' + $PrimaryOrigin + '"')
  if ([regex]::IsMatch($text, 'csrf\.extraOrigins\s*=\s*\[(?s:.*?)\]', [System.Text.RegularExpressions.RegexOptions]::Multiline)) {
    $text = [regex]::Replace($text, 'csrf\.extraOrigins\s*=\s*\[(?s:.*?)\]', $extraBlock)
  } else {
    $text = $text.TrimEnd() + "`n" + $extraBlock + "`n"
  }
  Set-Utf8NoBom $WsConfWin $text
}

function Start-WslSbt([string]$Dir, [string]$LogName, [int]$Port, [string]$PidFile) {
  Stop-TrackedProcess $PidFile
  Stop-WslPort $Port
  $stdoutLog = Join-Path $StateDir $LogName
  $stderrLog = $stdoutLog + '.err'
  $launcherWin = "\\wsl$\$WslDistro\tmp\omok-start-$Port.sh"
  $launcherWsl = "/tmp/omok-start-$Port.sh"
  $launcherScript = @(
    '#!/usr/bin/env bash'
    'set -e'
    "cd $Dir"
    'exec sbt run'
    ''
  ) -join "`n"
  Set-Utf8NoBom $launcherWin $launcherScript
  Invoke-Wsl "chmod +x $launcherWsl" | Out-Null
  Start-TrackedProcess -FilePath 'wsl' -ArgumentList @('-d', $WslDistro, 'bash', $launcherWsl) -StdOutLog $stdoutLog -StdErrLog $stderrLog -PidFile $PidFile | Out-Null
  Wait-WslPort $Port 240
}

function Ensure-QuickProxy {
  if (-not (Test-Path $ProxyScript)) {
    throw "Quick proxy script not found: $ProxyScript"
  }
  if (Test-TcpPort '127.0.0.1' $ProxyPort) {
    Write-Step "Quick proxy already listening on 127.0.0.1:$ProxyPort"
    return
  }
  Write-Step 'Starting quick proxy'
  Start-TrackedProcess -FilePath 'node' -ArgumentList @($ProxyScript) -StdOutLog $ProxyLog -StdErrLog ($ProxyLog + '.err') -PidFile $ProxyPidFile | Out-Null
  Wait-TcpPort '127.0.0.1' $ProxyPort 30
}

function Start-CloudflareTunnel {
  if (-not (Test-Path $CloudflaredPath)) {
    throw "cloudflared.exe not found: $CloudflaredPath"
  }
  Write-Step 'Starting Cloudflare Quick Tunnel'
  Stop-TrackedProcess $CloudflarePidFile
  if (Test-Path $CloudflareOutLog) { Remove-Item $CloudflareOutLog -Force }
  if (Test-Path $CloudflareErrLog) { Remove-Item $CloudflareErrLog -Force }
  $psCommand = '& ''{0}'' tunnel --url ''http://127.0.0.1:{1}'' *>&1 | Tee-Object -FilePath ''{2}''' -f $CloudflaredPath, $ProxyPort, $CloudflareOutLog
  $proc = Start-Process -FilePath 'powershell' -ArgumentList @('-NoProfile', '-Command', $psCommand) -PassThru -WindowStyle Hidden
  Set-Utf8NoBom $CloudflarePidFile ($proc.Id.ToString())
  $deadline = (Get-Date).AddMinutes(2)
  while ((Get-Date) -lt $deadline) {
    $combined = ''
    if (Test-Path $CloudflareOutLog) { $combined = Get-Content $CloudflareOutLog -Raw -ErrorAction SilentlyContinue }
    $match = [regex]::Match($combined, 'https://[-a-z0-9]+\.trycloudflare\.com')
    if ($match.Success) {
      Set-Utf8NoBom $CloudflareUrlFile $match.Value
      return $match.Value
    }
    Start-Sleep -Seconds 1
  }
  throw 'Timed out waiting for Cloudflare quick tunnel URL'
}

function Get-CurrentTunnelUrl {
  if (Test-Path $CloudflareUrlFile) {
    return (Get-Content $CloudflareUrlFile -Raw).Trim()
  }
  return $null
}

function Start-Stack {
  Ensure-Docker
  Ensure-QuickProxy

  $publicUrl = $null
  if ($Mode -eq 'cloudflare') {
    $publicUrl = Start-CloudflareTunnel
    $uri = [Uri]$publicUrl
    Update-AppConfig -Host $uri.Host -Scheme 'https'
    Update-WsConfig -PrimaryOrigin $publicUrl -ExtraOrigins @('http://localhost:9663', "http://127.0.0.1:$ProxyPort", $publicUrl)
  } else {
    Update-AppConfig -Host "127.0.0.1:$ProxyPort" -Scheme 'http'
    Update-WsConfig -PrimaryOrigin "http://127.0.0.1:$ProxyPort" -ExtraOrigins @('http://localhost:9663', "http://127.0.0.1:$ProxyPort")
  }

  Write-Step 'Starting lila app'
  Start-WslSbt -Dir $LilaDir -LogName 'omok-lila-app.log' -Port 9663 -PidFile $AppPidFile

  Write-Step 'Starting lila-ws'
  Start-WslSbt -Dir $LilaWsDir -LogName 'omok-lila-ws.log' -Port 9664 -PidFile $WsPidFile

  Write-Step 'Verifying proxy entrypoint'
  Wait-HttpOk -Url "http://127.0.0.1:$ProxyPort/ko" -TimeoutSec 60

  if ($Mode -eq 'cloudflare') {
    Write-Step 'Verifying public Cloudflare entrypoint'
    Wait-HttpOk -Url ($publicUrl + '/ko') -TimeoutSec 60
    Write-Host "`nCloudflare URL: $publicUrl/ko" -ForegroundColor Green
  } else {
    Write-Host "`nLocal URL: http://127.0.0.1:$ProxyPort/ko" -ForegroundColor Green
  }
}

function Stop-Stack {
  Stop-TrackedProcess $CloudflarePidFile
  Stop-TrackedProcess $ProxyPidFile
  Stop-TrackedProcess $WsPidFile
  Stop-TrackedProcess $AppPidFile
  Stop-WslPort 9664
  Stop-WslPort 9663
}

function Show-Status {
  $appPid = Get-WslPortPid 9663
  $wsPid = Get-WslPortPid 9664
  $proxyUp = Test-TcpPort '127.0.0.1' $ProxyPort
  $cfPid = Read-PidFile $CloudflarePidFile
  $cfRunning = $false
  if ($cfPid -and (Get-Process -Id $cfPid -ErrorAction SilentlyContinue)) { $cfRunning = $true }
  $tunnelUrl = Get-CurrentTunnelUrl
  [pscustomobject]@{
    app9663 = [bool]$appPid
    appPid = $appPid
    ws9664 = [bool]$wsPid
    wsPid = $wsPid
    proxy = $proxyUp
    cloudflare = $cfRunning
    tunnelUrl = $tunnelUrl
    localUrl = "http://127.0.0.1:$ProxyPort/ko"
  } | Format-List
}

switch ($Action) {
  'start' { Start-Stack }
  'stop' { Stop-Stack }
  'status' { Show-Status }
}
