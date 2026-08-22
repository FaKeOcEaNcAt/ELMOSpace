package com.profans.elmospace

import com.profans.elmospace.WebConstants.EXCHANGE_LIST_URL
import com.profans.elmospace.WebConstants.EXCHANGE_SUBMIT_URL
import com.profans.elmospace.WebConstants.MEMBER_INFO_URL
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import org.json.JSONArray
import org.json.JSONObject

data class ExchangeItem(
    val exchangeId: Int,
    val itemType: Int,
    val itemName: String,
    val itemCount: Int,
    val useScore: Int,
    val exchangeCount: Int,
    val maxExchangeCount: Int,
    val cycle: String
) {
    val displayName: String = "$itemName*$itemCount"
    val remainingCount: Int = (maxExchangeCount - exchangeCount).coerceAtLeast(0)
    val cycleLabel: String = when (cycle) {
        "day" -> "每日"
        "week" -> "每周"
        "month" -> "每月"
        "life" -> "终生"
        else -> "未知周期"
    }
}

object NativeExchangeClient {
    data class SyncData(val score: Int, val items: List<ExchangeItem>)

    sealed class SyncResult {
        data class Success(val data: SyncData) : SyncResult()
        data object LoginInvalid : SyncResult()
        data object InterfaceUnavailable : SyncResult()
    }

    sealed class ExchangeResult {
        data object Success : ExchangeResult()
        data object LoginInvalid : ExchangeResult()
        data object LimitReached : ExchangeResult()
        data object ScoreNotEnough : ExchangeResult()
        data class Failed(val message: String) : ExchangeResult()
    }

    private data class ApiResponse(val httpCode: Int, val body: JSONObject)
    private sealed class ScoreResult {
        data class Success(val score: Int) : ScoreResult()
        data object LoginInvalid : ScoreResult()
        data object Unavailable : ScoreResult()
    }

    fun sync(context: android.content.Context): SyncResult {
        val token = AppPreferences.getSignAuthToken(context)
        if (token.isBlank()) return SyncResult.LoginInvalid

        val score = when (val result = fetchScore(context, token)) {
            ScoreResult.LoginInvalid -> return SyncResult.LoginInvalid
            ScoreResult.Unavailable -> return SyncResult.InterfaceUnavailable
            is ScoreResult.Success -> result.score
        }
        val listResponse = runCatching {
            request(context, EXCHANGE_LIST_URL, METHOD_GET, token)
        }.getOrElse { return SyncResult.InterfaceUnavailable }
        if (listResponse.isLoginInvalid()) return SyncResult.LoginInvalid
        if (!listResponse.isSuccess()) return SyncResult.InterfaceUnavailable

        val list = listResponse.body.optJSONObject("data")?.optJSONArray("list")
            ?: listResponse.body.optJSONArray("data")
            ?: return SyncResult.InterfaceUnavailable
        val items = parseItems(list)
        return SyncResult.Success(SyncData(score, items))
    }

    fun submit(context: android.content.Context, exchangeId: Int): ExchangeResult {
        val token = AppPreferences.getSignAuthToken(context)
        if (token.isBlank()) return ExchangeResult.LoginInvalid

        val response = runCatching {
            request(
                context,
                EXCHANGE_SUBMIT_URL,
                METHOD_POST,
                token,
                JSONObject().put("exchange_id", exchangeId).toString()
            )
        }.getOrElse { return ExchangeResult.Failed("网络异常") }
        if (response.isLoginInvalid()) return ExchangeResult.LoginInvalid
        if (response.isSuccess()) return ExchangeResult.Success

        val message = response.body.optString("Message")
        return when {
            message.contains("积分") -> ExchangeResult.ScoreNotEnough
            message.contains("上限") || message.contains("限购") -> ExchangeResult.LimitReached
            else -> ExchangeResult.Failed(message.ifBlank { "接口返回异常" })
        }
    }

    fun itemsToJson(items: List<ExchangeItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("exchange_id", item.exchangeId)
                    .put("item_type", item.itemType)
                    .put("item_name", item.itemName)
                    .put("item_count", item.itemCount)
                    .put("use_score", item.useScore)
                    .put("exchange_count", item.exchangeCount)
                    .put("max_exchange_count", item.maxExchangeCount)
                    .put("cycle", item.cycle)
            )
        }
        return array.toString()
    }

    fun itemsFromJson(json: String): List<ExchangeItem> {
        if (json.isBlank()) return emptyList()
        return runCatching { parseItems(JSONArray(json)) }.getOrDefault(emptyList())
    }

    private fun parseItems(array: JSONArray): List<ExchangeItem> {
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    ExchangeItem(
                        exchangeId = item.optInt("exchange_id"),
                        itemType = item.optInt("item_type"),
                        itemName = item.optString("item_name"),
                        itemCount = item.optInt("item_count"),
                        useScore = item.optInt("use_score"),
                        exchangeCount = item.optInt("exchange_count"),
                        maxExchangeCount = item.optInt("max_exchange_count"),
                        cycle = item.optString("cycle")
                    )
                )
            }
        }.filter { it.exchangeId > 0 && it.itemName.isNotBlank() }
    }

    private fun fetchScore(context: android.content.Context, token: String): ScoreResult {
        val response = runCatching {
            request(context, MEMBER_INFO_URL, METHOD_POST, token, EMPTY_JSON_BODY)
        }.getOrNull() ?: return ScoreResult.Unavailable
        if (response.isLoginInvalid()) return ScoreResult.LoginInvalid
        if (!response.isSuccess()) return ScoreResult.Unavailable

        val user = response.body.optJSONObject("data")?.optJSONObject("user")
            ?: return ScoreResult.Unavailable
        return user.opt("score")?.toString()?.toIntOrNull()
            ?.let { ScoreResult.Success(it) }
            ?: ScoreResult.Unavailable
    }

    private fun request(
        context: android.content.Context,
        url: String,
        method: String,
        token: String,
        body: String? = null
    ): ApiResponse {
        val connection = AppNetworkProxy.openHttpConnection(context, url).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", token)
            doInput = true
            if (body != null) doOutput = true
        }

        try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val bodyJson = if (responseText.trimStart().startsWith("{")) {
                JSONObject(responseText)
            } else {
                JSONObject()
            }
            return ApiResponse(responseCode, bodyJson)
        } finally {
            connection.disconnect()
        }
    }

    private fun ApiResponse.isSuccess() = httpCode in 200..299 && body.optString("Code") == "0"

    private fun ApiResponse.isLoginInvalid(): Boolean {
        if (httpCode == HTTP_UNAUTHORIZED || httpCode == HTTP_FORBIDDEN) return true
        if (body.optString("Code") in LOGIN_INVALID_CODES) return true
        return body.optString("Message").contains("登录")
    }

    private val LOGIN_INVALID_CODES = setOf("10004", "30018", "401", "403")
    private const val METHOD_GET = "GET"
    private const val METHOD_POST = "POST"
    private const val EMPTY_JSON_BODY = "{}"
    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
    private const val TIMEOUT_MS = 15_000
}
