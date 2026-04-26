package com.lyx.copy.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class ApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {

    suspend fun uploadClipboard(
        serverUrl: String,
        apiToken: String,
        payload: ClipboardPayload
    ) = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder()
            .add("content_type", payload.contentType)
            .add("content", payload.content)
            .apply {
                payload.mimeType?.let { add("mime_type", it) }
            }
            .build()

        val request = Request.Builder()
            .url(buildEndpoint(serverUrl, SET_CLIPBOARD_PATH))
            .header(HEADER_API_TOKEN, apiToken)
            .post(formBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = body.toJsonObject()
            if (!response.isSuccessful || json?.optBoolean("success") == false) {
                throw ApiException(parseErrorMessage(json, response.code, response.message))
            }
        }
    }

    suspend fun fetchClipboard(
        serverUrl: String,
        apiToken: String
    ): ClipboardPayload = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(buildEndpoint(serverUrl, GET_CLIPBOARD_PATH))
            .header(HEADER_API_TOKEN, apiToken)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = body.toJsonObject()
            if (!response.isSuccessful || json?.optBoolean("success") == false || json == null) {
                throw ApiException(parseErrorMessage(json, response.code, response.message))
            }

            ClipboardPayload(
                contentType = json.optString("content_type"),
                content = json.optString("content"),
                mimeType = json.optString("mime_type").ifBlank { null },
                updatedAt = json.optString("updated_at").ifBlank { null }
            )
        }
    }

    private fun buildEndpoint(serverUrl: String, path: String): String {
        return serverUrl.trim().trimEnd('/') + path
    }

    private fun String.toJsonObject(): JSONObject? {
        if (isBlank()) {
            return null
        }
        return try {
            JSONObject(this)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseErrorMessage(json: JSONObject?, code: Int, fallback: String): String {
        val errorCode = json?.optString("error").orEmpty()
        val message = json?.optString("message").orEmpty()
        if (message.isNotBlank() && !message.contains("method_not_allowed", ignoreCase = true)) {
            return message
        }
        return when {
            errorCode == "missing_token" || errorCode == "invalid_token" -> "API Token 无效或缺失。"
            errorCode == "token_inactive" || errorCode == "token_expired" -> "API Token 已失效或被停用。"
            code == 401 || code == 403 -> "API Token 无效或已失效。"
            code == 405 -> "请求方式不正确。"
            code >= 500 -> "服务器响应异常，请稍后重试。"
            else -> fallback.ifBlank { "请求失败，请稍后重试。" }
        }
    }

    companion object {
        private const val HEADER_API_TOKEN = "X-Api-Token"
        private const val SET_CLIPBOARD_PATH = "/api_set_clipboard.php"
        private const val GET_CLIPBOARD_PATH = "/api_get_clipboard.php"
    }
}

data class ClipboardPayload(
    val contentType: String,
    val content: String,
    val mimeType: String? = null,
    val updatedAt: String? = null
)

class ApiException(message: String) : IOException(message)
