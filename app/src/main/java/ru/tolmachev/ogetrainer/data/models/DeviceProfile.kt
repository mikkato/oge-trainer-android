package ru.tolmachev.ogetrainer.data.models

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

/** Характеристики устройства: по ним отбирается список доступных моделей. */
data class DeviceProfile(
    val totalRamBytes: Long,
    val availableStorageBytes: Long,
    val cpuCores: Int,
    val abi: String,
    val androidVersion: String,
) {
    val ramGb: Double get() = totalRamBytes / 1024.0 / 1024.0 / 1024.0
    val freeStorageGb: Double get() = availableStorageBytes / 1024.0 / 1024.0 / 1024.0

    companion object {
        fun detect(context: Context): DeviceProfile {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memory = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
            val free = runCatching {
                val stat = StatFs(context.filesDir.absolutePath)
                stat.availableBlocksLong * stat.blockSizeLong
            }.getOrDefault(0L)

            return DeviceProfile(
                totalRamBytes = memory.totalMem,
                availableStorageBytes = free,
                cpuCores = Runtime.getRuntime().availableProcessors(),
                abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a",
                androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )
        }
    }
}
