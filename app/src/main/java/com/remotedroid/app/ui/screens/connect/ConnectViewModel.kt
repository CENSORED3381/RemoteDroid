package com.remotedroid.app.ui.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotedroid.app.core.adb.AdbConnectionManager
import com.remotedroid.app.core.adb.ConnectionState
import com.remotedroid.app.core.model.ConnectionHistoryItem
import com.remotedroid.app.core.storage.ConnectionHistoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConnectUiState(
    val pairHost: String = "",
    val pairPort: String = "",
    val pairCode: String = "",
    val connectHost: String = "",
    val connectPort: String = "",
    val logLines: List<String> = emptyList(),
    val busy: Boolean = false
)

class ConnectViewModel(
    private val historyStore: ConnectionHistoryStore
) : ViewModel() {

    private val _ui = MutableStateFlow(ConnectUiState())
    val ui: StateFlow<ConnectUiState> = _ui.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = AdbConnectionManager.state

    val history: StateFlow<List<ConnectionHistoryItem>> = historyStore.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onPairFieldsChange(host: String? = null, port: String? = null, code: String? = null) {
        _ui.value = _ui.value.copy(
            pairHost = host ?: _ui.value.pairHost,
            pairPort = port ?: _ui.value.pairPort,
            pairCode = code ?: _ui.value.pairCode
        )
    }

    fun onConnectFieldsChange(host: String? = null, port: String? = null) {
        _ui.value = _ui.value.copy(
            connectHost = host ?: _ui.value.connectHost,
            connectPort = port ?: _ui.value.connectPort
        )
    }

    private fun appendLog(line: String) {
        _ui.value = _ui.value.copy(logLines = _ui.value.logLines + line)
    }

    fun pair() {
        val host = _ui.value.pairHost.trim()
        val port = _ui.value.pairPort.trim().toIntOrNull()
        val code = _ui.value.pairCode.trim()
        if (host.isBlank() || port == null || code.isBlank()) {
            appendLog("[-] preencha IP, porta e codigo de pareamento")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            appendLog("[*] pareando com $host:$port ...")
            AdbConnectionManager.pair(host, port, code)
                .onSuccess { appendLog("[+] pareamento concluido. agora conecte pelo IP:PORTA da tela principal.") }
                .onFailure { appendLog("[-] falha no pareamento: ${it.message}") }
            _ui.value = _ui.value.copy(busy = false)
        }
    }

    fun connect(onConnected: () -> Unit) {
        val host = _ui.value.connectHost.trim()
        val port = _ui.value.connectPort.trim().toIntOrNull()
        if (host.isBlank() || port == null) {
            appendLog("[-] preencha IP e porta de conexao")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            appendLog("[*] conectando a $host:$port ...")
            AdbConnectionManager.connect(host, port)
                .onSuccess {
                    appendLog("[+] conectado com sucesso.")
                    historyStore.upsert(
                        ConnectionHistoryItem(
                            host = host,
                            port = port,
                            label = "$host:$port",
                            lastConnectedAt = System.currentTimeMillis()
                        )
                    )
                    onConnected()
                }
                .onFailure { appendLog("[-] falha ao conectar: ${it.message}") }
            _ui.value = _ui.value.copy(busy = false)
        }
    }

    fun connectFromHistory(item: ConnectionHistoryItem, onConnected: () -> Unit) {
        onConnectFieldsChange(host = item.host, port = item.port.toString())
        connect(onConnected)
    }

    fun forgetHistoryItem(item: ConnectionHistoryItem) {
        viewModelScope.launch { historyStore.remove(item) }
    }
}
