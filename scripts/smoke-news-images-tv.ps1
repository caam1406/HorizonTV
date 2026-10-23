param(
    [string]$TvHost = $env:TV_HOST,
    [int]$WaitSeconds = 20
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$packageName = 'br.com.suporte.horizontv'

if (-not $TvHost) { throw 'Informe TV_HOST ou passe -TvHost <ip:porta>.' }
if (-not (Test-Path $adb)) { throw "ADB não encontrado em $adb." }

Push-Location $projectRoot
try {
    & .\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Compilação ou gates falharam.' }
} finally {
    Pop-Location
}

& $adb connect $TvHost | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível conectar ao ADB da TV.' }
& $adb -s $TvHost install -r (Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk') | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'A instalação do Horizon TV falhou.' }
& $adb -s $TvHost shell input keyevent HOME | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível abrir a tela inicial da TV.' }
Start-Sleep -Seconds $WaitSeconds

$activities = & $adb -s $TvHost shell dumpsys activity activities
if ($LASTEXITCODE -ne 0 -or ($activities -join "`n") -notmatch 'mResumedActivity:.*br\.com\.suporte\.horizontv') {
    throw 'Horizon não permaneceu em primeiro plano durante o smoke.'
}

$artifactDir = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$screenshot = Join-Path $artifactDir "news-images-tv-$stamp.png"
& $adb -s $TvHost shell screencap -p /sdcard/horizon-news-images.png | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível capturar a tela da TV.' }
& $adb -s $TvHost pull /sdcard/horizon-news-images.png $screenshot | Out-Null
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $screenshot)) { throw 'Não foi possível salvar a captura do smoke.' }
Write-Output "PASS: Horizon ficou aberto após a atualização de manchetes e imagens por ${WaitSeconds}s."
Write-Output "Revise visualmente as três imagens nos cards Em alta: $screenshot"
