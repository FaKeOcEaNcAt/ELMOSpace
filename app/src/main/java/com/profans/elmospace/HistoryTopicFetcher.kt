package com.profans.elmospace

import android.content.Context
import com.profans.elmospace.WebConstants.TOPIC_DETAIL_API_URL
import java.net.HttpURLConnection
import org.json.JSONObject

data class HistoryTopicDetails(
    val title: String,
    val author: String,
    val viewCount: Long
)

object HistoryTopicFetcher {
    private const val HISTORY_DETAIL_TIMEOUT_MS = 8_000

    fun sanitizeText(value: String, maxLength: Int): String =
        value.replace(Regex("\\s+"), " ").trim().take(maxLength)

    fun fetchTopicDetails(
        context: Context,
        topicId: Long,
        fallbackTitle: String,
        fallbackAuthor: String
    ): HistoryTopicDetails {
        var connection: HttpURLConnection? = null
        return try {
            connection = AppNetworkProxy.openHttpConnection(
                context,
                "$TOPIC_DETAIL_API_URL/$topicId?id=$topicId"
            )
            connection.requestMethod = "GET"
            connection.connectTimeout = HISTORY_DETAIL_TIMEOUT_MS
            connection.readTimeout = HISTORY_DETAIL_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "ElmoSpace-Android-WebView")
            if (connection.responseCode !in 200..299) {
                return HistoryTopicDetails(fallbackTitle, fallbackAuthor, -1L)
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val response = JSONObject(body)
            if (response.optString("Code") != "0") {
                return HistoryTopicDetails(fallbackTitle, fallbackAuthor, -1L)
            }
            val data = response.optJSONObject("data")
                ?: return HistoryTopicDetails(fallbackTitle, fallbackAuthor, -1L)
            HistoryTopicDetails(
                sanitizeText(data.optString("title", fallbackTitle), 240)
                    .ifBlank { fallbackTitle },
                sanitizeText(data.optString("user_nick_name", fallbackAuthor), 80)
                    .ifBlank { fallbackAuthor },
                data.optLong("view_num", -1L)
            )
        } catch (_: Exception) {
            HistoryTopicDetails(fallbackTitle, fallbackAuthor, -1L)
        } finally {
            connection?.disconnect()
        }
    }
}
