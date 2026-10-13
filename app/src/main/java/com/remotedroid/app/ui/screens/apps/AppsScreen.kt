package com.remotedroid.app.ui.screens.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.remotedroid.app.core.model.AppInfo
import com.remotedroid.app.ui.components.TerminalButton
import com.remotedroid.app.ui.components.TerminalTextField
import com.remotedroid.app.ui.theme.AlertRed
import com.remotedroid.app.ui.theme.MatrixGreen
import com.remotedroid.app.ui.theme.TerminalBorder
import com.remotedroid.app.ui.theme.TextMuted
import com.remotedroid.app.ui.theme.TextSecondary

@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val ui by viewModel.ui.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("APPS", style = MaterialTheme.typography.headlineMedium, color = MatrixGreen)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("mostrar apps de sistema", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Switch(
                checked = ui.includeSystem,
                onCheckedChange = { viewModel.toggleSystemApps() },
                colors = SwitchDefaults.colors(checkedTrackColor = MatrixGreen)
            )
        }

        TerminalTextField(
            value = ui.query,
            onValueChange = viewModel::onQueryChange,
            label = "filtrar por pacote",
            placeholder = "com.exemplo.app",
            prompt = "/"
        )

        ui.statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
        }

        when {
            ui.loading -> Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = MatrixGreen)
            }
            ui.error != null -> Text("[-] ${ui.error}", color = AlertRed, modifier = Modifier.padding(top = 16.dp))
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(viewModel.filteredApps(), key = { it.packageName }) { app ->
                    AppRow(app = app, onLaunch = { viewModel.launch(app) }, onUninstall = { viewModel.uninstall(app) })
                    Divider(color = TerminalBorder)
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, onLaunch: () -> Unit, onUninstall: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(app.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            if (app.isSystemApp) "sistema" else "usuario",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalButton(text = "abrir", onClick = onLaunch, modifier = Modifier.weight(1f))
            if (!app.isSystemApp) {
                TerminalButton(text = "remover", onClick = onUninstall, danger = true, modifier = Modifier.weight(1f))
            }
        }
    }
}
