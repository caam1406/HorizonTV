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
    if ($LASTEXITCODE -ne 0) { throw 'Compilação, testes unitários ou lint falharam.' }
} finally {
    Pop-Location
}

& $adb connect $TvHost | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível conectar ao ADB da TV.' }
& $adb -s $TvHost install -r (Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk') | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'A instalação do Horizon TV falhou.' }
& $adb -s $TvHost shell am force-stop $packageName | Out-Null
& $adb -s $TvHost shell monkey -p $packageName -c android.intent.category.LEANBACK_LAUNCHER 1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível iniciar o Horizon pela atividade de TV.' }
Start-Sleep -Seconds $WaitSeconds

$activities = & $adb -s $TvHost shell dumpsys activity activities
if ($LASTEXITCODE -ne 0 -or ($activities -join "`n") -notmatch "mResumedActivity:.*$([regex]::Escape($packageName))") {
    throw 'Horizon não permaneceu em primeiro plano durante o smoke.'
}
& $adb -s $TvHost shell input keyevent 20 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível focar o filme em destaque pelo controle remoto.' }
$hierarchy = & $adb -s $TvHost shell uiautomator dump /sdcard/horizon-film-suggestion.xml 2>&1
if ($LASTEXITCODE -ne 0) { throw "Não foi possível inspecionar o foco na TV: $hierarchy" }
$ui = & $adb -s $TvHost shell cat /sdcard/horizon-film-suggestion.xml
$uiText = $ui -join "`n"
$firstFilm = [regex]::Match($uiText, 'content-desc="Abrir ([^"]+) no TMDB"')
if (-not $firstFilm.Success) {
    throw 'O destaque não apresentou título de filme acessível após aguardar o catálogo.'
}
& $adb -s $TvHost shell input keyevent 22 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível mover o foco para Outra sugestão.' }
$hierarchy = & $adb -s $TvHost shell uiautomator dump /sdcard/horizon-film-suggestion.xml 2>&1
if ($LASTEXITCODE -ne 0) { throw "Não foi possível inspecionar o controle de atualização: $hierarchy" }
$ui = & $adb -s $TvHost shell cat /sdcard/horizon-film-suggestion.xml
if (($ui -join "`n") -notmatch 'Buscar outra sugestão de filme') {
    throw 'O controle Outra sugestão não está acessível pelo controle remoto.'
}
& $adb -s $TvHost shell input keyevent 66 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível solicitar outra sugestão de filme.' }
Start-Sleep -Seconds 15
& $adb -s $TvHost shell input keyevent 21 | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível voltar ao controle Ver no TMDB.' }
$hierarchy = & $adb -s $TvHost shell uiautomator dump /sdcard/horizon-film-suggestion.xml 2>&1
if ($LASTEXITCODE -ne 0) { throw "Não foi possível inspecionar o novo destaque: $hierarchy" }
$ui = & $adb -s $TvHost shell cat /sdcard/horizon-film-suggestion.xml
$secondFilm = [regex]::Match(($ui -join "`n"), 'content-desc="Abrir ([^"]+) no TMDB"')
if (-not $secondFilm.Success -or $secondFilm.Groups[1].Value -eq $firstFilm.Groups[1].Value) {
    throw 'Outra sugestão não atualizou o filme em destaque.'
}

$artifactDir = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$screenshot = Join-Path $artifactDir "film-suggestion-tv-$stamp.png"
& $adb -s $TvHost shell screencap -p /sdcard/horizon-film-suggestion.png | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível capturar a tela da TV.' }
& $adb -s $TvHost pull /sdcard/horizon-film-suggestion.png $screenshot | Out-Null
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $screenshot)) { throw 'Não foi possível salvar a captura do smoke.' }
Write-Output "PASS: HOME > Horizon > filme em destaque > Outra sugestão > novo filme executado na TV."
Write-Output "Revise o título, a sinopse e a imagem do filme nesta captura: $screenshot"
