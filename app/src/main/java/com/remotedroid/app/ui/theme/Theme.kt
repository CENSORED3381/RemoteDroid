package com.remotedroid.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RemoteDroidDarkScheme = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = TerminalBlackDeep,
    primaryContainer = MatrixGreenFaded,
    onPrimaryContainer = MatrixGreenBright,
    secondary = InfoCyan,
    onSecondary = TerminalBlackDeep,
    background = TerminalBlack,
    onBackground = TextPrimary,
    surface = TerminalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TerminalSurfaceAlt,
    onSurfaceVariant = TextSecondary,
    outline = TerminalBorder,
    error = AlertRed,
    onError = TerminalBlackDeep
)

@Composable
fun RemoteDroidTheme(
    // O app e deliberadamente mono-tema (dark/hacker); o parametro fica
    // disponivel caso um tema claro seja adicionado no futuro.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = RemoteDroidDarkScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RemoteDroidTypography,
        content = content
    )
}
