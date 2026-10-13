package com.remotedroid.app.ui.screens.mirror

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.remotedroid.app.core.model.MirrorQuality
import com.remotedroid.app.core.model.MirrorState
import com.remotedroid.app.ui.components.BlinkingDot
import com.remotedroid.app.ui.theme.AlertRed
import com.remotedroid.app.ui.theme.MatrixGreen
import com.remotedroid.app.ui.theme.TerminalBlackDeep
import com.remotedroid.app.ui.theme.TerminalBorder
import com.remotedroid.app.ui.theme.TerminalSurface
import com.remotedroid.app.ui.theme.TextMuted
import com.remotedroid.app.ui.theme.TextSecondary
import com.remotedroid.app.ui.theme.WarnAmber

@Composable
fun MirrorScreen(viewModel: MirrorViewModel) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("ESPELHAMENTO DE TELA", style = MaterialTheme.typography.headlineMedium, color = MatrixGreen, fontWeight = FontWeight.Bold)
        Text(
            "Tela ao vivo do aparelho alvo, via scrcpy-server + MediaCodec. " +
                "Somente visualizacao nesta fase (sem toques/gestos).",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        QualitySelector(
            selected = viewModel.quality,
            onSelect = viewModel::setQuality
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .border(1.dp, TerminalBorder, RoundedCornerShape(6.dp))
                .background(TerminalBlackDeep, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                viewModel.onSurfaceAvailable(holder.surface)
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                viewModel.onSurfaceDestroyed()
                            }
                        })
                    }
                }
            )

            MirrorOverlay(state)
        }

        MirrorStatusLine(state)
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.onSurfaceDestroyed() }
    }
}

@Composable
private fun QualitySelector(selected: MirrorQuality, onSelect: (MirrorQuality) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MirrorQuality.entries.forEach { quality ->
            val isSelected = quality == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .border(1.dp, if (isSelected) MatrixGreen else TerminalBorder, RoundedCornerShape(4.dp))
                    .background(if (isSelected) MatrixGreen.copy(alpha = 0.12f) else TerminalSurface, RoundedCornerShape(4.dp))
                    .clickable { onSelect(quality) }
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = quality.label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MatrixGreen else TextSecondary
                )
                Text(
                    text = "${quality.maxSize}px / ${quality.maxFps}fps",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun MirrorOverlay(state: MirrorState) {
    when (state) {
        is MirrorState.Idle -> OverlayMessage("// toque em conectar para iniciar o espelhamento")
        is MirrorState.PushingServidor -> OverlayMessage("[*] enviando scrcpy-server ao aparelho...", showSpinner = true)
        is MirrorState.AguardandoSocket -> OverlayMessage("[*] aguardando socket de video...", showSpinner = true)
        is MirrorState.Error -> OverlayMessage("[-] ${state.message}", color = AlertRed)
        is MirrorState.Streaming -> Unit // video ocupa a tela, sem overlay
    }
}

@Composable
private fun OverlayMessage(text: String, color: androidx.compose.ui.graphics.Color = TextSecondary, showSpinner: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showSpinner) {
            CircularProgressIndicator(modifier = Modifier.height(24.dp), color = MatrixGreen, strokeWidth = 2.dp)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(horizontal = 20.dp))
    }
}

@Composable
private fun MirrorStatusLine(state: MirrorState) {
    val (dotColor, label) = when (state) {
        is MirrorState.Idle -> TextMuted to "desconectado"
        is MirrorState.PushingServidor -> WarnAmber to "enviando servidor..."
        is MirrorState.AguardandoSocket -> WarnAmber to "conectando..."
        is MirrorState.Error -> AlertRed to "erro: ${state.message}"
        is MirrorState.Streaming -> MatrixGreen to
            "${state.deviceName} - ${state.width}x${state.height} - ${state.fps} fps"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BlinkingDot(color = dotColor)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}
