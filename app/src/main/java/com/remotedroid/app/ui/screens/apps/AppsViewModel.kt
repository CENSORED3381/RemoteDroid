package com.remotedroid.app.ui.screens.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotedroid.app.core.adb.AdbRepository
import com.remotedroid.app.core.model.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppsUiState(
    val loading: Boolean = true,
    val includeSystem: Boolean = false,
    val query: String = "",
    val apps: List<AppInfo> = emptyList(),
    val error: String? = null,
    val statusMessage: String? = null
)

class AppsViewModel(
    private val repository: AdbRepository = AdbRepository()
) : ViewModel() {

    private val _ui = MutableStateFlow(AppsUiState())
    val ui: StateFlow<AppsUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            runCatching { repository.listApps(_ui.value.includeSystem) }
                .onSuccess { _ui.value = _ui.value.copy(loading = false, apps = it) }
                .onFailure { _ui.value = _ui.value.copy(loading = false, error = it.message) }
        }
    }

    fun toggleSystemApps() {
        _ui.value = _ui.value.copy(includeSystem = !_ui.value.includeSystem)
        refresh()
    }

    fun onQueryChange(query: String) {
        _ui.value = _ui.value.copy(query = query)
    }

    fun filteredApps(): List<AppInfo> {
        val q = _ui.value.query.trim()
        if (q.isBlank()) return _ui.value.apps
        return _ui.value.apps.filter { it.packageName.contains(q, ignoreCase = true) }
    }

    fun launch(app: AppInfo) {
        viewModelScope.launch {
            val result = repository.launchApp(app.packageName)
            _ui.value = _ui.value.copy(
                statusMessage = if (result.exitCode == 0) "abrindo ${app.packageName}" else "falha ao abrir: ${result.output}"
            )
        }
    }

    fun uninstall(app: AppInfo) {
        viewModelScope.launch {
            val result = repository.uninstallApp(app.packageName)
            _ui.value = _ui.value.copy(statusMessage = result.output.trim())
            refresh()
        }
    }
}
