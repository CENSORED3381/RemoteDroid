package com.remotedroid.app.core.model

/** Um host:porta previamente conectado, guardado para reconexao rapida. */
data class ConnectionHistoryItem(
    val host: String,
    val port: Int,
    val label: String,
    val lastConnectedAt: Long
) {
    fun encode(): String = listOf(host, port.toString(), label, lastConnectedAt.toString())
        .joinToString("\u0001")

    companion object {
        fun decode(raw: String): ConnectionHistoryItem? {
            val parts = raw.split("\u0001")
            if (parts.size != 4) return null
            val port = parts[1].toIntOrNull() ?: return null
            val ts = parts[3].toLongOrNull() ?: 0L
            return ConnectionHistoryItem(parts[0], port, parts[2], ts)
        }
    }
}
