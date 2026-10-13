package com.remotedroid.app.ui.screens.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.remotedroid.app.ui.components.TerminalButton
import com.remotedroid.app.ui.components.TerminalWindow
import com.remotedroid.app.ui.theme.MatrixGreen
import com.remotedroid.app.ui.theme.TextSecondary

@Composable
fun LogsScreen(viewModel: LogsViewModel) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("LOGS", style = MaterialTheme.typography.headlineMedium, color = MatrixGreen)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TerminalButton(text = "Capturar", onClick = viewModel::capture, loading = ui.loading, modifier = Modifier.weight(1f))
            TerminalButton(
                text = "Salvar",
                onClick = { viewModel.saveReport(context.getExternalFilesDir(null) ?: context.filesDir) },
                modifier = Modifier.weight(1f)
            )
            TerminalButton(text = "Limpar", onClick = viewModel::clearBuffer, danger = true, modifier = Modifier.weight(1f))
        }

        ui.statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }

        TerminalWindow(title = "logcat -d -v time") {
            Text(
                text = ui.logText.ifBlank { "// toque em capturar para ler o buffer de log" },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MatrixGreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}
