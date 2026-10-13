# RemoteDroid

Gerenciador e auditor ADB remoto em Android, com visual de terminal/hacker
(preto + verde "matrix", fonte monoespacada). Evolucao em app do script
`pegasus_pt.py`, controlando um aparelho alvo via Wi-Fi (Depuracao sem fio),
sem depender do binario `adb` nem de computador.

**Uso exclusivo em aparelhos proprios ou com autorizacao do dono.**
A conexao sempre exige pareamento manual no aparelho alvo (trava de
seguranca do proprio Android) — nao ha acesso oculto ou nao autorizado.

---

## Status: Fase 1 a Fase 4 concluidas

| Fase | Conteudo | Status |
|---|---|---|
| 1 | Esqueleto do app + conexao/pareamento ADB | ✅ |
| 2 | Telas de info do aparelho, apps, arquivos e logs | ✅ |
| 3 | Auditoria de seguranca | ✅ |
| 4 | Espelhamento de tela | ✅ |
| 5 | Workflow do GitHub Actions (build automatico do `.apk`) | ⏳ |

---

## Tecnologias

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Kadb** (`com.flyfishxu:kadb`) — cliente ADB 100% Kotlin: pareamento,
  shell, push/pull, install, streams brutos (sync, shell, localabstract),
  sem precisar do binario `adb` nem de servidor
- **MediaCodec** (API nativa do Android) — decodifica o H.264 do espelhamento
  de tela direto numa `Surface`, sem bibliotecas externas
- **scrcpy-server** (Genymobile/scrcpy, Apache 2.0) — reaproveitado como o
  "agente" que roda no aparelho alvo e captura a tela; baixado automaticamente
  em tempo de build (veja a secao "Espelhamento de tela" abaixo)
- **Navigation Compose** para as telas
- **DataStore Preferences** para o historico de conexoes
- Arquitetura simples: `ui/` (telas + ViewModels) e `core/` (conexao ADB,
  repositorio de comandos, modelos de dados, armazenamento local)

---

## Estrutura

```
app/src/main/java/com/remotedroid/app/
├── MainActivity.kt
├── RemoteDroidApp.kt
├── core/
│   ├── adb/          # AdbConnectionManager, AdbRepository, AuditRepository, MirrorStreamer
│   ├── model/         # DeviceInfo, AppInfo, RemoteFile, ConnectionHistoryItem, AuditModels, MirrorModels
│   └── storage/        # ConnectionHistoryStore (DataStore)
└── ui/
    ├── theme/         # Cores, tipografia e tema "hacker"
    ├── components/      # TerminalWindow, TerminalButton, ConsoleLog etc.
    ├── navigation/       # Rotas e NavGraph
    └── screens/
        ├── connect/      # Parear + Conectar (Fase 1)
        ├── home/         # Painel/dashboard
        ├── deviceinfo/     # Fase 2
        ├── apps/         # Fase 2
        ├── files/        # Fase 2
        ├── logs/         # Fase 2
        ├── audit/        # Fase 3 - auditoria de seguranca
        └── mirror/        # Fase 4 - espelhamento de tela ao vivo
```

---

## Espelhamento de tela (Fase 4)

A tela **Espelhamento** mostra a tela do aparelho alvo ao vivo, dentro do app,
sem precisar de computador nem do cliente `scrcpy`. Como funciona:

1. O app envia o `scrcpy-server.jar` (o mesmo "agente" de captura de tela do
   projeto [scrcpy](https://github.com/Genymobile/scrcpy), licenca Apache 2.0)
   para `/data/local/tmp/` no aparelho alvo, via Kadb.
2. Executa o servidor com `app_process` (um processo Java/ART normal, sem
   precisar de root) atraves de um stream de shell ADB que fica aberto durante
   toda a sessao.
3. Abre um stream ADB bruto para o socket local do servidor
   (`localabstract:scrcpy_<id>`) — o mesmo mecanismo que `adb forward` usa por
   baixo dos panos, sem precisar reservar porta TCP nenhuma.
4. Le o cabecalho do protocolo (nome do aparelho, resolucao) e, depois, o loop
   de pacotes H.264, entregando cada um ao `MediaCodec` do proprio Android, que
   decodifica e desenha direto numa `Surface` da tela.

Tres predefinicoes de qualidade (Baixa / Media / Alta) controlam resolucao
maxima, fps e bitrate pedidos ao servidor.

**O `scrcpy-server.jar` nao fica versionado no repositorio** (e um binario
compilado a partir de outro projeto). A tarefa Gradle `fetchScrcpyServer`
baixa a versao certa (pinada em `MirrorStreamer.SCRCPY_SERVER_VERSION`, hoje
`2.4`) direto do GitHub oficial do scrcpy antes de cada build — por isso o
build precisa de internet liberada (tanto local quanto no CI).

**Limitacoes desta fase:** somente visualizacao, sem toques/gestos (fica para
uma fase futura, como ja estava no plano original), sem audio, e uma rotacao
de tela no aparelho alvo exige reconectar o espelhamento.

---

## Build no Termux (Ubuntu/proot ou nativo)

1. Instale as dependencias:
   ```bash
   pkg install openjdk-17 gradle git -y
   ```
2. Dentro da pasta do projeto, gere o wrapper do Gradle (o `.jar` do wrapper
   nao fica versionado no git por ser binario):
   ```bash
   gradle wrapper --gradle-version 8.7
   ```
3. Compile o APK de debug:
   ```bash
   ./gradlew assembleDebug
   ```
   O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

4. Para instalar direto num aparelho ja conectado via ADB sem fio pelo
   `adb` do sistema (opcional, so pra teste local):
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

> Nota: o build completo baixa as dependencias do Google/Maven Central na
> primeira vez, alem do `scrcpy-server.jar` (fase 4, veja acima) a cada build
> limpo — garanta conexao de internet liberada no Termux.

---

## Fluxo de uso do app

1. No aparelho alvo (**C**): *Opcoes do desenvolvedor > Depuracao sem fio >
   Parear com codigo*.
2. No RemoteDroid (aparelho **A**): tela **Parear** — informe IP:PORTA e o
   codigo de 6 digitos exibidos no aparelho alvo.
3. Depois de parear uma vez, use a tela **Conectar** com o IP:PORTA
   mostrado na tela principal de Depuracao sem fio (muda a cada sessao).
4. Use o **Painel** para navegar entre Info do aparelho, Apps, Arquivos, Logs,
   Auditoria de seguranca e Espelhamento de tela.

---

## Limitacoes atuais (herdadas do plano original)

- Exige Depuracao sem fio ativa e pareada manualmente no aparelho alvo —
  nao ha como contornar essa trava do Android.
- Reiniciar/desligar o alvo derruba a conexao; e preciso reconectar.
- Em alguns fabricantes (Xiaomi, Oppo e parecidos), instalar apps
  remotamente pode exigir uma permissao extra ativada manualmente no
  aparelho alvo.
- Espelhamento de tela (Fase 4) e somente visualizacao por enquanto, sem
  controle por toque/gesto, e uma rotacao de tela no alvo exige reconectar.
