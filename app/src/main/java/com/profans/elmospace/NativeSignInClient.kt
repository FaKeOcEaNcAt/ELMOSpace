package com.profans.elmospace

import android.content.Context
import com.profans.elmospace.WebConstants.MEMBER_INFO_URL
import com.profans.elmospace.WebConstants.SIGN_IN_URL
import com.profans.elmospace.WebConstants.SIGN_STATUS_URL
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import org.json.JSONObject

object NativeSignInClient {
    sealed class Result {
        data object AlreadySigned : Result()
        data class AlreadySignedWithScore(val score: String) : Result()
        data object LoginInvalid : Result()
        data object InterfaceUnavailable : Result()
        data class SignedWithScore(val score: String) : Result()
        data object SignedWithoutScore : Result()
    }

    private data class ApiResponse(val httpCode: Int, val body: JSONObject)

    fun signIn(context: Context): Result {
        val token = AppPreferences.getSignAuthToken(context)
        if (token.isBlank()) return Result.LoginInvalid

        val status = runCatching {
            request(context, SIGN_STATUS_URL, METHOD_GET, token)
        }.getOrElse { return Result.InterfaceUnavailable }
        if (status.isLoginInvalid()) return Result.LoginInvalid
        if (!status.isSuccess()) return Result.InterfaceUnavailable

        val statusData = status.body.optJSONObject("data")
            ?: return Result.InterfaceUnavailable
        if (!statusData.has("has_sign_in")) return Result.InterfaceUnavailable
        if (statusData.optBoolean("has_sign_in")) {
            val score = fetchScore(context, token)
            return if (score.isNullOrBlank()) {
                Result.AlreadySigned
            } else {
                Result.AlreadySignedWithScore(score)
            }
        }

        val sign = runCatching {
            request(context, SIGN_IN_URL, METHOD_POST, token, EMPTY_JSON_BODY)
        }.getOrElse { return Result.InterfaceUnavailable }
        if (sign.isLoginInvalid()) return Result.LoginInvalid
        if (!sign.isSuccess()) return Result.InterfaceUnavailable

        val score = fetchScore(context, token)
        return if (score.isNullOrBlank()) {
            Result.SignedWithoutScore
        } else {
            Result.SignedWithScore(score)
        }
    }

    private fun fetchScore(context: Context, token: String): String? {
        val response = runCatching {
            request(context, MEMBER_INFO_URL, METHOD_POST, token, EMPTY_JSON_BODY)
        }.getOrNull() ?: return null
        if (!response.isSuccess()) return null

        val user = response.body.optJSONObject("data")?.optJSONObject("user")
            ?: return null
        return user.opt("score")?.toString()
    }

    private fun request(
        context: Context,
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
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
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
