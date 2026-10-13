# RemoteDroid

Gerenciador e auditor ADB remoto em Android, com visual de terminal/hacker
(preto + verde "matrix", fonte monoespacada). Evolucao em app do script
`pegasus_pt.py`, controlando um aparelho alvo via Wi-Fi (Depuracao sem fio),
sem depender do binario `adb` nem de computador.

**Uso exclusivo em aparelhos proprios ou com autorizacao do dono.**
A conexao sempre exige pareamento manual no aparelho alvo (trava de
seguranca do proprio Android) — nao ha acesso oculto ou nao autorizado.

---

## Status: Fase 1 e Fase 2 concluidas

| Fase | Conteudo | Status |
|---|---|---|
| 1 | Esqueleto do app + conexao/pareamento ADB | ✅ |
| 2 | Telas de info do aparelho, apps, arquivos e logs | ✅ |
| 3 | Auditoria de seguranca | ⏳ |
| 4 | Espelhamento de tela | ⏳ |
| 5 | Workflow do GitHub Actions (build automatico do `.apk`) | ⏳ |

---

## Tecnologias

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Kadb** (`com.flyfishxu:kadb`) — cliente ADB 100% Kotlin: pareamento,
  shell, push/pull, install, sem precisar do binario `adb` nem de servidor
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
│   ├── adb/          # AdbConnectionManager (pareamento/conexao) e AdbRepository (comandos shell)
│   ├── model/         # DeviceInfo, AppInfo, RemoteFile, ConnectionHistoryItem
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
        └── logs/         # Fase 2
```

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
> primeira vez — garanta conexao de internet liberada no Termux.

---

## Fluxo de uso do app

1. No aparelho alvo (**C**): *Opcoes do desenvolvedor > Depuracao sem fio >
   Parear com codigo*.
2. No RemoteDroid (aparelho **A**): tela **Parear** — informe IP:PORTA e o
   codigo de 6 digitos exibidos no aparelho alvo.
3. Depois de parear uma vez, use a tela **Conectar** com o IP:PORTA
   mostrado na tela principal de Depuracao sem fio (muda a cada sessao).
4. Use o **Painel** para navegar entre Info do aparelho, Apps, Arquivos e
   Logs.

---

## Limitacoes atuais (herdadas do plano original)

- Exige Depuracao sem fio ativa e pareada manualmente no aparelho alvo —
  nao ha como contornar essa trava do Android.
- Reiniciar/desligar o alvo derruba a conexao; e preciso reconectar.
- Em alguns fabricantes (Xiaomi, Oppo e parecidos), instalar apps
  remotamente pode exigir uma permissao extra ativada manualmente no
  aparelho alvo.
- Espelhamento de tela ainda nao implementado (Fase 4).
