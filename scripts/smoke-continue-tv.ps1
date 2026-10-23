param(
    [string]$TvHost = $env:TV_HOST,
    [int]$WaitSeconds = 3
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$packageName = 'br.com.suporte.horizontv'
$temporaryXml = Join-Path $env:TEMP 'horizon-continue-window.xml'

if (-not $TvHost) { throw 'Informe TV_HOST ou passe -TvHost <ip:porta>.' }
if (-not (Test-Path $adb)) { throw "ADB não encontrado em $adb." }

function Invoke-TvAdb([string[]]$Arguments) {
    $result = & $adb -s $TvHost @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Falha no ADB: adb $($Arguments -join ' ')" }
    return $result
}

function Get-FocusedDescription {
    Invoke-TvAdb @('shell', 'uiautomator', 'dump', '/sdcard/horizon-continue-window.xml') | Out-Null
    Invoke-TvAdb @('pull', '/sdcard/horizon-continue-window.xml', $temporaryXml) | Out-Null
    [xml]$window = Get-Content -Raw -LiteralPath $temporaryXml
    $focusedNode = $window.SelectSingleNode('//*[@focused="true"]')
    if ($null -eq $focusedNode) { return '' }
    return [string]$focusedNode.GetAttribute('content-desc')
}

function Get-ResumedPackage {
    $activities = Invoke-TvAdb @('shell', 'dumpsys', 'activity', 'activities')
    $match = [regex]::Match(($activities -join "`n"), 'mResumedActivity:.*?\s([A-Za-z0-9._]+)/')
    if (-not $match.Success) { throw 'Não foi possível identificar o app em primeiro plano.' }
    return $match.Groups[1].Value
}

function Wait-ForFocusedDescription([string]$Expected, [int]$Attempts = 8) {
    for ($attempt = 0; $attempt -lt $Attempts; $attempt++) {
        $description = Get-FocusedDescription
        if ($description -eq $Expected) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "Foco esperado '$Expected' não apareceu. Última descrição: '$description'."
}

Push-Location $projectRoot
try {
    & .\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Compilação ou gates falharam.' }
} finally {
    Pop-Location
}

Invoke-TvAdb @('connect', $TvHost) | Out-Null
Invoke-TvAdb @('install', '-r', (Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk')) | Out-Null
Invoke-TvAdb @('shell', 'am', 'force-stop', $packageName) | Out-Null
Invoke-TvAdb @('shell', 'am', 'start', '-n', "$packageName/.HomeActivity") | Out-Null
Start-Sleep -Seconds $WaitSeconds

# Começa pela tela inicial e segue o foco real da dock até Configurações.
for ($step = 0; $step -lt 2; $step++) { Invoke-TvAdb @('shell', 'input', 'keyevent', 'DPAD_DOWN') | Out-Null }
$description = Get-FocusedDescription
for ($step = 0; $step -lt 30 -and $description -ne 'Configurações'; $step++) {
    Invoke-TvAdb @('shell', 'input', 'keyevent', 'DPAD_RIGHT') | Out-Null
    Start-Sleep -Milliseconds 200
    $description = Get-FocusedDescription
}
if ($description -ne 'Configurações') { throw "Configurações não apareceu na dock; foco atual: '$description'." }

Invoke-TvAdb @('shell', 'input', 'keyevent', 'DPAD_CENTER') | Out-Null
Start-Sleep -Seconds $WaitSeconds
$targetPackage = Get-ResumedPackage
if ($targetPackage -eq $packageName) { throw 'A seleção na dock não abriu o app de Configurações.' }
Write-Output "PASS: a dock abriu '$description' ($targetPackage)."

Invoke-TvAdb @('shell', 'input', 'keyevent', 'HOME') | Out-Null
Start-Sleep -Seconds $WaitSeconds
for ($step = 0; $step -lt 3; $step++) { Invoke-TvAdb @('shell', 'input', 'keyevent', 'DPAD_DOWN') | Out-Null }
Wait-ForFocusedDescription 'Continuar Configurações'

$artifactDir = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$screenshot = Join-Path $artifactDir "continue-card-$stamp.png"
Invoke-TvAdb @('shell', 'screencap', '-p', '/sdcard/horizon-continue.png') | Out-Null
Invoke-TvAdb @('pull', '/sdcard/horizon-continue.png', $screenshot) | Out-Null

Invoke-TvAdb @('shell', 'input', 'keyevent', 'DPAD_CENTER') | Out-Null
Start-Sleep -Seconds $WaitSeconds
if ((Get-ResumedPackage) -ne $targetPackage) { throw 'Selecionar Continuar não retomou o app aberto pela dock.' }
Write-Output "PASS: Continuar trouxe '$targetPackage' para frente."

Invoke-TvAdb @('shell', 'am', 'force-stop', $packageName) | Out-Null
Invoke-TvAdb @('shell', 'am', 'start', '-n', "$packageName/.HomeActivity") | Out-Null
Start-Sleep -Seconds $WaitSeconds
for ($step = 0; $step -lt 3; $step++) { Invoke-TvAdb @('shell', 'input', 'keyevent', 'DPAD_DOWN') | Out-Null }
Wait-ForFocusedDescription 'Continuar Configurações'
Invoke-TvAdb @('shell', 'input', 'keyevent', 'DPAD_CENTER') | Out-Null
Start-Sleep -Seconds $WaitSeconds
if ((Get-ResumedPackage) -ne $targetPackage) { throw 'Continuar não retomou o app após reiniciar o processo do Horizon.' }
Write-Output "PASS: após reiniciar o processo do Horizon, Continuar retomou '$targetPackage'."
Write-Output "Evidência de tela: $screenshot"
Invoke-TvAdb @('shell', 'input', 'keyevent', 'HOME') | Out-Null
