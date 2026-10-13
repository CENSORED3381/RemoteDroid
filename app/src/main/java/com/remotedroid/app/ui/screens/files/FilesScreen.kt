package com.remotedroid.app.ui.screens.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.remotedroid.app.core.model.RemoteFile
import com.remotedroid.app.ui.theme.AlertRed
import com.remotedroid.app.ui.theme.MatrixGreen
import com.remotedroid.app.ui.theme.TerminalBorder
import com.remotedroid.app.ui.theme.TextMuted
import com.remotedroid.app.ui.theme.TextSecondary
import java.io.File

@Composable
fun FilesScreen(viewModel: FilesViewModel) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { copyPickedFileAndPush(context, it, viewModel) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ARQUIVOS", style = MaterialTheme.typography.headlineMedium, color = MatrixGreen)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = viewModel::goUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "subir", tint = MatrixGreen)
                }
                Text(
                    text = ui.currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            IconButton(onClick = { pickFileLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.Upload, contentDescription = "enviar arquivo", tint = MatrixGreen)
            }
        }

        ui.statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }

        when {
            ui.loading -> Row(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = MatrixGreen)
            }
            ui.error != null -> Text("[-] ${ui.error}", color = AlertRed)
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(ui.entries, key = { it.fullPath }) { entry ->
                    FileRow(
                        entry = entry,
                        onOpen = { viewModel.openEntry(entry) },
                        onDownload = { viewModel.pull(entry, context.getExternalFilesDir(null) ?: context.filesDir) }
                    )
                    Divider(color = TerminalBorder)
                }
            }
        }
    }
}

@Composable
private fun FileRow(entry: RemoteFile, onOpen: () -> Unit, onDownload: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.isDirectory, onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                contentDescription = null,
                tint = if (entry.isDirectory) MatrixGreen else TextMuted
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                if (!entry.isDirectory && entry.sizeBytes != null) {
                    Text("${entry.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        }
        if (!entry.isDirectory) {
            IconButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = "baixar", tint = MatrixGreen)
            }
        }
    }
}

private fun copyPickedFileAndPush(context: android.content.Context, uri: Uri, viewModel: FilesViewModel) {
    val name = queryDisplayName(context, uri) ?: "arquivo_enviado"
    val tempFile = File(context.cacheDir, name)
    context.contentResolver.openInputStream(uri)?.use { input ->
        tempFile.outputStream().use { output -> input.copyTo(output) }
    }
    viewModel.push(tempFile)
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && nameIndex >= 0) return it.getString(nameIndex)
    }
    return null
}
