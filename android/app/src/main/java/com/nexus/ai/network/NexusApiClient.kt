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

    suspend fun callAiDirectly(routerUrl: String, modelName: String, apiKey: String, userPrompt: String, systemPrompt: String = "Kamu adalah NEXUS AI, personal Android AI Agent."): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val u = routerUrl.trim().trimEnd('/')
            val k = apiKey.trim()
            if (k.isEmpty()) throw IllegalArgumentException("API Key belum diisi di menu Settings / AI Providers.")
            if (u.contains("generativelanguage.googleapis.com") && !u.contains("/openai")) {
                var lastE = "Gemini Error"
                for (gm in linkedSetOf(modelName.trim(), "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")) {
                    if (gm.isBlank()) continue
                    val p = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "$systemPrompt\n\n$userPrompt")))))
                    val req = Request.Builder().url("$u/models/$gm:generateContent?key=$k").addHeader("Content-Type", "application/json").post(p.toString().toRequestBody(jsonMedia)).build()
                    client.newCall(req).execute().use { r ->
                        val b = r.body?.string() ?: ""
                        if (r.isSuccessful) return@runCatching JSONObject(b).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                        lastE = "HTTP ${r.code}: ${b.take(140)}"
                    }
                }
                throw IllegalStateException(lastE)
            } else {
                val url = if (u.endsWith("/chat/completions")) u else "$u/chat/completions"
                val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt)).put(JSONObject().put("role", "user").put("content", userPrompt))
                val p = JSONObject().put("model", modelName.trim()).put("messages", msgs)
                val req = Request.Builder().url(url).addHeader("Authorization", "Bearer $k").addHeader("Content-Type", "application/json").post(p.toString().toRequestBody(jsonMedia)).build()
                client.newCall(req).execute().use { r ->
                    val b = r.body?.string() ?: ""
                    if (!r.isSuccessful) throw IllegalStateException("HTTP ${r.code}: ${b.take(140)}")
                    JSONObject(b).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                }
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
