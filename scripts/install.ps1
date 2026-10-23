param([switch]$SetAsHome, [string]$TvHost = $env:TV_HOST)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$apk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'

if (-not $TvHost) { throw 'Informe TV_HOST ou passe -TvHost <ip:porta>.' }
if (-not (Test-Path $adb)) { throw "ADB não encontrado em $adb." }
Push-Location $projectRoot
try { & .\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease; if ($LASTEXITCODE -ne 0) { throw 'A compilação ou os gates falharam.' } }
finally { Pop-Location }

& $adb connect $TvHost
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível conectar ao ADB da TV.' }
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'A instalação do Horizon TV falhou.' }
& $adb shell pm grant br.com.suporte.horizontv android.permission.ACCESS_COARSE_LOCATION
if ($LASTEXITCODE -ne 0) { Write-Warning 'A localização não foi concedida; o app solicitará a permissão na TV.' }

if ($SetAsHome) {
    & $adb shell cmd package set-home-activity br.com.suporte.horizontv/.HomeActivity
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível definir Horizon como tela inicial.' }
    & $adb shell input keyevent HOME
} else {
    & $adb shell am start -n br.com.suporte.horizontv/.HomeActivity
}
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível abrir Horizon TV.' }
