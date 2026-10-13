package com.remotedroid.app.core.adb

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import com.flyfishxu.kadb.stream.AdbStream
import com.remotedroid.app.core.model.MirrorQuality
import com.remotedroid.app.core.model.MirrorState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.BufferedSource
import okio.buffer
import java.io.File
import java.io.IOException
import kotlin.random.Random

/**
 * Espelhamento de tela ao vivo (Fase 4).
 *
 * Reaproveita o servidor do projeto scrcpy (Genymobile/scrcpy, Apache 2.0): o app
 * empurra o `scrcpy-server.jar` (baixado em tempo de build, ver tarefa Gradle
 * `fetchScrcpyServer`) para o aparelho alvo e o executa com `app_process`, igual
 * ao que o scrcpy faz no computador. A diferenca e que aqui quem fala o protocolo
 * ADB e decodifica o video (via MediaCodec) e o proprio app Android, sem precisar
 * do cliente scrcpy nem do binario `adb`.
 *
 * Fluxo:
 *  1. Envia o `scrcpy-server.jar` para `/data/local/tmp/scrcpy-server.jar`.
 *  2. Roda o servidor via `app_process` num stream de shell que fica aberto
 *     durante toda a sessao (fechar esse stream + `cleanup=true` derruba o
 *     servidor do lado do aparelho).
 *  3. Abre um stream ADB bruto para `localabstract:scrcpy_<scid>` — o mesmo
 *     mecanismo que `adb forward` usa por baixo dos panos, so que direto,
 *     sem precisar reservar uma porta TCP local.
 *  4. Le o cabecalho do protocolo (nome do aparelho + codec + resolucao) e
 *     depois o loop de pacotes (PTS/flags + tamanho + dados H.264), entregando
 *     cada pacote ao MediaCodec, que renderiza direto na Surface da tela.
 *
 * Limitacoes conhecidas (fase 4 / MVP): somente visualizacao (sem toques/
 * gestos - fica para uma fase futura, como ja previsto no plano), sem audio,
 * e uma rotacao de tela no aparelho alvo exige reconectar o espelhamento.
 */
