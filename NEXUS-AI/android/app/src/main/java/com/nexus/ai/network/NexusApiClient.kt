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
import java.io.File
import java.util.concurrent.TimeUnit

data class DirectVideoSubmitResult(
    val remoteJobId: String,
    val status: String,
    val videoUrl: String = ""
)

data class DirectVideoPollResult(
    val status: String,
    val progressPercent: Int,
    val videoUrl: String = "",
    val errorMessage: String = ""
)

class NexusApiClient(
    private val getBaseUrl: () -> String,
    private val getToken: () -> String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
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

    suspend fun callAiDirectly(
        routerUrl: String,
        modelName: String,
        apiKey: String,
        userPrompt: String,
        systemPrompt: String = "Kamu adalah NEXUS AI, personal Android AI Agent."
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val u = routerUrl.trim().trimEnd('/')
            val k = apiKey.trim()
            if (k.isEmpty()) {
                throw IllegalArgumentException("API Key belum diisi di menu Settings / AI Providers.")
            }
            if (u.contains("generativelanguage.googleapis.com") && !u.contains("/openai")) {
                var lastE = "Gemini Error"
                var finalOut: String? = null
                val candidates = listOf(modelName.trim(), "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash").distinct()
                for (gm in candidates) {
                    if (gm.isBlank() || finalOut != null) continue
                    val p = JSONObject().put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put("role", "user").put(
                                "parts",
                                JSONArray().put(JSONObject().put("text", "$systemPrompt\n\n$userPrompt"))
                            )
                        )
                    )
                    val req = Request.Builder()
                        .url("$u/models/$gm:generateContent?key=$k")
                        .addHeader("Content-Type", "application/json")
                        .post(p.toString().toRequestBody(jsonMedia))
                        .build()
                    client.newCall(req).execute().use { r ->
                        val b = r.body?.string() ?: ""
                        if (r.isSuccessful) {
                            finalOut = JSONObject(b)
                                .getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text")
                        } else {
                            lastE = "HTTP ${r.code}: ${b.take(140)}"
                        }
                    }
                }
                finalOut ?: throw IllegalStateException(lastE)
            } else {
                val url = if (u.endsWith("/chat/completions")) u else "$u/chat/completions"
                val msgs = JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt))
                val p = JSONObject().put("model", modelName.trim()).put("messages", msgs)
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $k")
                    .addHeader("Content-Type", "application/json")
                    .post(p.toString().toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { r ->
                    val b = r.body?.string() ?: ""
                    if (!r.isSuccessful) {
                        throw IllegalStateException("HTTP ${r.code}: ${b.take(140)}")
                    }
                    JSONObject(b)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            }
        }
    }

    suspend fun submitVideoJobDirectly(
        providerId: String,
        baseUrl: String,
        modelName: String,
        apiKey: String,
        prompt: String,
        mode: String,
        imageUrl: String,
        durationSec: Int,
        aspectRatio: String,
        resolution: String
    ): Result<DirectVideoSubmitResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanKey = apiKey.trim()
            if (cleanKey.isEmpty()) {
                throw IllegalArgumentException("API Key provider video belum diatur.")
            }
            val cleanUrl = baseUrl.trim().trimEnd('/')
            when (providerId) {
                "video-dashscope" -> {
                    val endpoint = "$cleanUrl/services/aigc/video-generation/video-synthesis"
                    val sizeStr = when (aspectRatio to resolution) {
                        "9:16" to "480p" -> "480*832"
                        "16:9" to "480p" -> "832*480"
                        "9:16" to "1080p" -> "1080*1920"
                        "16:9" to "1080p" -> "1920*1080"
                        "9:16" to "720p" -> "720*1280"
                        "1:1" to "720p" -> "960*960"
                        else -> "1280*720"
                    }
                    val inputObj = JSONObject().put("prompt", prompt)
                    if (mode == "image_to_video" && imageUrl.isNotBlank()) {
                        inputObj.put("img_url", imageUrl.trim())
                    }
                    val paramsObj = JSONObject()
                        .put("size", sizeStr)
                        .put("duration", durationSec)
                        .put("prompt_extend", true)
                    val payload = JSONObject()
                        .put("model", modelName)
                        .put("input", inputObj)
                        .put("parameters", paramsObj)
                    val req = Request.Builder()
                        .url(endpoint)
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .addHeader("X-DashScope-Async", "enable")
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(jsonMedia))
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string() ?: "{}"
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("DashScope HTTP ${resp.code}: ${raw.take(180)}")
                        }
                        val json = JSONObject(raw)
                        val taskId = json.optJSONObject("output")?.optString("task_id").orEmpty()
                        if (taskId.isBlank()) {
                            throw IllegalStateException("DashScope tidak mengembalikan task_id: ${raw.take(180)}")
                        }
                        DirectVideoSubmitResult(remoteJobId = taskId, status = "processing")
                    }
                }
                "video-replicate" -> {
                    val endpoint = if (modelName.contains("/")) {
                        "$cleanUrl/models/${modelName.trim()}/predictions"
                    } else {
                        "$cleanUrl/predictions"
                    }
                    val inputObj = JSONObject()
                        .put("prompt", prompt)
                        .put("aspect_ratio", aspectRatio)
                    if (mode == "image_to_video" && imageUrl.isNotBlank()) {
                        inputObj.put("first_frame_image", imageUrl.trim())
                    }
                    val payload = JSONObject().put("input", inputObj)
                    val req = Request.Builder()
                        .url(endpoint)
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(jsonMedia))
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string() ?: "{}"
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("Replicate HTTP ${resp.code}: ${raw.take(180)}")
                        }
                        val json = JSONObject(raw)
                        val predId = json.optString("id", "")
                        val outUrl = json.optString("output", "")
                        if (outUrl.startsWith("http")) {
                            DirectVideoSubmitResult(remoteJobId = predId, status = "succeeded", videoUrl = outUrl)
                        } else {
                            DirectVideoSubmitResult(remoteJobId = predId, status = "processing")
                        }
                    }
                }
                else -> {
                    // Default: SiliconFlow Wan 2.1 official endpoint
                    val endpoint = "$cleanUrl/video/submit"
                    val imgSize = when (aspectRatio) {
                        "9:16" -> "720x1280"
                        "1:1" -> "960x960"
                        else -> "1280x720"
                    }
                    val payload = JSONObject()
                        .put("model", modelName)
                        .put("prompt", prompt)
                        .put("image_size", imgSize)
                    if (mode == "image_to_video" && imageUrl.isNotBlank()) {
                        payload.put("image", imageUrl.trim())
                    }
                    val req = Request.Builder()
                        .url(endpoint)
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(jsonMedia))
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string() ?: "{}"
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("SiliconFlow HTTP ${resp.code}: ${raw.take(180)}")
                        }
                        val json = JSONObject(raw)
                        val reqId = json.optString("requestId").ifBlank { json.optString("id") }
                        if (reqId.isBlank()) {
                            throw IllegalStateException("SiliconFlow tidak mengembalikan requestId: ${raw.take(180)}")
                        }
                        DirectVideoSubmitResult(remoteJobId = reqId, status = "processing")
                    }
                }
            }
        }
    }

    suspend fun pollVideoJobDirectly(
        providerId: String,
        baseUrl: String,
        apiKey: String,
        remoteJobId: String
    ): Result<DirectVideoPollResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanKey = apiKey.trim()
            val cleanUrl = baseUrl.trim().trimEnd('/')
            when (providerId) {
                "video-dashscope" -> {
                    val req = Request.Builder()
                        .url("$cleanUrl/tasks/$remoteJobId")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string() ?: "{}"
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("DashScope Poll HTTP ${resp.code}: ${raw.take(150)}")
                        }
                        val out = JSONObject(raw).optJSONObject("output") ?: JSONObject()
                        val st = out.optString("task_status", "").uppercase()
                        when (st) {
                            "SUCCEEDED" -> DirectVideoPollResult(
                                status = "succeeded",
                                progressPercent = 100,
                                videoUrl = out.optString("video_url", "")
                            )
                            "FAILED", "CANCELED" -> DirectVideoPollResult(
                                status = "failed",
                                progressPercent = 0,
                                errorMessage = out.optString("message", "DashScope task $st")
                            )
                            else -> DirectVideoPollResult(status = "processing", progressPercent = 60)
                        }
                    }
                }
                "video-replicate" -> {
                    val req = Request.Builder()
                        .url("$cleanUrl/predictions/$remoteJobId")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string() ?: "{}"
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("Replicate Poll HTTP ${resp.code}: ${raw.take(150)}")
                        }
                        val json = JSONObject(raw)
                        val st = json.optString("status", "").lowercase()
                        when (st) {
                            "succeeded" -> {
                                val outAny = json.opt("output")
                                val vUrl = when (outAny) {
                                    is String -> outAny
                                    is JSONArray -> if (outAny.length() > 0) outAny.getString(0) else ""
                                    else -> ""
                                }
                                DirectVideoPollResult("succeeded", 100, vUrl)
                            }
                            "failed", "canceled" -> DirectVideoPollResult(
                                "failed",
                                0,
                                errorMessage = json.optString("error", "Replicate failed")
                            )
                            else -> DirectVideoPollResult("processing", 60)
                        }
                    }
                }
                else -> {
                    val payload = JSONObject().put("requestId", remoteJobId)
                    val req = Request.Builder()
                        .url("$cleanUrl/video/status")
                        .addHeader("Authorization", "Bearer $cleanKey")
                        .addHeader("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(jsonMedia))
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val raw = resp.body?.string() ?: "{}"
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("SiliconFlow Poll HTTP ${resp.code}: ${raw.take(150)}")
                        }
                        val json = JSONObject(raw)
                        val st = json.optString("status", "").lowercase()
                        when (st) {
                            "succeed" -> {
                                val vids = json.optJSONObject("results")?.optJSONArray("videos")
                                val vUrl = if (vids != null && vids.length() > 0) {
                                    vids.getJSONObject(0).optString("url", "")
                                } else ""
                                DirectVideoPollResult("succeeded", 100, vUrl)
                            }
                            "failed", "error" -> DirectVideoPollResult(
                                "failed",
                                0,
                                errorMessage = json.optString("reason", "SiliconFlow video failed")
                            )
                            else -> DirectVideoPollResult("processing", 55)
                        }
                    }
                }
            }
        }
    }

    suspend fun downloadFileToDevice(url: String, targetFile: File): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder().url(url.trim()).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Gagal mengunduh video: HTTP ${resp.code}")
                    }
                    val bytes = resp.body?.bytes() ?: throw IllegalStateException("Body video kosong.")
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeBytes(bytes)
                    targetFile.absolutePath
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
