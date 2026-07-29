package com.profans.elmospace

import android.os.Environment
import android.os.StatFs

object DeviceStorageUtils {
    data class StorageSpace(
        val totalBytes: Long,
        val availableBytes: Long
    ) {
        val usedBytes: Long = (totalBytes - availableBytes).coerceAtLeast(0L)
        val usedPercent: Int =
            if (totalBytes <= 0L) 0 else ((usedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
    }

    fun internalStorageSpace(): StorageSpace {
        val statFs = StatFs(Environment.getDataDirectory().path)
        return StorageSpace(
            totalBytes = statFs.totalBytes,
            availableBytes = statFs.availableBytes
        )
    }
}
