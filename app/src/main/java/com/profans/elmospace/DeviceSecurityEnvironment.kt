package com.profans.elmospace

import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit

object DeviceSecurityEnvironment {
    private val rootPaths = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/data/local/su",
        "/data/adb/magisk"
    )

    fun mayBeCompromised(): Boolean = hasRootIndicators() || isBootloaderUnlocked()

    private fun hasRootIndicators(): Boolean {
        if (Build.TAGS?.contains("test-keys", ignoreCase = true) == true) return true
        if (rootPaths.any { File(it).exists() }) return true

        val secure = readSystemProperty("ro.secure")
        val debuggable = readSystemProperty("ro.debuggable")
        if (secure == "0" || debuggable == "1") return true

        return commandReturnsPath("/system/bin/which", "su") ||
            commandReturnsPath("which", "su")
    }

    private fun isBootloaderUnlocked(): Boolean {
        val flashLocked = readSystemProperty("ro.boot.flash.locked")
        val vbmetaState = readSystemProperty("ro.boot.vbmeta.device_state")
        val verifiedBootState = readSystemProperty("ro.boot.verifiedbootstate")
        return flashLocked == "0" ||
            vbmetaState.equals("unlocked", ignoreCase = true) ||
            verifiedBootState.equals("orange", ignoreCase = true)
    }

    private fun readSystemProperty(name: String): String {
        return runCommand("/system/bin/getprop", name)
            .ifBlank { runCommand("getprop", name) }
            .trim()
    }

    private fun commandReturnsPath(vararg command: String): Boolean =
        runCommand(*command).isNotBlank()

    private fun runCommand(vararg command: String): String {
        var process: Process? = null
        return try {
            process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                ""
            } else {
                process.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (_: Exception) {
            ""
        } finally {
            process?.destroy()
        }
    }

    private const val COMMAND_TIMEOUT_MS = 500L
}
