package com.remotedroid.app.core.adb

import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Estado atual da conexao com o aparelho alvo (C). */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Pairing : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val host: String, val port: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * Ponto unico de acesso a conexao ADB ativa com o aparelho alvo.
 *
 * Reaproveita o mesmo fluxo do script pegasus_pt.py: pareamento por codigo
 * (Depuracao sem fio > Parear com codigo) seguido de conexao por IP:PORTA.
 * Toda a logica de protocolo ADB roda dentro do proprio app via Kadb,
 * sem depender do binario `adb` nem de um servidor ADB externo.
 */
object AdbConnectionManager {

    private var activeClient: Kadb? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    val isConnected: Boolean
        get() = activeClient != null

    /** Pareia com o aparelho alvo usando o codigo de 6 digitos exibido na Depuracao sem fio. */
    suspend fun pair(host: String, port: Int, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        _state.value = ConnectionState.Pairing
        runCatching {
            Kadb.pair(host, port, code)
            _state.value = ConnectionState.Disconnected
        }.onFailure { e ->
            _state.value = ConnectionState.Error(e.message ?: "falha no pareamento")
        }
    }

    /** Conecta ao aparelho alvo ja pareado, usando o IP:PORTA da tela de Depuracao sem fio. */
    suspend fun connect(host: String, port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        _state.value = ConnectionState.Connecting
        runCatching {
            disconnectInternal()
            val client = Kadb.create(host, port)
            // valida a conexao com um comando trivial antes de confirmar o estado
            client.shell("echo remotedroid_ok")
            activeClient = client
            _state.value = ConnectionState.Connected(host, port)
        }.onFailure { e ->
            activeClient = null
            _state.value = ConnectionState.Error(e.message ?: "falha ao conectar")
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        disconnectInternal()
        _state.value = ConnectionState.Disconnected
    }

    private fun disconnectInternal() {
        runCatching { activeClient?.close() }
        activeClient = null
    }

    /** Executa um comando de shell no aparelho alvo. Lanca excecao se nao houver conexao ativa. */
    suspend fun shell(command: String): ShellResult = withContext(Dispatchers.IO) {
        val client = activeClient ?: throw IllegalStateException("nenhum aparelho conectado")
        val response = client.shell(command)
        ShellResult(exitCode = response.exitCode, output = response.output)
    }

    /** Acesso ao cliente bruto para operacoes de push/pull/install que exigem a API completa do Kadb. */
    fun requireClient(): Kadb = activeClient ?: throw IllegalStateException("nenhum aparelho conectado")
}

/** Resultado simplificado de um comando de shell (codigo de saida + stdout/stderr combinados). */
data class ShellResult(val exitCode: Int, val output: String)
