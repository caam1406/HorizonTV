param([string]$TvHost = $env:TV_HOST)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$packageName = 'br.com.suporte.horizontv'
$hierarchyPath = '/sdcard/horizon-home-navigation.xml'

function Invoke-TvAdb([string[]]$Arguments) {
    $result = & $adb -s $TvHost @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Falha no ADB: adb $($Arguments -join ' ')" }
    return $result
}

if (-not $TvHost) { throw 'Informe TV_HOST ou passe -TvHost <ip:porta>.' }
Push-Location $projectRoot
try {
    & .\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Testes, lint ou build falharam.' }
} finally { Pop-Location }

& $adb connect $TvHost | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível conectar ao ADB da TV.' }
Invoke-TvAdb @('install', '-r', (Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk')) | Out-Null
Invoke-TvAdb @('shell', 'am', 'force-stop', $packageName) | Out-Null
Invoke-TvAdb @('shell', 'input', 'keyevent', 'HOME') | Out-Null
Start-Sleep -Seconds 4

$homeHierarchy = (Invoke-TvAdb @('shell', 'uiautomator', 'dump', $hierarchyPath) | Out-String)
$homeHierarchy += (Invoke-TvAdb @('shell', 'cat', $hierarchyPath) | Out-String)
if ($homeHierarchy -notmatch 'Horizon TV, tela inicial') { throw 'A tecla HOME não abriu o Horizon pela jornada normal da TV.' }

for ($step = 0; $step -lt 8; $step++) {
    Invoke-TvAdb @('shell', 'input', 'keyevent', 'DPAD_RIGHT') | Out-Null
    Start-Sleep -Milliseconds 300
}
$navigation = (Invoke-TvAdb @('shell', 'uiautomator', 'dump', $hierarchyPath) | Out-String)
$navigation += (Invoke-TvAdb @('shell', 'cat', $hierarchyPath) | Out-String)
if ($navigation -notmatch 'content-desc="Início"') {
    throw 'A barra superior não retornou ao item inicial após percorrer todos os itens.'
}

$artifactDir = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$screenshot = Join-Path $artifactDir "home-navigation-$stamp.png"
& $adb -s $TvHost exec-out screencap -p > $screenshot
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $screenshot)) {
    throw 'Não foi possível salvar a evidência da tela inicial.'
}

Write-Output 'PASS: tela inicial aberta pelo botão HOME e navegação percorreu os oito itens até voltar a Início.'
Write-Output "Captura: $screenshot"
