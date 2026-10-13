package com.remotedroid.app.core.model

/** Representa um pacote instalado no aparelho alvo. */
data class AppInfo(
    val packageName: String,
    val apkPath: String? = null,
    val isSystemApp: Boolean = false
)
