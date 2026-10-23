param([string]$TvHost = $env:TV_HOST)
$ErrorActionPreference = 'Stop'
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
if (-not $TvHost) { throw 'Informe TV_HOST ou passe -TvHost <ip:porta>.' }
& $adb connect $TvHost
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível conectar ao ADB da TV.' }
& $adb shell cmd package set-home-activity com.gathin.tvhome/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível restaurar o launcher anterior.' }
& $adb shell input keyevent HOME
if ($LASTEXITCODE -ne 0) { throw 'O launcher foi restaurado, mas a tecla HOME não foi executada.' }
