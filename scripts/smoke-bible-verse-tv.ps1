param(
    [string]$TvHost = $env:TV_HOST,
    [int]$WaitSeconds = 35
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
    if ($LASTEXITCODE -ne 0) { throw 'Compilação, testes unitários ou lint falharam.' }
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

foreach ($key in @(19, 19, 22, 22, 22, 20, 22)) {
    & $adb -s $TvHost shell input keyevent $key | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível navegar até o cartão de versículo.' }
}

$activities = & $adb -s $TvHost shell dumpsys activity activities
if ($LASTEXITCODE -ne 0 -or ($activities -join "`n") -notmatch "mResumedActivity:.*$([regex]::Escape($packageName))") {
    throw 'Horizon não permaneceu em primeiro plano durante o smoke.'
}
$hierarchy = & $adb -s $TvHost shell uiautomator dump /sdcard/horizon-bible-verse.xml 2>&1
if ($LASTEXITCODE -ne 0) { throw "Não foi possível inspecionar o foco da TV: $hierarchy" }
$ui = & $adb -s $TvHost shell cat /sdcard/horizon-bible-verse.xml
if (($ui -join "`n") -notmatch 'Versículo bíblico do dia') {
    throw 'O fluxo HOME > TV ao vivo > cartão de versículo não chegou ao controle focado esperado.'
}
& $adb -s $TvHost shell input keyevent 66 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível solicitar a atualização pelo cartão.' }
Start-Sleep -Seconds 5

$artifactDir = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$screenshot = Join-Path $artifactDir "bible-verse-tv-$stamp.png"
& $adb -s $TvHost shell screencap -p /sdcard/horizon-bible-verse.png | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível capturar a tela da TV.' }
& $adb -s $TvHost pull /sdcard/horizon-bible-verse.png $screenshot | Out-Null
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $screenshot)) { throw 'Não foi possível salvar a captura do smoke.' }
Write-Output "PASS: fluxo HOME > cartão de versículo > OK executado após ${WaitSeconds}s."
Write-Output "Revise visualmente o texto, a referência e o relógio da barra superior: $screenshot"
