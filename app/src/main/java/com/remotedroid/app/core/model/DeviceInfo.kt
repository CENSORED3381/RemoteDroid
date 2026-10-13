package com.remotedroid.app.core.model

/** Resumo de informacoes do aparelho alvo, obtidas via getprop/dumpsys/proc. */
data class DeviceInfo(
    val manufacturer: String = "-",
    val model: String = "-",
    val androidVersion: String = "-",
    val sdkInt: String = "-",
    val buildFingerprint: String = "-",
    val batteryLevel: Int? = null,
    val batteryStatus: String = "-",
    val totalStorageMb: Long? = null,
    val freeStorageMb: Long? = null,
    val totalMemoryMb: Long? = null,
    val freeMemoryMb: Long? = null
)
