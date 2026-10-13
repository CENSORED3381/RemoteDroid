package com.remotedroid.app.ui.screens.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotedroid.app.core.adb.AdbRepository
import com.remotedroid.app.core.adb.AuditRepository
import com.remotedroid.app.core.model.AuditReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class AuditUiState(
    val running: Boolean = false,
    val report: AuditReport? = null,
    val statusMessage: String? = null
)

class AuditViewModel(
    private val auditRepository: AuditRepository = AuditRepository(),
    private val deviceRepository: AdbRepository = AdbRepository()
) : ViewModel() {

    private val _ui = MutableStateFlow(AuditUiState())
    val ui: StateFlow<AuditUiState> = _ui.asStateFlow()

    fun runAudit() {
        if (_ui.value.running) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(running = true, statusMessage = null)
            runCatching {
                val device = runCatching { deviceRepository.getDeviceInfo() }.getOrNull()
                val label = device?.let { "${it.manufacturer} ${it.model}" } ?: "aparelho alvo"
                auditRepository.runFullAudit(label)
            }.onSuccess { report ->
                _ui.value = _ui.value.copy(running = false, report = report)
            }.onFailure { e ->
                _ui.value = _ui.value.copy(running = false, statusMessage = "falha na auditoria: ${e.message}")
            }
        }
    }

    fun saveReport(destDir: File) {
        val report = _ui.value.report ?: return
        viewModelScope.launch {
            runCatching {
                val file = File(destDir, "remotedroid_auditoria_${System.currentTimeMillis()}.txt")
                file.writeText(report.toPlainText())
                file
            }.onSuccess {
                _ui.value = _ui.value.copy(statusMessage = "salvo em ${it.absolutePath}")
            }.onFailure {
                _ui.value = _ui.value.copy(statusMessage = "falha ao salvar: ${it.message}")
            }
        }
    }
}
