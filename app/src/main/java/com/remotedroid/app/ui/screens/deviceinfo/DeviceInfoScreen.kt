package com.remotedroid.app.ui.screens.deviceinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remotedroid.app.core.model.DeviceInfo
import com.remotedroid.app.ui.components.TerminalButton
import com.remotedroid.app.ui.components.TerminalWindow
import com.remotedroid.app.ui.theme.AlertRed
import com.remotedroid.app.ui.theme.MatrixGreen
import com.remotedroid.app.ui.theme.TextSecondary

@Composable
fun DeviceInfoScreen(viewModel: DeviceInfoViewModel) {
    val ui by viewModel.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("INFO DO APARELHO", style = MaterialTheme.typography.headlineMedium, color = MatrixGreen)

        TerminalWindow(title = "getprop / dumpsys battery / df / meminfo") {
            when {
                ui.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = MatrixGreen, modifier = Modifier.padding(end = 8.dp))
                    Text("consultando aparelho alvo...", color = TextSecondary)
                }
                ui.error != null -> Text("[-] ${ui.error}", color = AlertRed)
                ui.info != null -> DeviceInfoRows(ui.info!!)
            }
        }

        TerminalButton(text = "Atualizar", onClick = viewModel::refresh, loading = ui.loading)
    }
}

@Composable
private fun DeviceInfoRows(info: DeviceInfo) {
    val rows = listOf(
        "fabricante" to info.manufacturer,
        "modelo" to info.model,
        "android" to info.androidVersion,
        "sdk" to info.sdkInt,
        "bateria" to (info.batteryLevel?.let { "$it% (${info.batteryStatus})" } ?: "-"),
        "armazenamento livre" to (info.freeStorageMb?.let { "$it MB / ${info.totalStorageMb} MB" } ?: "-"),
        "memoria livre" to (info.freeMemoryMb?.let { "$it MB / ${info.totalMemoryMb} MB" } ?: "-"),
        "fingerprint" to info.buildFingerprint
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(value, style = MaterialTheme.typography.bodySmall, color = MatrixGreen)
            }
        }
    }
}
