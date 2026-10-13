package com.remotedroid.app.ui.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.remotedroid.app.ui.theme.AlertRed
import com.remotedroid.app.ui.theme.MatrixGreen
import com.remotedroid.app.ui.theme.TerminalBlackDeep
import com.remotedroid.app.ui.theme.TerminalBorder
import com.remotedroid.app.ui.theme.TerminalSurface
import com.remotedroid.app.ui.theme.TextMuted
import com.remotedroid.app.ui.theme.TextSecondary
import com.remotedroid.app.ui.theme.WarnAmber

/**
 * Janela com moldura de terminal: barra de titulo com "bolinhas" e
 * cabecalho estilo prompt (ex: "root@remotedroid:~#").
 */
@Composable
fun TerminalWindow(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScopeAlias.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, TerminalBorder), RoundedCornerShape(6.dp))
            .background(TerminalSurface, RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalBlackDeep, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrafficDot(AlertRed)
            Spacer(Modifier.width(6.dp))
            TrafficDot(WarnAmber)
            Spacer(Modifier.width(6.dp))
            TrafficDot(MatrixGreen)
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
        }
        Divider(color = TerminalBorder, thickness = 1.dp)
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

// Alias apenas para deixar a assinatura do content legivel sem importar ColumnScope diretamente aqui.
typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun TrafficDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape)
    )
}

/** Ponto piscante de status (online/offline). */
@Composable
fun BlinkingDot(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ) as InfiniteRepeatableSpec<Float>,
        label = "blinkAlpha"
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color.copy(alpha = alpha), CircleShape)
    )
}

/** Campo de texto com visual de prompt de terminal. */
@Composable
fun TerminalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    prompt: String = "$"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        placeholder = { Text(placeholder, color = TextMuted, fontFamily = FontFamily.Monospace) },
        leadingIcon = {
            Text(
                text = prompt,
                color = MatrixGreen,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 10.dp)
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MatrixGreen,
            unfocusedBorderColor = TerminalBorder,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = MatrixGreen,
            focusedLabelColor = MatrixGreen,
            unfocusedLabelColor = TextSecondary
        )
    )
}

/** Botao principal, com a mesma linguagem visual "glow verde". */
@Composable
fun TerminalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    danger: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(46.dp),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) AlertRed else MatrixGreen,
            contentColor = TerminalBlackDeep,
            disabledContainerColor = TerminalBorder,
            disabledContentColor = TextMuted
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = TerminalBlackDeep,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** Area de log tipo console, mostrando linhas estilo "[*] ..." / "[+] ..." / "[-] ...". */
@Composable
fun ConsoleLog(lines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalBlackDeep, RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, TerminalBorder), RoundedCornerShape(4.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (lines.isEmpty()) {
            Text(
                text = "// aguardando comandos...",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        lines.takeLast(200).forEach { line ->
            Text(
                text = line,
                color = colorForLogLine(line),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun colorForLogLine(line: String): Color = when {
    line.startsWith("[+]") -> MatrixGreen
    line.startsWith("[-]") -> AlertRed
    line.startsWith("[!]") -> WarnAmber
    else -> TextSecondary
}
