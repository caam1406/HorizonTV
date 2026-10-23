param([string]$TvHost = $env:TV_HOST, [int]$WaitSeconds = 45)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$apk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
$packageName = 'br.com.suporte.horizontv'

function Send-Key([string]$Key) {
    & $adb shell input keyevent $Key | Out-Null
    Start-Sleep -Milliseconds 500
}

function Save-Hierarchy([string]$Path, [string]$RemotePath) {
    & $adb shell uiautomator dump $RemotePath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível inspecionar a tela da launcher.' }
    & $adb pull $RemotePath $Path | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível salvar a hierarquia de acessibilidade.' }
    return Get-Content -LiteralPath $Path -Raw
}

if (-not $TvHost) { throw 'Informe TV_HOST ou passe -TvHost <ip:porta>.' }
Push-Location $projectRoot
try {
    & .\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Os testes, lint ou build falharam.' }
} finally { Pop-Location }

& $adb connect $TvHost | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível conectar à TCL via ADB.' }
& $adb install -r $apk | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'A instalação do Horizon falhou.' }
& $adb shell am force-stop com.android.tv.settings | Out-Null
& $adb shell am force-stop $packageName | Out-Null
& $adb shell am start -n "$packageName/.HomeActivity" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível abrir o Horizon pela atividade de TV.' }
Start-Sleep -Seconds 5
$foreground = (& $adb shell dumpsys activity activities) -join "`n"
if ($foreground -notmatch [regex]::Escape($packageName)) { throw 'A launcher não ficou em primeiro plano.' }

for ($step = 0; $step -lt 7; $step++) {
    Send-Key 'KEYCODE_DPAD_RIGHT'
}
Send-Key 'KEYCODE_DPAD_CENTER'
Start-Sleep -Seconds 1
Send-Key 'KEYCODE_DPAD_DOWN'
Send-Key 'KEYCODE_DPAD_CENTER'
Start-Sleep -Seconds 1
Send-Key 'KEYCODE_DPAD_DOWN'
Send-Key 'KEYCODE_DPAD_DOWN'
Send-Key 'KEYCODE_DPAD_CENTER'
Start-Sleep -Seconds $WaitSeconds

$artifactDir = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$menuXml = Join-Path $artifactDir "horizon-background-menu-$stamp.xml"
$menuScreenshot = Join-Path $artifactDir "horizon-background-$stamp.png"
Send-Key 'KEYCODE_DPAD_CENTER'
Start-Sleep -Seconds 1
$screenText = Save-Hierarchy $menuXml '/sdcard/horizon-background-menu.xml'
if ($screenText -notmatch 'Personalizar fundo' -or $screenText -notmatch 'forest nature landscape') {
    throw "A busca escolhida não apareceu no menu Personalizar. Evidência: $menuXml"
}
for ($step = 0; $step -lt 4; $step++) { Send-Key 'KEYCODE_DPAD_DOWN' }
Send-Key 'KEYCODE_DPAD_CENTER'
Start-Sleep -Seconds 1
$creditsXml = Join-Path $artifactDir "horizon-background-credits-$stamp.xml"
$creditsScreenshot = Join-Path $artifactDir "horizon-background-credits-$stamp.png"
$creditText = Save-Hierarchy $creditsXml '/sdcard/horizon-background-credits.xml'
if ($creditText -notmatch 'Créditos da imagem' -or $creditText -notmatch 'ABRIR ORIGEM') {
    throw "A tela de créditos da foto não abriu. Evidência: $creditsXml"
}
& $adb exec-out screencap -p > $creditsScreenshot
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $creditsScreenshot)) { throw 'Não foi possível salvar a captura dos créditos.' }
Send-Key 'KEYCODE_BACK'
& $adb exec-out screencap -p > $menuScreenshot
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $menuScreenshot)) { throw 'Não foi possível salvar a captura do fundo.' }
Write-Output "Menu, busca Natureza e créditos observados na TCL. Captura do fundo: $menuScreenshot"
Write-Output "Captura dos créditos: $creditsScreenshot"
