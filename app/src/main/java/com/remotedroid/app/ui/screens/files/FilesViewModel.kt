package com.remotedroid.app.ui.screens.files

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotedroid.app.core.adb.AdbRepository
import com.remotedroid.app.core.model.RemoteFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class FilesUiState(
    val currentPath: String = "/sdcard",
    val loading: Boolean = true,
    val entries: List<RemoteFile> = emptyList(),
    val error: String? = null,
    val statusMessage: String? = null
)

class FilesViewModel(
    private val repository: AdbRepository = AdbRepository()
) : ViewModel() {

    private val _ui = MutableStateFlow(FilesUiState())
    val ui: StateFlow<FilesUiState> = _ui.asStateFlow()

    init {
        load(_ui.value.currentPath)
    }

    fun load(path: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null, currentPath = path)
            runCatching { repository.listFiles(path) }
                .onSuccess { _ui.value = _ui.value.copy(loading = false, entries = it) }
                .onFailure { _ui.value = _ui.value.copy(loading = false, error = it.message) }
        }
    }

    fun openEntry(entry: RemoteFile) {
        if (entry.isDirectory) load(entry.fullPath)
    }

    fun goUp() {
        val current = _ui.value.currentPath.trimEnd('/')
        val parent = current.substringBeforeLast('/', missingDelimiterValue = "/")
        load(if (parent.isBlank()) "/" else parent)
    }

    fun pull(entry: RemoteFile, localDir: File) {
        viewModelScope.launch {
            runCatching {
                val dest = File(localDir, entry.name)
                repository.pullFile(entry.fullPath, dest)
                dest
            }.onSuccess {
                _ui.value = _ui.value.copy(statusMessage = "baixado para ${it.absolutePath}")
            }.onFailure {
                _ui.value = _ui.value.copy(statusMessage = "falha ao baixar: ${it.message}")
            }
        }
    }

    fun push(localFile: File) {
        viewModelScope.launch {
            val remotePath = _ui.value.currentPath.trimEnd('/') + "/" + localFile.name
            runCatching { repository.pushFile(localFile, remotePath) }
                .onSuccess {
                    _ui.value = _ui.value.copy(statusMessage = "enviado: $remotePath")
                    load(_ui.value.currentPath)
                }
                .onFailure { _ui.value = _ui.value.copy(statusMessage = "falha ao enviar: ${it.message}") }
        }
    }
}
