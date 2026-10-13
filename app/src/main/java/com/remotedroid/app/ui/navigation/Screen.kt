package com.remotedroid.app.ui.navigation

sealed class Screen(val route: String, val label: String) {
    data object Connect : Screen("connect", "Conectar")
    data object Home : Screen("home", "Painel")
    data object DeviceInfo : Screen("device_info", "Info do aparelho")
    data object Apps : Screen("apps", "Apps")
    data object Files : Screen("files", "Arquivos")
    data object Logs : Screen("logs", "Logs")
    data object Audit : Screen("audit", "Auditoria de seguranca")
}