class MirrorStreamer(
    private val context: Context,
    private val manager: AdbConnectionManager = AdbConnectionManager
) {
    private val streamerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var decodeJob: Job? = null

    private var shellStream: AdbStream? = null
    private var videoStream: AdbStream? = null
    private var decoder: MediaCodec? = null

    private val _state = MutableStateFlow<MirrorState>(MirrorState.Idle)
    val state: StateFlow<MirrorState> = _state.asStateFlow()

    /** Inicia o espelhamento, renderizando na Surface informada. Nao bloqueia ate o fim da sessao. */
    suspend fun start(surface: Surface, quality: MirrorQuality) {
        if (decodeJob?.isActive == true) return
        if (!manager.isConnected) {
            _state.value = MirrorState.Error("nenhum aparelho conectado")
            return
        }

        withContext(Dispatchers.IO) {
            runCatching {
                _state.value = MirrorState.PushingServidor
                val client = manager.requireClient()
                val serverJar = extractServerJarToCache()
                client.push(serverJar, DEVICE_SERVER_PATH)

                val scid = randomScid()
                val launchCmd = buildLaunchCommand(scid, quality)
                val shell = client.open("shell:$launchCmd")
                shellStream = shell
                logShellOutputInBackground(shell)

                _state.value = MirrorState.AguardandoSocket
                val video = openVideoSocketWithRetry(scid)
                videoStream = video

                val source = video.source.buffer()
                val header = readStreamHeader(source)

                val format = MediaFormat.createVideoFormat(MIME_H264, header.width, header.height)
                val codec = MediaCodec.createDecoderByType(MIME_H264)
                codec.configure(format, surface, null, 0)
                codec.start()
                decoder = codec

                decodeJob = streamerScope.launch {
                    runCatching { decodeLoop(source, codec, header.deviceName, header.width, header.height) }
                        .onFailure { e ->
                            if (isActive) {
                                Log.w(TAG, "espelhamento encerrado: ${e.message}")
                                _state.value = MirrorState.Error(e.message ?: "conexao de video perdida")
                            }
                        }
                }
            }.onFailure { e ->
                Log.e(TAG, "falha ao iniciar espelhamento", e)
                _state.value = MirrorState.Error(e.message ?: "falha ao iniciar espelhamento")
                stop()
            }
        }
    }

    /** Encerra a sessao atual (se houver) e libera os recursos. Seguro de chamar varias vezes. */
    fun stop() {
        decodeJob?.cancel()
        decodeJob = null
        runCatching { videoStream?.close() }
        runCatching { shellStream?.close() }
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        decoder = null
        videoStream = null
        shellStream = null
        _state.value = MirrorState.Idle
    }

    // -------------------------------------------------------------------
    // Protocolo scrcpy: cabecalho + loop de pacotes
    // -------------------------------------------------------------------

    private data class StreamHeader(val deviceName: String, val width: Int, val height: Int)

    /** 64 bytes nome do aparelho + 4 bytes codec id + 4 bytes largura + 4 bytes altura. */
    private fun readStreamHeader(source: BufferedSource): StreamHeader {
        val nameBytes = source.readByteArray(DEVICE_NAME_FIELD_LENGTH.toLong())
        val deviceName = nameBytes.toString(Charsets.UTF_8).trimEnd('\u0000').ifBlank { "aparelho alvo" }
        source.readInt() // codec id - forcamos h264 na linha de comando, entao ignoramos aqui
        val width = source.readInt()
        val height = source.readInt()
        return StreamHeader(deviceName, width, height)
    }

    private suspend fun decodeLoop(
        source: BufferedSource,
        codec: MediaCodec,
        deviceName: String,
        width: Int,
        height: Int
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var framesInWindow = 0
        var windowStartMs = System.currentTimeMillis()

        while (kotlin.coroutines.coroutineContext.isActive) {
            // 8 bytes PTS+flags (big-endian) + 4 bytes tamanho do pacote (big-endian)
            val ptsAndFlags = source.readLong()
            val size = source.readInt()
            if (size < 0 || size > MAX_PACKET_SIZE) {
                throw IOException("pacote de video com tamanho invalido: $size")
            }
            val packet = source.readByteArray(size.toLong())

            val isConfig = (ptsAndFlags and FLAG_CONFIG) != 0L
            val pts = if (isConfig) 0L else ptsAndFlags and PTS_MASK

            val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex)
                inputBuffer?.clear()
                inputBuffer?.put(packet)
                val flags = if (isConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
                codec.queueInputBuffer(inputIndex, 0, packet.size, pts, flags)
            }

            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            while (outputIndex >= 0) {
                codec.releaseOutputBuffer(outputIndex, true)
                if (!isConfig) framesInWindow++
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }

            val now = System.currentTimeMillis()
            if (now - windowStartMs >= 1000) {
                val fps = framesInWindow
                framesInWindow = 0
                windowStartMs = now
                _state.value = MirrorState.Streaming(deviceName, width, height, fps)
            }
        }
    }

    // -------------------------------------------------------------------
    // Auxiliares de conexao
    // -------------------------------------------------------------------

    private suspend fun openVideoSocketWithRetry(scid: String): AdbStream {
        val client = manager.requireClient()
        var lastError: Throwable? = null
        repeat(VIDEO_SOCKET_RETRIES) { attempt ->
            val result = runCatching { client.open("localabstract:scrcpy_$scid") }
            result.getOrNull()?.let { return it }
            lastError = result.exceptionOrNull()
            delay(150L * (attempt + 1))
        }
        throw IOException(
            "nao foi possivel conectar ao socket de video do scrcpy-server" +
                (lastError?.message?.let { " ($it)" } ?: "")
        )
    }

    private fun logShellOutputInBackground(shell: AdbStream) {
        streamerScope.launch {
            runCatching {
                val source = shell.source.buffer()
                while (isActive) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isNotBlank()) Log.d(TAG, "scrcpy-server: $line")
                }
            }
        }
    }

    private fun extractServerJarToCache(): File {
        val out = File(context.cacheDir, "scrcpy-server.jar")
        if (!out.exists() || out.length() == 0L) {
            context.assets.open(SERVER_ASSET_NAME).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return out
    }

    private fun buildLaunchCommand(scid: String, quality: MirrorQuality): String = listOf(
        "CLASSPATH=$DEVICE_SERVER_PATH",
        "app_process", "/", "com.genymobile.scrcpy.Server", SCRCPY_SERVER_VERSION,
        "scid=$scid",
        "log_level=info",
        "audio=false",
        "control=false",
        "cleanup=true",
        "tunnel_forward=true",
        "send_device_meta=true",
        "send_frame_meta=true",
        "send_dummy_byte=false",
        "stay_awake=true",
        "video_codec=h264",
        "max_size=${quality.maxSize}",
        "max_fps=${quality.maxFps}",
        "video_bit_rate=${quality.bitRateMbps * 1_000_000}",
        "downsize_on_error=true"
    ).joinToString(" ")

    private fun randomScid(): String {
        val chars = "0123456789abcdef"
        return (1..8).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    companion object {
        private const val TAG = "MirrorStreamer"
        private const val MIME_H264 = "video/avc"
        private const val DEVICE_SERVER_PATH = "/data/local/tmp/scrcpy-server.jar"
        private const val SERVER_ASSET_NAME = "scrcpy-server.jar"

        /** Precisa bater com a versao baixada pela tarefa Gradle `fetchScrcpyServer`. */
        const val SCRCPY_SERVER_VERSION = "2.4"

        private const val DEVICE_NAME_FIELD_LENGTH = 64
        private const val VIDEO_SOCKET_RETRIES = 10
        private const val INPUT_TIMEOUT_US = 10_000L
        private const val MAX_PACKET_SIZE = 32 * 1024 * 1024

        // No word de PTS: bit 63 = pacote de configuracao (SPS/PPS), bit 62 = keyframe.
        private const val FLAG_CONFIG = 1L shl 63
        private const val FLAG_KEY_FRAME = 1L shl 62
        private const val PTS_MASK = FLAG_KEY_FRAME - 1
    }
}
