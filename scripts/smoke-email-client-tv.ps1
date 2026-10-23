param([string]$TvHost = $env:TV_HOST)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
$packageName = 'br.com.suporte.horizontv'
$apk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release.apk'
$artifactDir = Join-Path $projectRoot 'artifacts'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

function Invoke-TvAdb([string[]]$Arguments) {
    $result = & $adb -s $TvHost @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Falha no ADB: adb $($Arguments -join ' ')" }
    return $result
}

function Read-ScreenText([string]$Name) {
    $remotePath = "/sdcard/horizon-imap-$Name.xml"
    Invoke-TvAdb @('shell', 'rm', '-f', $remotePath) | Out-Null
    $dumpResult = Invoke-TvAdb @('shell', 'uiautomator', 'dump', $remotePath)
    if (($dumpResult -join ' ') -match 'ERROR:') { throw "Não foi possível ler a tela atual: $Name." }
    return (Invoke-TvAdb @('shell', 'cat', $remotePath) -join '')
}

if (-not $TvHost) { throw 'Informe TV_HOST ou passe -TvHost <ip:porta>.' }
Push-Location $projectRoot
try {
    & .\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Testes, lint ou build falharam.' }
} finally { Pop-Location }

Invoke-TvAdb @('connect', $TvHost) | Out-Null
$listenersBefore = (Invoke-TvAdb @('shell', 'settings', 'get', 'secure', 'enabled_notification_listeners') -join '').Trim()
if ($listenersBefore -match 'br\.com\.suporte\.horizontv\.EmailNotificationListener') {
    throw 'O acesso legado de notificações do Horizon precisa estar desativado antes do smoke IMAP.'
}
Invoke-TvAdb @('install', '-r', $apk) | Out-Null
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
Invoke-TvAdb @('shell', 'am', 'force-stop', $packageName) | Out-Null
Invoke-TvAdb @('shell', 'am', 'start', '-n', "$packageName/.HomeActivity") | Out-Null
Start-Sleep -Seconds 4
for ($step = 0; $step -lt 8; $step++) {
    Invoke-TvAdb @('shell', 'input', 'keyevent', 'DPAD_RIGHT') | Out-Null
    Start-Sleep -Milliseconds 500
}
$navigation = Read-ScreenText 'navigation'
if ($navigation -notmatch 'E-mail') { throw 'O item E-mail não apareceu após navegar pela barra superior.' }
Invoke-TvAdb @('shell', 'input', 'keyevent', 'DPAD_CENTER') | Out-Null
Start-Sleep -Seconds 3
$clientScreen = Read-ScreenText 'client'
$requiredLabels = @('E-mail', 'IMAP', 'Servidor IMAP', 'Porta com TLS', 'Usuário ou endereço da conta', 'Senha IMAP ou senha de app')
foreach ($label in $requiredLabels) {
    if ($clientScreen -notmatch [regex]::Escape($label)) { throw "A tela do cliente IMAP não mostrou: $label." }
}
if ($clientScreen -match 'Acesso a notificações|NotificationListenerService|Selecionar conta Google') {
    throw 'O fluxo de e-mail ainda apresentou recursos de notificações ou seleção Google.'
}
$fieldScreenshot = Join-Path $artifactDir "email-client-fields-$stamp.png"
$remoteFieldScreenshot = "/sdcard/email-client-fields-$stamp.png"
Invoke-TvAdb @('shell', 'screencap', '-p', $remoteFieldScreenshot) | Out-Null
Invoke-TvAdb @('pull', $remoteFieldScreenshot, $fieldScreenshot) | Out-Null
$actionsVisible = $false
for ($step = 0; $step -lt 12; $step++) {
	Invoke-TvAdb @('shell', 'input', 'keyevent', 'DPAD_DOWN') | Out-Null
	Start-Sleep -Milliseconds 300
	$scrolledScreen = Read-ScreenText 'client-actions'
	if (($scrolledScreen -match 'Conectar e salvar') -and ($scrolledScreen -match 'certificado validado')) {
		$actionsVisible = $true
		break
	}
}
if (-not $actionsVisible) { throw 'O botão Conectar e salvar não ficou acessível pela navegação do controle remoto.' }
$actionScreenshot = Join-Path $artifactDir "email-client-actions-$stamp.png"
$remoteActionScreenshot = "/sdcard/email-client-actions-$stamp.png"
Invoke-TvAdb @('shell', 'screencap', '-p', $remoteActionScreenshot) | Out-Null
Invoke-TvAdb @('pull', $remoteActionScreenshot, $actionScreenshot) | Out-Null
Invoke-TvAdb @('shell', 'input', 'keyevent', 'KEYCODE_BACK') | Out-Null
Start-Sleep -Seconds 2
$activityState = (Invoke-TvAdb @('shell', 'dumpsys', 'activity', 'activities') | Out-String)
if ($activityState -notlike '*br.com.suporte.horizontv/.HomeActivity*') {
    throw 'O botão Voltar não retornou à tela inicial do Horizon.'
}
$listenersAfter = (Invoke-TvAdb @('shell', 'settings', 'get', 'secure', 'enabled_notification_listeners') -join '').Trim()
if ($listenersAfter -ne $listenersBefore) {
    throw 'O acesso a notificações do aparelho mudou durante o smoke do cliente IMAP.'
}
Write-Output 'PASS: navegação normal até o cliente IMAP, configuração TLS visível, retorno ao Horizon e permissões de notificações inalteradas.'
Write-Output "Campos: $fieldScreenshot"
Write-Output "Ação: $actionScreenshot"
Write-Output 'Pré-condição da conexão real não atendida: o smoke não usa credenciais nem se conecta a uma caixa externa.'
