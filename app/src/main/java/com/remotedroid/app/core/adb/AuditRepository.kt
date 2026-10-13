package com.remotedroid.app.core.adb

import com.remotedroid.app.core.model.AuditFinding
import com.remotedroid.app.core.model.AuditLevel
import com.remotedroid.app.core.model.AuditReport
import com.remotedroid.app.core.model.AuditSection
import java.math.BigInteger
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Auditoria de seguranca do aparelho alvo (C) - port de "AUDITORIA DE SEGURANCA" do
 * pegasus_pt.py (secao 3.3 do plano).
 *
 * Todas as verificacoes sao SOMENTE LEITURA: nada e alterado no aparelho, so sao lidas
 * propriedades do sistema (getprop), pacotes instalados (pm/dumpsys) e o buffer de log
 * (logcat -d). Uso exclusivo em aparelhos proprios ou com autorizacao do dono.
 */
class AuditRepository(private val manager: AdbConnectionManager = AdbConnectionManager) {

    private data class AppScan(
        val pkg: String,
        val perms: Set<String>,
        val score: Int,
        val debuggable: Boolean,
        val installer: String
    )

    private val riskyPermissions: Map<String, Pair<String, Int>> = mapOf(
        "android.permission.RECORD_AUDIO" to ("Microfone" to 2),
        "android.permission.CAMERA" to ("Camera" to 2),
        "android.permission.READ_SMS" to ("Ler SMS" to 3),
        "android.permission.RECEIVE_SMS" to ("Receber SMS" to 3),
        "android.permission.SEND_SMS" to ("Enviar SMS" to 3),
        "android.permission.READ_CALL_LOG" to ("Registro de chamadas" to 3),
        "android.permission.PROCESS_OUTGOING_CALLS" to ("Chamadas realizadas" to 3),
        "android.permission.READ_CONTACTS" to ("Contatos" to 1),
        "android.permission.READ_PHONE_STATE" to ("Estado do telefone" to 1),
        "android.permission.ACCESS_FINE_LOCATION" to ("Localizacao precisa" to 2),
        "android.permission.ACCESS_BACKGROUND_LOCATION" to ("Localizacao em segundo plano" to 3),
        "android.permission.SYSTEM_ALERT_WINDOW" to ("Sobrepor outros apps" to 3),
        "android.permission.REQUEST_INSTALL_PACKAGES" to ("Instalar apps" to 2),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to ("Acesso a todos os arquivos" to 2),
        "android.permission.PACKAGE_USAGE_STATS" to ("Estatisticas de uso" to 2)
    )

    private val storeInstallers = setOf(
        "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia",
        "com.huawei.appmarket", "com.xiaomi.mipicks", "com.heytap.market",
        "com.google.android.feedback", "org.fdroid.fdroid"
    )

