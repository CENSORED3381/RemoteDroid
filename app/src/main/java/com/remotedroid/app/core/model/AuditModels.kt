package com.remotedroid.app.core.model

/** Nivel de severidade de um item da auditoria, equivalente a good/warn/bad/info do pegasus_pt.py. */
enum class AuditLevel { OK, WARN, BAD, INFO }

/** Um item de achado dentro de uma secao da auditoria, com detalhes opcionais (lista de pacotes, arquivos, etc). */
data class AuditFinding(
    val level: AuditLevel,
    val message: String,
    val details: List<String> = emptyList()
)

/** Uma secao da auditoria (ex: "Status de root", "Patch de seguranca"). */
data class AuditSection(
    val title: String,
    val findings: List<AuditFinding>
)

/** Relatorio completo de uma execucao da auditoria de seguranca. */
data class AuditReport(
    val generatedAt: Long,
    val deviceLabel: String,
    val sections: List<AuditSection>,
    val okCount: Int,
    val warnCount: Int,
    val badCount: Int
) {
    /** Representacao em texto simples, pronta para salvar em arquivo (como o relatorio do pegasus_pt.py). */
    fun toPlainText(): String = buildString {
        appendLine("AUDITORIA DE SEGURANCA REMOTEDROID")
        appendLine("Aparelho: $deviceLabel")
        appendLine()
        sections.forEach { section ->
            appendLine("== ${section.title} ==")
            section.findings.forEach { f ->
                val tag = when (f.level) {
                    AuditLevel.OK -> "[+]"
                    AuditLevel.WARN -> "[!]"
                    AuditLevel.BAD -> "[-]"
                    AuditLevel.INFO -> "[*]"
                }
                appendLine("$tag ${f.message}")
                f.details.forEach { appendLine("    $it") }
            }
            appendLine()
        }
        appendLine("Resumo: $okCount ok, $warnCount avisos, $badCount problemas")
    }
}
