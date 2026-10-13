package com.remotedroid.app.core.model

/** Qualidade do espelhamento: controla resolucao maxima, fps e bitrate pedidos ao scrcpy-server. */
enum class MirrorQuality(
    val label: String,
    val maxSize: Int,
    val maxFps: Int,
    val bitRateMbps: Int
) {
    BAIXA(label = "Baixa", maxSize = 600, maxFps = 24, bitRateMbps = 2),
    MEDIA(label = "Media", maxSize = 960, maxFps = 30, bitRateMbps = 8),
    ALTA(label = "Alta", maxSize = 1280, maxFps = 30, bitRateMbps = 16)
}

/** Estado atual do espelhamento de tela ao vivo. */
sealed class MirrorState {
    data object Idle : MirrorState()
    data object PushingServidor : MirrorState()
    data object AguardandoSocket : MirrorState()
    data class Streaming(
        val deviceName: String,
        val width: Int,
        val height: Int,
        val fps: Int = 0
    ) : MirrorState()
    data class Error(val message: String) : MirrorState()
}
