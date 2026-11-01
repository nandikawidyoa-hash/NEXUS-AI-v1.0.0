import shutil
from pathlib import Path

def w(rel, txt):
    p = Path(rel)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(txt.strip() + "\n", encoding="utf-8")

# 1. Gunakan vector drawable standar di res/drawable/ic_nexus_launcher.xml (100% lolos AAPT2)
shutil.rmtree("android/app/src/main/res/mipmap-anydpi-v26", ignore_errors=True)
for old_f in ["ic_launcher_background.xml", "ic_launcher_foreground.xml"]:
    p = Path(f"android/app/src/main/res/drawable/{old_f}")
    if p.exists():
        p.unlink()

w("android/app/src/main/res/drawable/ic_nexus_launcher.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#090D16" android:pathData="M0,0h108v108h-108z"/>
    <path android:fillColor="#111827" android:pathData="M18,18 L90,18 L90,90 L18,90 Z"/>
    <path android:fillColor="#10B981" android:pathData="M51,22 L57,22 L57,34 L51,34 Z"/>
    <path android:fillColor="#1E293B" android:strokeColor="#60A5FA" android:strokeWidth="3" android:pathData="M32,34 L76,34 L76,74 L32,74 Z"/>
    <path android:fillColor="#090D16" android:pathData="M38,42 L70,42 L70,58 L38,58 Z"/>
    <path android:fillColor="#60A5FA" android:pathData="M43,47 L50,47 L50,54 L43,54 Z"/>
    <path android:fillColor="#60A5FA" android:pathData="M58,47 L65,47 L65,54 L58,54 Z"/>
    <path android:fillColor="#10B981" android:pathData="M44,64 L64,64 L64,69 L44,69 Z"/>
</vector>""")

w("android/app/src/main/AndroidManifest.xml", """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <application
        android:allowBackup="false"
        android:icon="@drawable/ic_nexus_launcher"
        android:roundIcon="@drawable/ic_nexus_launcher"
        android:label="@string/app_name"
        android:usesCleartextTraffic="true"
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true"
        android:theme="@style/Theme.NexusAI">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service
            android:name=".services.NexusForegroundService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="dataSync" />
    </application>
</manifest>""")

# 2. Perbaiki NexusApiClient.kt tanpa non-local return@runCatching
w("android/app/src/main/java/com/nexus/ai/network/NexusApiClient.kt", """package com.nexus.ai.network

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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
                                JSONArray().put(JSONObject().put("text", "$systemPrompt\\n\\n$userPrompt"))
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
}""")

# 3. Pastikan non-null String pada MainScreens.kt dan pure Kotlin loop pada NexusViewModel.kt
vm_p = Path("android/app/src/main/java/com/nexus/ai/data/NexusViewModel.kt")
vm_t = vm_p.read_text(encoding="utf-8")
vm_t = vm_t.replace(
    'tasks.replaceAll { if (it.id == taskId) it.copy(status = "cancelled") else it }',
    'for (i in tasks.indices) { if (tasks[i].id == taskId) tasks[i] = tasks[i].copy(status = "cancelled") }'
)
vm_p.write_text(vm_t, encoding="utf-8")

ms_p = Path("android/app/src/main/java/com/nexus/ai/screens/MainScreens.kt")
ms_t = ms_p.read_text(encoding="utf-8")
ms_t = ms_t.replace(
    'Text(task.resultSummary, style = MaterialTheme.typography.bodyMedium)',
    'Text(task.resultSummary.orEmpty(), style = MaterialTheme.typography.bodyMedium)'
)
ms_p.write_text(ms_t, encoding="utf-8")

print("PERBAIKAN FINAL SELESAI!")
