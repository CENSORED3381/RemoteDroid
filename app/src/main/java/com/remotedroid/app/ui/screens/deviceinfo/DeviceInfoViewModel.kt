package com.remotedroid.app.ui.screens.deviceinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.remotedroid.app.core.adb.AdbRepository
import com.remotedroid.app.core.model.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceInfoUiState(
    val loading: Boolean = true,
    val info: DeviceInfo? = null,
    val error: String? = null
)

class DeviceInfoViewModel(
    private val repository: AdbRepository = AdbRepository()
) : ViewModel() {

    private val _ui = MutableStateFlow(DeviceInfoUiState())
    val ui: StateFlow<DeviceInfoUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            runCatching { repository.getDeviceInfo() }
                .onSuccess { _ui.value = DeviceInfoUiState(loading = false, info = it) }
                .onFailure { _ui.value = DeviceInfoUiState(loading = false, error = it.message ?: "erro desconhecido") }
        }
    }
}
