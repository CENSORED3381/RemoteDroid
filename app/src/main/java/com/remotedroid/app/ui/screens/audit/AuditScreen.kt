package com.remotedroid.app.ui.screens.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.remotedroid.app.core.model.AuditLevel
import com.remotedroid.app.core.model.AuditReport
import com.remotedroid.app.core.model.AuditSection
import com.remotedroid.app.ui.components.ConsoleLog
import com.remotedroid.app.ui.components.TerminalButton
import com.remotedroid.app.ui.components.TerminalWindow
import com.remotedroid.app.ui.theme.AlertRed
import com.remotedroid.app.ui.theme.MatrixGreen
import com.remotedroid.app.ui.theme.TextMuted
import com.remotedroid.app.ui.theme.TextSecondary
import com.remotedroid.app.ui.theme.WarnAmber

@Composable
fun AuditScreen(viewModel: AuditViewModel) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AUDITORIA DE SEGURANCA", style = MaterialTheme.typography.headlineMedium, color = MatrixGreen)
        Text(
            "Verificacoes somente leitura (root, permissoes, patch, criptografia, logs, rede). " +
                "Use somente em aparelhos seus ou com autorizacao do dono; nada e alterado no aparelho alvo.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TerminalButton(
                text = if (ui.report == null) "Rodar auditoria" else "Rodar de novo",
                onClick = viewModel::runAudit,
                loading = ui.running,
                modifier = Modifier.weight(1f)
            )
            if (ui.report != null) {
                TerminalButton(
                    text = "Salvar relatorio",
                    onClick = { viewModel.saveReport(context.getExternalFilesDir(null) ?: context.filesDir) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        ui.statusMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }

        ui.report?.let { report -> ReportSummary(report) }

        ui.report?.sections?.forEach { section -> SectionWindow(section) }

        if (ui.report == null && !ui.running) {
            Text(
                "// toque em \"rodar auditoria\" para comecar",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun ReportSummary(report: AuditReport) {
    TerminalWindow(title = "resumo") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryCount("ok", report.okCount, MatrixGreen)
            SummaryCount("avisos", report.warnCount, WarnAmber)
            SummaryCount("problemas", report.badCount, AlertRed)
        }
    }
}

@Composable
private fun SummaryCount(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun SectionWindow(section: AuditSection) {
    TerminalWindow(title = section.title) {
        val lines = buildList {
            section.findings.forEach { f ->
                val tag = when (f.level) {
                    AuditLevel.OK -> "[+]"
                    AuditLevel.WARN -> "[!]"
                    AuditLevel.BAD -> "[-]"
                    AuditLevel.INFO -> "[*]"
                }
                add("$tag ${f.message}")
                f.details.forEach { add("    $it") }
            }
        }
        ConsoleLog(lines = lines)
    }
}
