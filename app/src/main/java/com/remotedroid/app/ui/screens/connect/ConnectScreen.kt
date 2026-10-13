package com.remotedroid.app.ui.screens.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remotedroid.app.core.model.ConnectionHistoryItem
import com.remotedroid.app.ui.components.ConsoleLog
import com.remotedroid.app.ui.components.TerminalButton
import com.remotedroid.app.ui.components.TerminalTextField
import com.remotedroid.app.ui.components.TerminalWindow
import com.remotedroid.app.ui.theme.MatrixGreen
import com.remotedroid.app.ui.theme.TextMuted
import com.remotedroid.app.ui.theme.TextSecondary

@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel,
    onConnected: () -> Unit
) {
    val ui by viewModel.ui.collectAsState()
    val history by viewModel.history.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "REMOTEDROID",
                style = MaterialTheme.typography.headlineLarge,
                color = MatrixGreen,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "gerenciador & auditor adb remoto // v0.2",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        TerminalWindow(title = "1. parear aparelho (uma vez so)") {
            Text(
                "No aparelho alvo: Opcoes do desenvolvedor > Depuracao sem fio > Parear com codigo.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            TerminalTextField(
                value = ui.pairHost,
                onValueChange = { viewModel.onPairFieldsChange(host = it) },
                label = "IP de pareamento",
                placeholder = "192.168.0.10"
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalTextField(
                    value = ui.pairPort,
                    onValueChange = { viewModel.onPairFieldsChange(port = it) },
                    label = "Porta",
                    placeholder = "37849",
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
                TerminalTextField(
                    value = ui.pairCode,
                    onValueChange = { viewModel.onPairFieldsChange(code = it) },
                    label = "Codigo",
                    placeholder = "123456",
                    numeric = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            TerminalButton(text = "Parear", onClick = viewModel::pair, loading = ui.busy)
        }

        TerminalWindow(title = "2. conectar") {
            Text(
                "Use o IP:PORTA mostrado na tela principal de Depuracao sem fio.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            TerminalTextField(
                value = ui.connectHost,
                onValueChange = { viewModel.onConnectFieldsChange(host = it) },
                label = "IP do aparelho",
                placeholder = "192.168.0.10"
            )
            Spacer(Modifier.height(8.dp))
            TerminalTextField(
                value = ui.connectPort,
                onValueChange = { viewModel.onConnectFieldsChange(port = it) },
                label = "Porta",
                placeholder = "40123",
                numeric = true
            )
            Spacer(Modifier.height(10.dp))
            TerminalButton(text = "Conectar", onClick = { viewModel.connect(onConnected) }, loading = ui.busy)
        }

        if (history.isNotEmpty()) {
            TerminalWindow(title = "historico de conexoes") {
                LazyColumn(
                    modifier = Modifier.height((history.size.coerceAtMost(4) * 52).dp)
                ) {
                    items(history, key = { it.host + it.port }) { item ->
                        HistoryRow(
                            item = item,
                            onClick = { viewModel.connectFromHistory(item, onConnected) },
                            onForget = { viewModel.forgetHistoryItem(item) }
                        )
                    }
                }
            }
        }

        TerminalWindow(title = "console") {
            ConsoleLog(lines = ui.logLines)
        }
    }
}

@Composable
private fun HistoryRow(
    item: ConnectionHistoryItem,
    onClick: () -> Unit,
    onForget: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.History, contentDescription = null, tint = TextMuted, modifier = Modifier.width(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.bodyMedium, color = MatrixGreen)
            }
        }
        TerminalButton(text = "usar", onClick = onClick, modifier = Modifier.width(90.dp).height(36.dp))
        IconButton(onClick = onForget) {
            Icon(Icons.Filled.Close, contentDescription = "esquecer", tint = TextMuted)
        }
    }
}
