package com.remotedroid.app.core.model

/** Uma entrada (arquivo ou pasta) listada no sistema de arquivos do aparelho alvo. */
data class RemoteFile(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    val permissions: String? = null
)
