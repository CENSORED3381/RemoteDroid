package com.remotedroid.app.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotedroid.app.core.adb.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class LogsUiState(
    val loading: Boolean = false,
    val logText: String = "",
    val statusMessage: String? = null
)

class LogsViewModel(
    private val repository: AdbRepository = AdbRepository()
) : ViewModel() {

    private val _ui = MutableStateFlow(LogsUiState())
    val ui: StateFlow<LogsUiState> = _ui.asStateFlow()

    fun capture() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            runCatching { repository.dumpLogcat() }
                .onSuccess { _ui.value = _ui.value.copy(loading = false, logText = it) }
                .onFailure { _ui.value = _ui.value.copy(loading = false, statusMessage = "falha: ${it.message}") }
        }
    }

    fun clearBuffer() {
        viewModelScope.launch {
            val result = repository.clearLogcat()
            _ui.value = _ui.value.copy(statusMessage = if (result.exitCode == 0) "buffer limpo" else "falha ao limpar buffer")
        }
    }

    fun saveReport(destDir: File) {
        viewModelScope.launch {
            runCatching {
                val file = File(destDir, "remotedroid_logcat_${System.currentTimeMillis()}.txt")
                file.writeText(_ui.value.logText)
                file
            }.onSuccess {
                _ui.value = _ui.value.copy(statusMessage = "salvo em ${it.absolutePath}")
            }.onFailure {
                _ui.value = _ui.value.copy(statusMessage = "falha ao salvar: ${it.message}")
            }
        }
    }
}
