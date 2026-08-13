package com.profans.elmospace

import android.content.Context
import android.os.Build
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

object AppUpdateManager {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/FaKeOcEaNcAt/ELMOSpace/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val UPDATE_CACHE_DIR = "app_updates"

    fun checkLatest(context: Context): CheckResult {
        val response = httpGet(LATEST_RELEASE_API)
        val json = JSONObject(response)
        val body = json.optString("body")
        val versionCode = parseVersionCode(body)
        val versionName = parseVersionName(json, body)
        val releaseTitle = json.optString("name").ifBlank { "Beta测试版$versionName" }
        val releaseUrl = json.optString("html_url").ifBlank { json.optString("url") }
        val publishedAt = json.optString("published_at")
        val apkAsset = findApkAsset(json.optJSONArray("assets"))
            ?: return CheckResult.NoApk
        val digest = apkAsset.optString("digest")
        val sha256 = parseSha256FromDigest(digest) ?: parseSha256FromBody(body)
        if (sha256.isNullOrBlank()) return CheckResult.MissingChecksum

        val info = ReleaseInfo(
            versionCode = versionCode,
            versionName = versionName,
            title = releaseTitle,
            publishedAt = publishedAt,
            releaseUrl = releaseUrl,
            apkName = apkAsset.getString("name"),
            apkSize = apkAsset.optLong("size", 0L),
            apkDownloadUrl = apkAsset.optString("browser_download_url")
                .ifBlank { apkAsset.getString("url") },
            sha256 = sha256.lowercase(Locale.US),
            changelog = parseChangelog(body)
        )
        return if (info.versionCode > currentVersionCode(context)) {
            CheckResult.UpdateAvailable(info)
        } else {
            CheckResult.UpToDate
        }
    }

    fun downloadAndVerify(
        context: Context,
        info: ReleaseInfo,
        isCanceled: () -> Boolean = { false },
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): DownloadResult {
        val updateDir = File(context.cacheDir, UPDATE_CACHE_DIR).apply { mkdirs() }
        val target = File(updateDir, info.apkName)
        val temp = File(updateDir, "${info.apkName}.download")
        if (temp.exists()) temp.delete()
        if (target.exists()) target.delete()

        if (isCanceled()) return DownloadResult.Canceled

        val connection = (URL(info.apkDownloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "ELMOSpace-Android")
            connect()
        }
        try {
            if (connection.responseCode !in 200..299) {
                return DownloadResult.Failure("下载更新失败，请稍后重试")
            }
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: info.apkSize
            var downloaded = 0L
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        if (isCanceled()) {
                            temp.delete()
                            return DownloadResult.Canceled
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            val actualSha256 = sha256(temp)
            if (!actualSha256.equals(info.sha256, ignoreCase = true)) {
                temp.delete()
                return DownloadResult.Failure("安装包校验失败，请稍后重试")
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target
            )
            return DownloadResult.Success(target, uri)
        } catch (error: Exception) {
            temp.delete()
            target.delete()
            if (isCanceled()) return DownloadResult.Canceled
            return DownloadResult.Failure(error.message ?: "下载更新失败，请稍后重试")
        } finally {
            connection.disconnect()
        }
    }

    fun clearCachedUpdates(context: Context) {
        File(context.cacheDir, UPDATE_CACHE_DIR).deleteRecursively()
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "ELMOSpace-Android")
            connect()
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("检查更新失败，请检查网络后重试")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun findApkAsset(assets: JSONArray?): JSONObject? {
        if (assets == null) return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) return asset
        }
        return null
    }

    private fun parseVersionCode(body: String): Int {
        Regex("""\|\s*版本号\s*\|\s*`?(\d+)`?\s*\|""")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }
        Regex("""versionCode\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }
        return 0
    }

    private fun parseVersionName(json: JSONObject, body: String): String {
        Regex("""\|\s*应用程序版本\s*\|\s*`?([^`|\s]+)`?\s*\|""")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return "V$it" }
        Regex("""V\d+\.\d+\.\d+""")
            .find(json.optString("tag_name") + " " + json.optString("name"))
            ?.value
            ?.let { return it }
        return json.optString("tag_name").ifBlank { "未知版本" }
    }

    private fun parseSha256FromDigest(digest: String): String? {
        val value = digest.trim()
        if (!value.startsWith("sha256:", ignoreCase = true)) return null
        return value.substringAfter(':').takeIf { isSha256(it) }
    }

    private fun parseSha256FromBody(body: String): String? {
        Regex("""SHA256\s*\|\s*`?([A-Fa-f0-9]{64})`?""")
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        Regex("""sha256\s*[:=]\s*([A-Fa-f0-9]{64})""", RegexOption.IGNORE_CASE)
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { return it }
        return null
    }

    private fun parseChangelog(body: String): String {
        val start = body.indexOf("## 三、更新日志")
        if (start < 0) return "暂无更新日志。"
        val afterTitle = body.indexOf('\n', start).takeIf { it >= 0 } ?: return "暂无更新日志。"
        val endMarkers = listOf("\n---", "\n## 四、开发者留信")
        val end = endMarkers
            .map { marker -> body.indexOf(marker, afterTitle).takeIf { it >= 0 } ?: body.length }
            .minOrNull() ?: body.length
        return body.substring(afterTitle, end).trim().ifBlank { "暂无更新日志。" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isSha256(value: String) = value.matches(Regex("[A-Fa-f0-9]{64}"))

    private fun currentVersionCode(context: Context): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    data class ReleaseInfo(
        val versionCode: Int,
        val versionName: String,
        val title: String,
        val publishedAt: String,
        val releaseUrl: String,
        val apkName: String,
        val apkSize: Long,
        val apkDownloadUrl: String,
        val sha256: String,
        val changelog: String
    )

    sealed class CheckResult {
        data object UpToDate : CheckResult()
        data object NoApk : CheckResult()
        data object MissingChecksum : CheckResult()
        data class UpdateAvailable(val info: ReleaseInfo) : CheckResult()
    }

    sealed class DownloadResult {
        data class Success(val file: File, val uri: Uri) : DownloadResult()
        data class Failure(val message: String) : DownloadResult()
        data object Canceled : DownloadResult()
    }
}