    private val rootPackages = setOf(
        "com.topjohnwu.magisk", "eu.chainfire.supersu", "com.koushikdutta.superuser",
        "com.noshufou.android.su", "com.thirdparty.superuser", "me.weishu.kernelsu",
        "com.kingroot.kinguser", "com.kingo.root", "org.lsposed.manager",
        "de.robv.android.xposed.installer", "com.devadvance.rootcloak"
    )

    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/sbin/su", "/vendor/bin/su",
        "/su/bin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/bin/.ext/.su",
        "/system/app/Superuser.apk"
    )

    private val logPatterns = listOf(
        Triple("Negacoes do SELinux", Regex("avc:\\s+denied", RegexOption.IGNORE_CASE), false),
        Triple("Negacoes de permissao / SecurityException", Regex("SecurityException|Permission Denial", RegexOption.IGNORE_CASE), false),
        Triple("Mencoes relacionadas a root", Regex("magisk|supersu|/system/xbin/su", RegexOption.IGNORE_CASE), true),
        Triple("Play Protect / verificacao de apps", Regex("play ?protect|verifyapps|PackageVerifier", RegexOption.IGNORE_CASE), false),
        Triple("Falhas de desbloqueio / bloqueio", Regex("lockout|failed attempt", RegexOption.IGNORE_CASE), false),
        Triple("Travamentos de apps (FATAL EXCEPTION)", Regex("FATAL EXCEPTION"), false)
    )

    // ------------------------------------------------------------------
    // Entrada principal
    // ------------------------------------------------------------------
    suspend fun runFullAudit(deviceLabel: String): AuditReport {
        val p = props()
        val sections = mutableListOf<AuditSection>()

        sections += auditRoot(p)
        val scan = scanApps()
        sections += auditPermissions(scan)
        sections += auditDebuggable(scan)
        sections += auditPatch(p)
        sections += auditEncryption(p)
        sections += auditLogs()
        sections += auditNetwork(p)

        val findings = sections.flatMap { it.findings }
        return AuditReport(
            generatedAt = System.currentTimeMillis(),
            deviceLabel = deviceLabel,
            sections = sections,
            okCount = findings.count { it.level == AuditLevel.OK },
            warnCount = findings.count { it.level == AuditLevel.WARN },
            badCount = findings.count { it.level == AuditLevel.BAD }
        )
    }

    // ------------------------------------------------------------------
    // Helpers de baixo nivel
    // ------------------------------------------------------------------
    private suspend fun props(): Map<String, String> {
        val out = manager.shell("getprop").output
        val regex = Regex("\\[(.+?)]: \\[(.*?)]")
        return regex.findAll(out).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private suspend fun packages(flag: String): List<String> =
        manager.shell("pm list packages $flag").output
            .lines()
            .mapNotNull { line -> line.removePrefix("package:").trim().ifBlank { null } }

    // ------------------------------------------------------------------
    // 1. Status de root
    // ------------------------------------------------------------------
    private suspend fun auditRoot(p: Map<String, String>): AuditSection {
        val findings = mutableListOf<AuditFinding>()

        val suCheck = "for sp in ${suPaths.joinToString(" ")}; do [ -e \$sp ] && echo \$sp; done"
        val found = manager.shell(suCheck).output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        findings += if (found.isNotEmpty()) {
            AuditFinding(AuditLevel.BAD, "Arquivos 'su' encontrados", found)
        } else {
            AuditFinding(AuditLevel.OK, "Nenhum binario 'su' comum encontrado")
        }

        val id = manager.shell("id").output.trim()
        findings += if (id.startsWith("uid=0")) {
            AuditFinding(AuditLevel.BAD, "O shell do ADB roda como root (comum em aparelhos com root ou builds userdebug)")
        } else {
            AuditFinding(AuditLevel.OK, "O shell do ADB roda como usuario 'shell' normal")
        }

        val installed = packages("").toSet()
        val hits = (installed intersect rootPackages).sorted()
        findings += if (hits.isNotEmpty()) {
            AuditFinding(AuditLevel.BAD, "Apps de root/hook instalados", hits)
        } else {
            AuditFinding(AuditLevel.OK, "Nenhum gerenciador de root conhecido instalado")
        }

        if (p["ro.build.tags"]?.contains("test-keys") == true) {
            findings += AuditFinding(AuditLevel.WARN, "Build assinada com test-keys (ROM personalizada ou sistema modificado)")
        }
        if (p["ro.debuggable"] == "1") {
            findings += AuditFinding(AuditLevel.WARN, "ro.debuggable=1 (build de desenvolvedor)")
        }
        if (p["ro.secure"] == "0") {
            findings += AuditFinding(AuditLevel.BAD, "ro.secure=0 (imagem de sistema insegura)")
        }

        return AuditSection("Status de root", findings)
    }

    // ------------------------------------------------------------------
    // 2. Varredura de apps + 3. Permissoes arriscadas / acessos especiais
    // ------------------------------------------------------------------
    private suspend fun scanApps(limit: Int = 250): List<AppScan> {
        var pkgs = packages("-3")
        if (pkgs.size > limit) pkgs = pkgs.take(limit)
        return pkgs.map { pkg ->
            val txt = manager.shell("dumpsys package $pkg").output
            val granted = Regex("([\\w.]+): granted=true").findAll(txt)
                .map { it.groupValues[1] }
                .filter { it in riskyPermissions }
                .toSet()
            val flags = Regex("(?:pkgFlags|flags)=\\[([^]]*)]").findAll(txt)
                .map { it.groupValues[1] }
                .toList()
            val installer = Regex("installerPackageName=(\\S+)").find(txt)?.groupValues?.get(1) ?: "null"
            AppScan(
                pkg = pkg,
                perms = granted,
                score = granted.sumOf { riskyPermissions.getValue(it).second },
                debuggable = flags.any { it.contains("DEBUGGABLE") },
                installer = installer
            )
        }
    }

    private suspend fun auditPermissions(scan: List<AppScan>): AuditSection {
        val findings = mutableListOf<AuditFinding>()

        val flagged = scan.filter { it.score >= 6 }.sortedByDescending { it.score }
        findings += if (flagged.isEmpty()) {
            AuditFinding(AuditLevel.OK, "Nenhum app tem uma combinacao pesada de permissoes sensiveis")
        } else {
            val lines = flagged.take(15).map { r ->
                val labels = r.perms.map { riskyPermissions.getValue(it).first }.sorted().joinToString(", ")
                "${r.score}  ${r.pkg}  ($labels)"
            }
            AuditFinding(
                AuditLevel.WARN,
                "${flagged.size} app(s) tem muitas permissoes sensiveis - revise se cada um realmente precisa delas",
                lines
            )
        }

        val odd = scan.filter { it.installer !in storeInstallers }.map { it.pkg }
        if (odd.isNotEmpty()) {
            findings += AuditFinding(
                AuditLevel.INFO,
                "${odd.size} app(s) nao vieram de uma loja conhecida (instalacao manual ou instalador desconhecido)",
                odd.take(15)
            )
        }

        val acc = manager.shell("settings get secure enabled_accessibility_services").output.trim()
        findings += if (acc.isNotEmpty() && acc != "null") {
            val svc = acc.split(":").filter { it.isNotBlank() }.map { it.substringBefore("/") }.distinct().sorted()
            AuditFinding(AuditLevel.WARN, "Servicos de acessibilidade ativos (podem ler/controlar a tela)", svc)
        } else {
            AuditFinding(AuditLevel.OK, "Nenhum servico de acessibilidade ativo")
        }

        val nl = manager.shell("settings get secure enabled_notification_listeners").output.trim()
        findings += if (nl.isNotEmpty() && nl != "null") {
            val svc = nl.split(":").filter { it.isNotBlank() }.map { it.substringBefore("/") }.distinct().sorted()
            AuditFinding(AuditLevel.WARN, "Leitores de notificacao ativos (podem ler todas as notificacoes)", svc)
        } else {
            AuditFinding(AuditLevel.OK, "Nenhum leitor de notificacoes ativo")
        }

        val dp = manager.shell("dumpsys device_policy").output
        val block = Regex("Enabled Device Admins.*?(?:\\n\\s*\\n|\\z)", RegexOption.DOT_MATCHES_ALL).find(dp)
        val admins = block?.value
            ?.let { Regex("([\\w.]+)/[\\w.$]+:").findAll(it).map { m -> m.groupValues[1] }.toSet() }
            ?.sorted() ?: emptyList()
        findings += if (admins.isNotEmpty()) {
            AuditFinding(AuditLevel.WARN, "Apps administradores do dispositivo (podem bloquear/apagar o celular)", admins)
        } else {
            AuditFinding(AuditLevel.OK, "Nenhum app administrador do dispositivo encontrado")
        }

        return AuditSection("Permissoes arriscadas e acessos especiais", findings)
    }

    // ------------------------------------------------------------------
    // 4. Apps depuraveis
    // ------------------------------------------------------------------
    private fun auditDebuggable(scan: List<AppScan>): AuditSection {
        val dbg = scan.filter { it.debuggable }.map { it.pkg }
        val findings = if (dbg.isNotEmpty()) {
            listOf(
                AuditFinding(
                    AuditLevel.WARN,
                    "${dbg.size} app(s) depuravel(is). Expoem dados a ferramentas de depuracao e normalmente sao builds de desenvolvimento",
                    dbg
                )
            )
        } else {
            listOf(AuditFinding(AuditLevel.OK, "Nenhum app de usuario depuravel"))
        }
        return AuditSection("Apps depuraveis", findings)
    }

    // ------------------------------------------------------------------
    // 5. Patch de seguranca e versao do Android
    // ------------------------------------------------------------------
    private fun auditPatch(p: Map<String, String>): AuditSection {
        val findings = mutableListOf<AuditFinding>()
        val patch = p["ro.build.version.security_patch"].orEmpty()
        val sdk = p["ro.build.version.sdk"]?.toIntOrNull() ?: 0

        findings += AuditFinding(AuditLevel.INFO, "Android ${p["ro.build.version.release"] ?: "?"} (API $sdk)")
        findings += AuditFinding(AuditLevel.INFO, "Patch de seguranca: ${patch.ifBlank { "desconhecido" }}")

        runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val patchDate = fmt.parse(patch) ?: error("sem data")
            val ageDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - patchDate.time)
            findings += when {
                ageDays <= 90 -> AuditFinding(AuditLevel.OK, "O patch e recente ($ageDays dias)")
                ageDays <= 180 -> AuditFinding(AuditLevel.WARN, "O patch tem $ageDays dias - procure atualizacoes do sistema")
                else -> AuditFinding(AuditLevel.BAD, "O patch tem $ageDays dias - atualize se o fabricante ainda der suporte a este aparelho")
            }
        }.onFailure {
            findings += AuditFinding(AuditLevel.WARN, "Nao foi possivel ler a data do patch")
        }

        if (sdk in 1..30) {
            findings += AuditFinding(AuditLevel.WARN, "Android 11 ou mais antigo normalmente nao recebe mais patches de seguranca")
        }

        return AuditSection("Patch de seguranca e versao do Android", findings)
    }

    // ------------------------------------------------------------------
    // 6. Criptografia e boot
    // ------------------------------------------------------------------
    private suspend fun auditEncryption(p: Map<String, String>): AuditSection {
        val findings = mutableListOf<AuditFinding>()

        when (val state = p["ro.crypto.state"]) {
            "encrypted" -> findings += AuditFinding(AuditLevel.OK, "O armazenamento esta criptografado (tipo ${p["ro.crypto.type"] ?: "desconhecido"})")
            null, "" -> findings += AuditFinding(AuditLevel.WARN, "Este aparelho nao informa o estado da criptografia")
            else -> findings += AuditFinding(AuditLevel.BAD, "Estado da criptografia: $state")
        }

        when (val vb = p["ro.boot.verifiedbootstate"]) {
            "green" -> findings += AuditFinding(AuditLevel.OK, "Verified boot: verde (sistema integro)")
            "orange" -> findings += AuditFinding(AuditLevel.BAD, "Verified boot: laranja (bootloader DESBLOQUEADO)")
            null, "" -> findings += AuditFinding(AuditLevel.INFO, "Estado do verified boot nao informado")
            else -> findings += AuditFinding(AuditLevel.WARN, "Estado do verified boot: $vb")
        }

        val locked = p["ro.boot.flash.locked"] ?: p["ro.boot.vbmeta.device_state"]
        when (locked) {
            "1", "locked" -> findings += AuditFinding(AuditLevel.OK, "O bootloader esta bloqueado")
            "0", "unlocked" -> findings += AuditFinding(AuditLevel.WARN, "O bootloader esta desbloqueado")
        }

        val selinux = manager.shell("getenforce").output.trim()
        if (selinux.isNotEmpty()) {
            findings += if (selinux == "Enforcing") {
                AuditFinding(AuditLevel.OK, "SELinux: Enforcing")
            } else {
                AuditFinding(AuditLevel.BAD, "SELinux: $selinux")
            }
        }

        return AuditSection("Criptografia e seguranca de boot", findings)
    }

    // ------------------------------------------------------------------
    // 7. Logs relacionados a seguranca
    // ------------------------------------------------------------------
    private suspend fun auditLogs(): AuditSection {
        val findings = mutableListOf<AuditFinding>()
        val lines = manager.shell("logcat -d -v threadtime").output.lines()
        findings += AuditFinding(AuditLevel.INFO, "Lidas ${lines.size} linha(s) de log")

        logPatterns.forEach { (label, regex, suspicious) ->
            val hits = lines.filter { regex.containsMatchIn(it) }
            findings += if (hits.isEmpty()) {
                AuditFinding(AuditLevel.OK, "$label: nenhum")
            } else {
                AuditFinding(
                    if (suspicious) AuditLevel.WARN else AuditLevel.INFO,
                    "$label: ${hits.size} linha(s)",
                    hits.take(3).map { it.take(150) }
                )
            }
        }

        return AuditSection("Logs relacionados a seguranca", findings)
    }

    // ------------------------------------------------------------------
    // 8. Conexoes e configuracoes de rede
    // ------------------------------------------------------------------
    private data class NetConn(val state: String, val localPort: Int, val remoteIp: InetAddress, val remotePort: Int, val uid: Int)

    private fun parseHexAddr(hex: String, v6: Boolean): Pair<InetAddress, Int> {
        val (ipHex, portHex) = hex.split(":")
        val ip: InetAddress = if (v6) {
            val bytes = ByteArray(16)
            for (i in 0 until 4) {
                val word = ipHex.substring(i * 8, i * 8 + 8)
                val wBytes = BigInteger(word, 16).toByteArray().let { b ->
                    // garante 4 bytes, preenchendo com zero a esquerda
                    val out = ByteArray(4)
                    val src = if (b.size > 4) b.copyOfRange(b.size - 4, b.size) else b
                    System.arraycopy(src, 0, out, 4 - src.size, src.size)
                    out
                }
                // cada "palavra" de 4 bytes vem em ordem invertida (little-endian)
                for (j in 0 until 4) bytes[i * 4 + j] = wBytes[3 - j]
            }
            InetAddress.getByAddress(bytes)
        } else {
            val raw = BigInteger(ipHex, 16).toByteArray()
            val out = ByteArray(4)
            val src = if (raw.size > 4) raw.copyOfRange(raw.size - 4, raw.size) else raw
            System.arraycopy(src, 0, out, 4 - src.size, src.size)
            // little-endian -> ordem normal
            InetAddress.getByAddress(byteArrayOf(out[3], out[2], out[1], out[0]))
        }
        return ip to portHex.toInt(16)
    }

    private suspend fun uidNames(): Map<Int, List<String>> {
        val map = mutableMapOf<Int, MutableList<String>>()
        manager.shell("pm list packages -U").output.lines().forEach { line ->
            val m = Regex("package:(\\S+)\\s+uid:(\\d+)").find(line) ?: return@forEach
            val uid = m.groupValues[2].toIntOrNull() ?: return@forEach
            map.getOrPut(uid) { mutableListOf() }.add(m.groupValues[1])
        }
        return map
    }

    private suspend fun auditNetwork(p: Map<String, String>): AuditSection {
        val findings = mutableListOf<AuditFinding>()
        val conns = mutableListOf<NetConn>()

        for ((table, v6) in listOf("tcp" to false, "tcp6" to true)) {
            val lines = manager.shell("cat /proc/net/$table").output.lines().drop(1)
            lines.forEach { line ->
                val f = line.trim().split(Regex("\\s+"))
                if (f.size < 8) return@forEach
                runCatching {
                    val (_, lport) = parseHexAddr(f[1], v6)
                    val (rip, rport) = parseHexAddr(f[2], v6)
                    conns += NetConn(f[0], lport, rip, rport, f[7].toInt())
                }
            }
        }

        if (conns.isEmpty()) {
            findings += AuditFinding(AuditLevel.WARN, "Nao foi possivel ler as tabelas de conexao (algumas versoes do Android bloqueiam isso)")
        } else {
            val names = uidNames()
            fun owner(uid: Int): String = when (uid) {
                0 -> "root"
                1000 -> "sistema"
                2000 -> "shell"
                else -> names[uid]?.let { it.first() + if (it.size > 1) " +${it.size - 1}" else "" } ?: "uid $uid"
            }

            val listening = conns.filter { it.state == "0A" && !it.remoteIp.isLoopbackAddress }
            val established = conns.filter { it.state == "01" && !it.remoteIp.isLoopbackAddress }
            findings += AuditFinding(AuditLevel.INFO, "${listening.size} porta(s) em escuta, ${established.size} conexao(oes) externa(s) ativa(s)")

            val listenLines = listening.take(15).map { c ->
                val tag = if (c.localPort in setOf(5555, 5037)) "<- ADB por WiFi esta aberto!" else ""
                "escutando na porta ${c.localPort} (${owner(c.uid)}) $tag".trim()
            }
            if (listening.any { it.localPort in setOf(5555, 5037) }) {
                findings += AuditFinding(AuditLevel.BAD, "Portas de ADB por WiFi encontradas em escuta", listenLines)
            } else if (listenLines.isNotEmpty()) {
                findings += AuditFinding(AuditLevel.INFO, "Portas em escuta", listenLines)
            }

            val estLines = established.take(25).map { c ->
                val tag = if (!c.remoteIp.isSiteLocalAddress && !c.remoteIp.isLoopbackAddress) "publica" else "local"
                "${c.remoteIp.hostAddress}:${c.remotePort} ($tag, ${owner(c.uid)})"
            }
            if (estLines.isNotEmpty()) {
                findings += AuditFinding(AuditLevel.INFO, "Conexoes externas ativas - procure apps que voce nao reconhece falando com a internet", estLines)
            }
        }

        val port = p["service.adb.tcp.port"] ?: p["persist.adb.tcp.port"]
        findings += if (port != null && port !in setOf("-1", "0")) {
            AuditFinding(AuditLevel.WARN, "O ADB pela rede esta ativo na porta $port - desative ao terminar (adb usb)")
        } else {
            AuditFinding(AuditLevel.OK, "O ADB pela rede nao esta ativo (somente USB)")
        }

        val dns = manager.shell("settings get global private_dns_mode").output.trim()
        findings += AuditFinding(AuditLevel.INFO, "DNS privado: ${dns.ifBlank { "desconhecido" }}")

        val proxy = manager.shell("settings get global http_proxy").output.trim()
        findings += if (proxy.isNotEmpty() && proxy != "null" && proxy != ":0") {
            AuditFinding(AuditLevel.WARN, "Ha um proxy HTTP global configurado: $proxy")
        } else {
            AuditFinding(AuditLevel.OK, "Nenhum proxy global configurado")
        }

        return AuditSection("Conexoes e configuracoes de rede", findings)
    }
}
