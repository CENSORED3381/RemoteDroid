package com.remotedroid.app.core.adb

import com.remotedroid.app.core.model.AppInfo
import com.remotedroid.app.core.model.DeviceInfo
import com.remotedroid.app.core.model.RemoteFile
import java.io.File

/**
 * Camada de mais alto nivel sobre o AdbConnectionManager: monta os comandos
 * shell (equivalentes aos usados no pegasus_pt.py) e faz o parsing da saida
 * para os modelos usados pela interface.
 */
class AdbRepository(private val manager: AdbConnectionManager = AdbConnectionManager) {

    // ---------------------------------------------------------------------
    // 3.2 / Tela "Info do aparelho"
    // ---------------------------------------------------------------------
    suspend fun getDeviceInfo(): DeviceInfo {
        val props = manager.shell(
            "getprop ro.product.manufacturer; " +
                "getprop ro.product.model; " +
                "getprop ro.build.version.release; " +
                "getprop ro.build.version.sdk; " +
                "getprop ro.build.fingerprint"
        ).output.lines().map { it.trim() }

        val battery = manager.shell("dumpsys battery").output
        val batteryLevel = Regex("level: (\\d+)").find(battery)?.groupValues?.get(1)?.toIntOrNull()
        val batteryStatus = Regex("status: (\\d+)").find(battery)?.groupValues?.get(1)?.let(::batteryStatusLabel)

        val df = manager.shell("df /data").output
        val (totalStorage, freeStorage) = parseDf(df)

        val meminfo = manager.shell("cat /proc/meminfo").output
        val totalMem = Regex("MemTotal:\\s+(\\d+) kB").find(meminfo)?.groupValues?.get(1)?.toLongOrNull()?.div(1024)
        val freeMem = Regex("MemAvailable:\\s+(\\d+) kB").find(meminfo)?.groupValues?.get(1)?.toLongOrNull()?.div(1024)

        return DeviceInfo(
            manufacturer = props.getOrNull(0)?.ifBlank { "-" } ?: "-",
            model = props.getOrNull(1)?.ifBlank { "-" } ?: "-",
            androidVersion = props.getOrNull(2)?.ifBlank { "-" } ?: "-",
            sdkInt = props.getOrNull(3)?.ifBlank { "-" } ?: "-",
            buildFingerprint = props.getOrNull(4)?.ifBlank { "-" } ?: "-",
            batteryLevel = batteryLevel,
            batteryStatus = batteryStatus ?: "-",
            totalStorageMb = totalStorage,
            freeStorageMb = freeStorage,
            totalMemoryMb = totalMem,
            freeMemoryMb = freeMem
        )
    }

    private fun batteryStatusLabel(code: String): String = when (code) {
        "1" -> "desconhecido"
        "2" -> "carregando"
        "3" -> "descarregando"
        "4" -> "nao carregando"
        "5" -> "cheia"
        else -> "desconhecido"
    }

    private fun parseDf(output: String): Pair<Long?, Long?> {
        // formato tipico: Filesystem  1K-blocks  Used  Available  Use%  Mounted on
        val dataLine = output.lines().getOrNull(1)?.trim()?.split(Regex("\\s+")) ?: return null to null
        if (dataLine.size < 4) return null to null
        val totalKb = dataLine[1].toLongOrNull()
        val availKb = dataLine[3].toLongOrNull()
        return (totalKb?.div(1024)) to (availKb?.div(1024))
    }

    // ---------------------------------------------------------------------
    // 3.2 / Tela "Apps"
    // ---------------------------------------------------------------------
    suspend fun listApps(includeSystem: Boolean): List<AppInfo> {
        val flag = if (includeSystem) "" else "-3 "
        val output = manager.shell("pm list packages $flag-f").output
        return output.lines()
            .filter { it.startsWith("package:") }
            .mapNotNull { line ->
                val raw = line.removePrefix("package:")
                val idx = raw.lastIndexOf('=')
                if (idx == -1) return@mapNotNull AppInfo(packageName = raw.trim())
                val path = raw.substring(0, idx)
                val pkg = raw.substring(idx + 1)
                AppInfo(
                    packageName = pkg.trim(),
                    apkPath = path.trim(),
                    isSystemApp = path.contains("/system/")
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.packageName }
    }

    suspend fun launchApp(packageName: String): ShellResult =
        manager.shell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")

    suspend fun uninstallApp(packageName: String): ShellResult =
        manager.shell("pm uninstall $packageName")

    suspend fun appPermissions(packageName: String): List<String> {
        val output = manager.shell("dumpsys package $packageName").output
        return Regex("android\\.permission\\.[A-Z_]+")
            .findAll(output)
            .map { it.value }
            .distinct()
            .sorted()
            .toList()
    }

    suspend fun installApk(localApk: File): ShellResult {
        val client = manager.requireClient()
        client.install(localApk)
        return ShellResult(0, "instalado: ${localApk.name}")
    }

    // ---------------------------------------------------------------------
    // 3.2 / Tela "Arquivos"
    // ---------------------------------------------------------------------
    suspend fun listFiles(path: String): List<RemoteFile> {
        val output = manager.shell("ls -la \"$path\" 2>/dev/null").output
        val entries = mutableListOf<RemoteFile>()
        output.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("total")) return@forEach
            val parts = trimmed.split(Regex("\\s+"), limit = 9)
            if (parts.size < 9) return@forEach
            val perms = parts[0]
            val name = parts[8]
            if (name == "." || name == "..") return@forEach
            val size = parts[4].toLongOrNull()
            val isDir = perms.startsWith("d")
            val fullPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
            entries.add(RemoteFile(name, fullPath, isDir, size, perms))
        }
        return entries.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun pullFile(remotePath: String, localDest: File) {
        manager.requireClient().pull(localDest, remotePath)
    }

    suspend fun pushFile(localSrc: File, remotePath: String) {
        manager.requireClient().push(localSrc, remotePath)
    }

    // ---------------------------------------------------------------------
    // 3.2 / Tela "Logs"
    // ---------------------------------------------------------------------
    suspend fun dumpLogcat(maxLines: Int = 500): String =
        manager.shell("logcat -d -v time -t $maxLines").output

    suspend fun clearLogcat(): ShellResult = manager.shell("logcat -c")

    suspend fun bugreportSummary(): String =
        manager.shell("dumpsys battery; echo ---; getprop; echo ---; dumpsys meminfo | head -n 40").output
}
