# Horizon TV

Launcher independente para Android TV, inspirado no painel Horizon: fundo sci-fi, navegação superior, dock central com ícones circulares, atalhos para UniTV e SmartTube, clima local com bairro, manchetes brasileiras e relógio.

## Capturas da interface

As imagens abaixo foram capturadas em uma TCL com Android TV 12, em 1920×1080. O bairro da localização real foi ocultado nas cópias públicas.

**Tela inicial**

![Horizon TV em execução na TCL Android TV](docs/screenshots/horizon-tv-home.png)

**Cartão de clima selecionado pelo controle remoto**

![Cartão de clima em foco no Horizon TV](docs/screenshots/horizon-tv-weather-focus.png)

Os cartões e a dock são dimensionados pela largura e altura da tela; a faixa inferior ocupa 98% da largura disponível.

## Dados e privacidade

- O app solicita somente localização aproximada (`ACCESS_COARSE_LOCATION`) e só a consulta enquanto a tela inicial está aberta. Não há rastreamento em segundo plano.
- As coordenadas são arredondadas para duas casas decimais e guardadas nas preferências locais para reutilizar a previsão. São enviadas por HTTPS à Open-Meteo para o clima e ao OpenStreetMap Foundation (Nominatim) para identificar o bairro aproximado.
- A consulta Nominatim é feita uma vez na configuração inicial e repetida somente após deslocamento aproximado de 2,5 km e no máximo uma vez por dia; o nome retornado fica em cache local. Se o mapa não tiver um bairro, a tela usa a cidade. O ponto mais próximo do mapa pode não corresponder exatamente à coordenada informada.
- Na primeira consulta, uma confirmação explica o envio ao OpenStreetMap; a pessoa pode recusar e continuar usando o clima sem o nome do bairro. Para apagar o bairro salvo, limpe os dados do Horizon em Configurações do Android TV.
- O clima usa condições atuais da Open-Meteo, com atualização a cada 30 minutos. A tela mostra a fonte. Consulte [documentação](https://open-meteo.com/en/docs) e [termos](https://open-meteo.com/en/terms); o serviço gratuito é destinado a usos não comerciais e requer atribuição.
- O nome do bairro vem de uma consulta reversa Nominatim, com agente `HorizonTV/1.0`, atribuição `© OpenStreetMap contributors` na tela e cache para evitar consultas periódicas. Aplicam-se a [documentação da API](https://nominatim.org/release-docs/latest/api/Reverse/) e a [política de uso](https://operations.osmfoundation.org/policies/nominatim/).
- Notícias são manchetes gerais do Brasil, obtidas por RSS do [G1](https://g1.globo.com/rss/g1/) e da [Agência Brasil](https://agenciabrasil.ebc.com.br/rss/ultimasnoticias/feed.xml). A tela atualiza a cada 15 minutos, identifica a fonte e abre os artigos apenas por HTTPS nos domínios dessas fontes.
- Conteúdo RSS é externo e tratado como dado: o parser limita a resposta a 512 KiB, bloqueia DTD/entidades externas, e os links são validados por protocolo e host. As preferências que incluem coordenadas ficam excluídas do backup e da transferência do Android.
- Permissões declaradas: internet e localização aproximada. Tráfego HTTP sem TLS é desativado. O app não pede conta, senha, microfone, armazenamento ou localização precisa.

## Compilar e verificar

Requisitos locais: JDK 21 e Android SDK com plataforma 34 e Build Tools instaladas. O alvo mínimo é Android 8 (API 26); a TV informada roda Android 12 (API 31).

```powershell
.\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

O APK é `app/build/outputs/apk/release/app-release.apk`. Os testes unitários cobrem parsing RSS, remoção de marcação, rejeição de URLs inseguros, bloqueio de entidades XML e descrições meteorológicas. O lint e o build release fazem parte do mesmo gate.

## Instalação e retorno

Com depuração de rede ativada na TV e ADB pareado, instale e abra sem alterar o launcher padrão:

```powershell
$env:TV_HOST = '<ip-da-tv>:5555'
.\scripts\install.ps1
```

Após conferir a tela, é possível definir Horizon como tela inicial com `-SetAsHome`. O launcher anterior `com.gathin.tvhome` permanece instalado. Para voltar:

```powershell
$env:TV_HOST = '<ip-da-tv>:5555'
.\scripts\restore-home.ps1
```

O smoke em TV inicia a tela inicial pelo fluxo HOME, aguarda a atualização de dados e salva uma captura em `artifacts/`:

```powershell
$env:TV_HOST = '<ip-da-tv>:5555'
.\scripts\smoke-tv.ps1
```

O usuário pode conceder ou negar localização na própria tela. Se a TV não tiver um provedor de localização de rede habilitado, o painel continua funcionando e mostra o estado do clima; nenhuma localização é inferida a partir do IP. Para abrir artigos é necessário ter um navegador instalado.

## Análise de segurança (2026-10-23)

- **XML externo / CWE-611** — feeds RSS são entrada não confiável; DTD, entidades externas e resolução de DTD são desativadas. Verificação: `NewsFeedReaderTest.rejectsExternalEntityDeclarations`; resultado esperado: parser rejeita o documento. Referência oficial: https://cwe.mitre.org/data/definitions/611 .
- **Comunicação / ASVS 5.0.0** — apenas HTTPS, hosts de notícia permitidos e sem tráfego cleartext; feed limitado por tamanho e timeouts. Verificação: testes de URL HTTPS/host e configuração do Manifest. Referência: https://asvs.owasp.org/ (versão 5.0.0, estável em 2025-05-30).
- **Localização e menor privilégio** — apenas localização aproximada em primeiro plano; coordenadas arredondadas e sem backup. O cache evita consultas repetidas ao Nominatim. Verificação: inspeção de `AndroidManifest.xml`, fluxo de permissão e regras de extração. Referências: https://developer.android.com/develop/sensors-and-location/location/permissions e https://operations.osmfoundation.org/policies/nominatim/ .
- **Exposição de localização — severidade média, consentida** — o Nominatim recebe coordenadas aproximadas para nomear o bairro; aviso de consentimento, arredondamento, cache local e consulta limitada reduzem exposição. Resultado consultado via HTTPS; validação manual do ponto de exemplo confirmou resposta de bairro. Fontes oficiais consultadas em 2026-10-23: https://nominatim.org/release-docs/latest/api/Reverse/ e https://operations.osmfoundation.org/policies/nominatim/ .
- **Dependências** — não há dependências externas de runtime. Consulta à API OSV em 2026-10-23 retornou 0 avisos para JUnit 4.13.2 e `org.json:json` 20260814, usados somente em testes; plugins Gradle/Android SDK são dependências de build, não incluídas no APK.
- **Risco residual** — disponibilidade e conteúdo dos feeds/API dependem dos provedores e da rede da TV. A fonte dos dados, não o app, define a exatidão meteorológica. Não foi feita auditoria independente dos provedores nem varredura de rede externa.

## Recursos do projeto

Implementação Java nativa, sem dependências de runtime de terceiros. JUnit e `org.json` são usados só nos testes locais. O projeto mantém o launcher `cleanyourtv` intacto e usa o wallpaper sci-fi aprovado como arte local.
