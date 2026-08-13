package com.profans.elmospace

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.Executors

object AppUpdateUi {
    private val executor = Executors.newSingleThreadExecutor()
    private var pendingInstallPermissionInfo: AppUpdateManager.ReleaseInfo? = null

    fun checkForUpdate(activity: Activity, manual: Boolean) {
        if (manual) {
            Toast.makeText(activity, R.string.checking_update, Toast.LENGTH_SHORT).show()
        }
        executor.execute {
            val result = runCatching { AppUpdateManager.checkLatest(activity.applicationContext) }
                .getOrElse {
                    activity.runOnUiThread {
                        if (manual) {
                            Toast.makeText(
                                activity,
                                R.string.update_check_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    return@execute
                }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                handleCheckResult(activity, result, manual)
            }
        }
    }

    fun maybeCheckOnStartup(activity: Activity) {
        if (!AppPreferences.isStartupUpdateCheckEnabled(activity)) return
        if (!AppPreferences.consumeStartupUpdateCheck(activity)) return
        checkForUpdate(activity, manual = false)
    }

    fun resumePendingInstallIfAllowed(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val info = pendingInstallPermissionInfo ?: return
        pendingInstallPermissionInfo = null
        if (canRequestPackageInstalls(activity)) {
            downloadAndInstall(activity, info)
        } else {
            Toast.makeText(activity, R.string.install_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCheckResult(
        activity: Activity,
        result: AppUpdateManager.CheckResult,
        manual: Boolean
    ) {
        when (result) {
            AppUpdateManager.CheckResult.UpToDate -> {
                if (manual) Toast.makeText(
                    activity,
                    R.string.already_latest_version,
                    Toast.LENGTH_SHORT
                ).show()
            }
            AppUpdateManager.CheckResult.NoApk -> {
                if (manual) Toast.makeText(
                    activity,
                    R.string.update_release_no_apk,
                    Toast.LENGTH_SHORT
                ).show()
            }
            AppUpdateManager.CheckResult.MissingChecksum -> {
                if (manual) Toast.makeText(
                    activity,
                    R.string.update_release_missing_checksum,
                    Toast.LENGTH_SHORT
                ).show()
            }
            is AppUpdateManager.CheckResult.UpdateAvailable -> {
                if (!manual &&
                    AppPreferences.getIgnoredUpdateVersionCode(activity) >= result.info.versionCode
                ) {
                    return
                }
                showUpdateDialog(activity, result.info)
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, info: AppUpdateManager.ReleaseInfo) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        view.findViewById<TextView>(R.id.updateVersionName).text = "Beta测试版${info.versionName}"
        view.findViewById<TextView>(R.id.updateChangelog).text = info.changelog
        view.findViewById<TextView>(R.id.updateNowButton).apply {
            setTextColor(AppAccentColor.color(activity))
            setOnClickListener {
                dialog.dismiss()
                ensureInstallPermissionThenDownload(activity, info)
            }
        }
        view.findViewById<TextView>(R.id.remindNextStartupButton).setOnClickListener {
            AppPreferences.remindUpdateNextStartup(activity, info.versionCode)
            dialog.dismiss()
        }
        view.findViewById<TextView>(R.id.ignoreThisUpdateButton).setOnClickListener {
            AppPreferences.setIgnoredUpdateVersionCode(activity, info.versionCode)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun ensureInstallPermissionThenDownload(
        activity: Activity,
        info: AppUpdateManager.ReleaseInfo
    ) {
        if (canRequestPackageInstalls(activity)) {
            downloadAndInstall(activity, info)
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_install_permission_title)
            .setMessage(R.string.update_install_permission_message)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                pendingInstallPermissionInfo = info
                openInstallPermissionSettings(activity)
            }
            .setNegativeButton(R.string.permission_cancel, null)
            .show()
            .also { tintDialogButtons(it, activity) }
    }

    private fun canRequestPackageInstalls(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return activity.packageManager.canRequestPackageInstalls()
    }

    private fun openInstallPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
            }.onFailure {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                })
            }
        } else {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
            })
        }
    }

    private fun downloadAndInstall(activity: Activity, info: AppUpdateManager.ReleaseInfo) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update_progress, null)
        val title = view.findViewById<TextView>(R.id.updateProgressTitle)
        val message = view.findViewById<TextView>(R.id.updateProgressMessage)
        val progress = view.findViewById<ProgressBar>(R.id.updateProgressBar)
        AppAccentColor.tintProgress(progress, activity)
        progress.isIndeterminate = info.apkSize <= 0L
        message.text = activity.getString(R.string.downloading_update)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .create()
        dialog.setCancelable(false)
        dialog.show()

        executor.execute {
            val result = AppUpdateManager.downloadAndVerify(activity.applicationContext, info) {
                    downloaded,
                    total ->
                activity.runOnUiThread {
                    if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                    if (total > 0L) {
                        progress.isIndeterminate = false
                        progress.progress = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                        message.text = activity.getString(
                            R.string.download_update_progress,
                            formatBytes(downloaded),
                            formatBytes(total)
                        )
                    } else {
                        progress.isIndeterminate = true
                        message.text = activity.getString(R.string.downloading_update)
                    }
                }
            }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                when (result) {
                    is AppUpdateManager.DownloadResult.Success -> {
                        title.setText(R.string.verifying_update_package)
                        message.text = ""
                        progress.isIndeterminate = true
                        dialog.dismiss()
                        installApk(activity, result.uri)
                    }
                    is AppUpdateManager.DownloadResult.Failure -> {
                        dialog.dismiss()
                        Toast.makeText(activity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun installApk(activity: Activity, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                activity,
                R.string.start_install_update_failed,
                Toast.LENGTH_LONG
            ).show()
        } catch (_: SecurityException) {
            Toast.makeText(
                activity,
                R.string.install_permission_denied,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "${bytes} B"
        val units = arrayOf("KB", "MB", "GB")
        var value = bytes / 1024.0
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return String.format(Locale.US, "%.1f %s", value, units[index])
    }

    private fun tintDialogButtons(dialog: AlertDialog, activity: Activity) {
        val accent = AppAccentColor.color(activity)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent)
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(accent)
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(accent)
    }
}
