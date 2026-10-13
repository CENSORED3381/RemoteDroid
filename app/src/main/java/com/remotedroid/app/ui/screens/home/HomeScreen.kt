package com.remotedroid.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LockPerson
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.remotedroid.app.core.adb.AdbConnectionManager
import com.remotedroid.app.core.adb.ConnectionState
import com.remotedroid.app.ui.components.BlinkingDot
import com.remotedroid.app.ui.components.TerminalButton
import com.remotedroid.app.ui.theme.AlertRed
import com.remotedroid.app.ui.theme.MatrixGreen
import com.remotedroid.app.ui.theme.TerminalBorder
import com.remotedroid.app.ui.theme.TerminalSurface
import com.remotedroid.app.ui.theme.TextMuted
import com.remotedroid.app.ui.theme.TextSecondary

private data class DashboardItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val connectionState by AdbConnectionManager.state.collectAsState()
    val connected = connectionState as? ConnectionState.Connected

    val items = listOf(
        DashboardItem("Info do aparelho", "modelo, android, bateria", Icons.Filled.Info, true) { onNavigate("device_info") },
        DashboardItem("Apps", "listar, instalar, remover", Icons.Filled.List, true) { onNavigate("apps") },
        DashboardItem("Arquivos", "explorar, enviar, baixar", Icons.Filled.Folder, true) { onNavigate("files") },
        DashboardItem("Logs", "logcat, bugreport", Icons.Filled.Terminal, true) { onNavigate("logs") },
        DashboardItem("Auditoria de seguranca", "root, permissoes, rede, logs", Icons.Filled.LockPerson, true) { onNavigate("audit") },
        DashboardItem("Espelhamento de tela", "fase 4 - em breve", Icons.Filled.ScreenShare, false) {}
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PAINEL", style = MaterialTheme.typography.headlineMedium, color = MatrixGreen, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BlinkingDot(color = if (connected != null) MatrixGreen else AlertRed)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = connected?.let { "conectado: ${it.host}:${it.port}" } ?: "desconectado",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Icon(Icons.Filled.Cable, contentDescription = null, tint = TextMuted)
        }

        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(items) { item -> DashboardCard(item) }
        }

        Spacer(Modifier.height(12.dp))
        TerminalButton(
            text = "Desconectar",
            onClick = onDisconnect,
            danger = true
        )
    }
}

@Composable
private fun DashboardCard(item: DashboardItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .border(1.dp, if (item.enabled) TerminalBorder else TerminalBorder.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .background(TerminalSurface, RoundedCornerShape(6.dp))
            .clickable(enabled = item.enabled, onClick = item.onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (item.enabled) MatrixGreen else TextMuted
        )
        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (item.enabled) MaterialTheme.colorScheme.onSurface else TextMuted
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
