package com.nexus.ai.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NexusApiClient(
    private val getBaseUrl: () -> String,
    private val getToken: () -> String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private var webSocket: WebSocket? = null

    private fun buildRequest(path: String, method: String = "GET", bodyJson: JSONObject? = null): Request {
        val url = "${getBaseUrl().trimEnd('/')}$path"
        val builder = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${getToken()}")
            .addHeader("X-Nexus-Token", getToken())
        val reqBody = bodyJson?.toString()?.toRequestBody(jsonMedia)
        when (method) {
            "POST" -> builder.post(reqBody ?: "{}".toRequestBody(jsonMedia))
            "PUT" -> builder.put(reqBody ?: "{}".toRequestBody(jsonMedia))
            "DELETE" -> if (reqBody != null) builder.delete(reqBody) else builder.delete()
            else -> builder.get()
        }
        return builder.build()
    }

    suspend fun requestJson(path: String, method: String = "GET", bodyJson: JSONObject? = null): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = buildRequest(path, method, bodyJson)
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string() ?: "{}"
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("HTTP ${resp.code}: $raw")
                    }
                    JSONObject(raw)
                }
            }
        }

    fun connectWebSocket(onEvent: (String, JSONObject) -> Unit, onStatusChange: (Boolean) -> Unit) {
        webSocket?.cancel()
        val wsUrl = getBaseUrl()
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
            .trimEnd('/') + "/ws?token=${getToken()}"
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer ${getToken()}")
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatusChange(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val obj = JSONObject(text)
                    val ev = obj.optString("event", "unknown")
                    val data = obj.optJSONObject("data") ?: JSONObject()
                    onEvent(ev, data)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStatusChange(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStatusChange(false)
            }
        })
    }

    fun closeWebSocket() {
        webSocket?.close(1000, "App closed")
        webSocket = null
    }
}
