param([string]$TvHost = $env:TV_HOST, [int]$WaitSeconds = 25)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
if (-not $TvHost) { throw 'Informe TV_HOST ou passe -TvHost <ip:porta>.' }
& $adb connect $TvHost
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível conectar ao ADB da TV.' }
& $adb shell input keyevent HOME
if ($LASTEXITCODE -ne 0) { throw 'Falha ao abrir a tela inicial pelo controle HOME.' }
Start-Sleep -Seconds $WaitSeconds
$artifactDir = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$screenshot = Join-Path $artifactDir "horizon-tv-$stamp.png"
& $adb exec-out screencap -p > $screenshot
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $screenshot)) { throw 'Não foi possível capturar a tela da TV.' }
Write-Output "Captura: $screenshot"
