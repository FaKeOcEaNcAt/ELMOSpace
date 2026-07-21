package com.profans.elmospace

import android.content.Context
import java.io.File
import java.util.Locale

object WebCacheUtils {
    fun getCacheSize(context: Context): Long {
        val appCacheSize = directorySize(context.cacheDir)
        val webViewRoot = File(context.applicationInfo.dataDir, "app_webview")
        return appCacheSize + webViewCacheSize(webViewRoot)
    }

    fun clearAppCache(context: Context) {
        deleteDirectoryContents(context.cacheDir)
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    }

    private fun webViewCacheSize(root: File): Long {
        if (!root.exists()) return 0L
        return root.walkTopDown()
            .filter { it.isFile && it.isInsideCacheDirectory(root) }
            .sumOf { it.length() }
    }

    private fun File.isInsideCacheDirectory(root: File): Boolean {
        var current = parentFile
        while (current != null && current != root) {
            if (current.name.contains("cache", ignoreCase = true)) return true
            current = current.parentFile
        }
        return false
    }

    private fun directorySize(directory: File): Long =
        if (!directory.exists()) 0L else directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun deleteDirectoryContents(directory: File) {
        directory.listFiles()?.forEach { child ->
            if (child.isDirectory) child.deleteRecursively() else child.delete()
        }
    }
}
