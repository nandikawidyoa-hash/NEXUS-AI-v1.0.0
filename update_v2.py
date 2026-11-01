from pathlib import Path

def w(rel, txt):
    p = Path(rel)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(txt.strip() + "\n", encoding="utf-8")

# 1. Bump version ke 2.0.0 & izinkan semua protokol HTTP/HTTPS Custom Router
w("android/app/build.gradle.kts", """plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.nexus.ai"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.nexus.ai"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0"
        vectorDrawables { useSupportLibrary = true }
    }
    buildTypes {
        release { isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
        debug { applicationIdSuffix = ".debug"; isDebuggable = true }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17"; freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api") }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    lint { abortOnError = false; checkReleaseBuilds = false }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
}""")

w("android/app/src/main/res/xml/network_security_config.xml", """<?xml version="1.0" encoding="utf-8"?>
<network-security-config><base-config cleartextTrafficPermitted="true" /></network-security-config>""")

# 2. Custom Cyber-Droid Adaptive Icon & AndroidManifest.xml
w("android/app/src/main/res/drawable/ic_launcher_background.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
<path android:fillColor="#090D16" android:pathData="M0,0h108v108h-108z"/>
<path android:fillColor="#111827" android:pathData="M54,54m-42,0a42,42 0,1 1,84 0a42,42 0,1 1,-84 0"/>
<path android:fillColor="#00000000" android:strokeColor="#2563EB" android:strokeWidth="2.2" android:pathData="M54,54m-36,0a36,36 0,1 1,72 0a36,36 0,1 1,-72 0"/>
</vector>""")

w("android/app/src/main/res/drawable/ic_launcher_foreground.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
<path android:fillColor="#00000000" android:strokeColor="#3B82F6" android:strokeWidth="2.2" android:pathData="M54,56m-28,0a28,28 0,1 1,56 0a28,28 0,1 1,-56 0"/>
<path android:fillColor="#00000000" android:strokeColor="#60A5FA" android:strokeWidth="3.2" android:strokeLineCap="round" android:pathData="M54,36 L54,26"/>
<path android:fillColor="#10B981" android:pathData="M54,24m-4,0a4,4 0,1 1,8 0a4,4 0,1 1,-8 0"/>
<path android:fillColor="#1E293B" android:strokeColor="#60A5FA" android:strokeWidth="2.8" android:pathData="M38,36 L70,36 A8,8 0 0,1 78,44 L78,66 A8,8 0 0,1 70,74 L38,74 A8,8 0 0,1 30,66 L30,44 A8,8 0 0,1 38,36 Z"/>
<path android:fillColor="#090D16" android:pathData="M39,44 L69,44 A4,4 0 0,1 73,48 L73,57 A4,4 0 0,1 69,61 L39,61 A4,4 0 0,1 35,57 L35,48 A4,4 0 0,1 39,44 Z"/>
<path android:fillColor="#60A5FA" android:pathData="M46,52.5m-4,0a4,4 0,1 1,8 0a4,4 0,1 1,-8 0"/>
<path android:fillColor="#60A5FA" android:pathData="M62,52.5m-4,0a4,4 0,1 1,8 0a4,4 0,1 1,-8 0"/>
<path android:fillColor="#10B981" android:pathData="M45,66 L63,66 A2,2 0 0,1 65,68 L65,69 A2,2 0 0,1 63,71 L45,71 A2,2 0 0,1 43,69 L43,68 A2,2 0 0,1 45,66 Z"/>
</vector>""")

adap = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
<background android:drawable="@drawable/ic_launcher_background"/><foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>"""
w("android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml", adap)
w("android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml", adap)

w("android/app/src/main/AndroidManifest.xml", """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <application android:allowBackup="false" android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name" android:usesCleartextTraffic="true" android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true" android:theme="@style/Theme.NexusAI">
        <activity android:name=".MainActivity" android:exported="true" android:windowSoftInputMode="adjustResize">
            <intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter>
        </activity>
        <service android:name=".services.NexusForegroundService" android:enabled="true" android:exported="false" android:foregroundServiceType="dataSync" />
    </application>
</manifest>""")

# 3. TokenPreferences.kt — Simpan Custom Router, Model ID, dan API Key langsung di HP
w("android/app/src/main/java/com/nexus/ai/data/TokenPreferences.kt", """package com.nexus.ai.data
import android.content.Context
import android.content.SharedPreferences
class TokenPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nexus_prefs_v2", Context.MODE_PRIVATE)
    var localEndpoint: String get() = prefs.getString("local_endpoint", "http://127.0.0.1:8765") ?: "http://127.0.0.1:8765"
        set(v) = prefs.edit().putString("local_endpoint", v.trim().trimEnd('/')).apply()
    var apiToken: String get() = prefs.getString("api_token", "nexus-local-dev-token-2026") ?: "nexus-local-dev-token-2026"
        set(v) = prefs.edit().putString("api_token", v.trim()).apply()
    var activeProviderId: String get() = prefs.getString("prov_id", "prov-gemini") ?: "prov-gemini"
        set(v) = prefs.edit().putString("prov_id", v.trim()).apply()
    var activeProviderName: String get() = prefs.getString("prov_name", "Google Gemini") ?: "Google Gemini"
        set(v) = prefs.edit().putString("prov_name", v.trim()).apply()
    var customRouterUrl: String get() = prefs.getString("router_url", "https://generativelanguage.googleapis.com/v1beta") ?: "https://generativelanguage.googleapis.com/v1beta"
        set(v) = prefs.edit().putString("router_url", v.trim().trimEnd('/')).apply()
    var activeModelName: String get() = prefs.getString("model_name", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(v) = prefs.edit().putString("model_name", v.trim()).apply()
    var providerApiKey: String get() = prefs.getString("prov_key", "") ?: ""
        set(v) = prefs.edit().putString("prov_key", v.trim()).apply()
    var cloudEndpoint: String get() = prefs.getString("cloud_endpoint", "") ?: ""
        set(v) = prefs.edit().putString("cloud_endpoint", v.trimEnd('/')).apply()
    var allowedSafUri: String get() = prefs.getString("allowed_saf_uri", "") ?: ""
        set(v) = prefs.edit().putString("allowed_saf_uri", v).apply()
    fun maskedApiKey(): String {
        val k = providerApiKey.trim()
        return if (k.isEmpty()) "Belum diatur" else if (k.length <= 8) "****" + k.takeLast(2) else k.take(4) + "..." + k.takeLast(4)
    }
}""")

# 4. NexusApiClient.kt — Koneksi langsung dari Android ke Gemini & Semua Custom Router OpenAI-Compatible
api_p = Path("android/app/src/main/java/com/nexus/ai/network/NexusApiClient.kt")
api_t = api_p.read_text(encoding="utf-8")
direct_fn = """    suspend fun callAiDirectly(routerUrl: String, modelName: String, apiKey: String, userPrompt: String, systemPrompt: String = "Kamu adalah NEXUS AI, personal Android AI Agent."): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val u = routerUrl.trim().trimEnd('/')
            val k = apiKey.trim()
            if (k.isEmpty()) throw IllegalArgumentException("API Key belum diisi di menu Settings / AI Providers.")
            if (u.contains("generativelanguage.googleapis.com") && !u.contains("/openai")) {
                var lastE = "Gemini Error"
                for (gm in linkedSetOf(modelName.trim(), "gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")) {
                    if (gm.isBlank()) continue
                    val p = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "$systemPrompt\\n\\n$userPrompt")))))
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

    fun connectWebSocket"""
if "fun callAiDirectly" not in api_t:
    api_p.write_text(api_t.replace("    fun connectWebSocket", direct_fn), encoding="utf-8")

# 5. CommonComponents.kt — Karakter Animasi NEXUS Cyber-Droid
w("android/app/src/main/java/com/nexus/ai/components/CommonComponents.kt", """package com.nexus.ai.components
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.nexus.ai.theme.AccentBlueLight
import com.nexus.ai.theme.AmberWarn
import com.nexus.ai.theme.CrimsonDanger
import com.nexus.ai.theme.EmeraldOk

@Composable
fun NexusCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun RiskBadge(riskLevel: String) {
    val c = when (riskLevel.lowercase()) { "critical", "high" -> CrimsonDanger; "medium" -> AmberWarn; else -> EmeraldOk }
    Box(modifier = Modifier.background(c.copy(alpha = 0.18f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text = riskLevel.uppercase(), style = MaterialTheme.typography.labelSmall, color = c)
    }
}

@Composable
fun NexusAgentCharacterCard(isWorking: Boolean, currentPhaseTitle: String, activeModelName: String) {
    val inf = rememberInfiniteTransition(label = "droid")
    val dy by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(if (isWorking) 500 else 1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy")
    val ang by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if (isWorking) 1600 else 5500, easing = LinearEasing), RepeatMode.Restart), label = "ang")
    val ex by inf.animateFloat(-7f, 7f, infiniteRepeatable(tween(if (isWorking) 420 else 2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ex")
    val pCol = if (isWorking) AccentBlueLight else EmeraldOk
    val sCol = if (isWorking) AmberWarn else AccentBlueLight
    NexusCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f; val cy = (size.height / 2f) + dy
                    drawCircle(pCol.copy(alpha = if (isWorking) 0.22f else 0.1f), size.minDimension / 2.1f, Offset(cx, cy))
                    rotate(ang, Offset(cx, cy)) {
                        val r = size.minDimension / 2.3f
                        drawCircle(pCol.copy(alpha = 0.7f), r, Offset(cx, cy), style = Stroke(if (isWorking) 4f else 2f))
                        drawCircle(sCol, if (isWorking) 7f else 4f, Offset(cx + r, cy))
                        drawCircle(pCol, if (isWorking) 6f else 3f, Offset(cx - r, cy))
                    }
                    drawLine(pCol, Offset(cx, cy - 24f), Offset(cx, cy - 36f), strokeWidth = 4f)
                    drawCircle(sCol, if (isWorking) 6.5f else 4.5f, Offset(cx, cy - 38f))
                    drawRoundRect(Color(0xFF1E293B), Offset(cx - 25f, cy - 22f), Size(50f, 44f), CornerRadius(14f, 14f))
                    drawRoundRect(pCol, Offset(cx - 25f, cy - 22f), Size(50f, 44f), CornerRadius(14f, 14f), style = Stroke(3f))
                    drawRoundRect(Color(0xFF090D16), Offset(cx - 19f, cy - 14f), Size(38f, 17f), CornerRadius(7f, 7f))
                    val eo = if (isWorking) ex else ex * 0.25f
                    drawCircle(pCol, 4.2f, Offset(cx - 8f + eo, cy - 5.5f))
                    drawCircle(pCol, 4.2f, Offset(cx + 8f + eo, cy - 5.5f))
                    drawRoundRect(sCol, Offset(cx - 11f, cy + 9f), Size(22f, 5f), CornerRadius(3f, 3f))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isWorking) "NEXUS Cyber-Droid Sedang Bekerja..." else "NEXUS Cyber-Droid Standby", style = MaterialTheme.typography.titleMedium, color = pCol)
                Spacer(Modifier.height(3.dp))
                Text(currentPhaseTitle, style = MaterialTheme.typography.bodyMedium)
                Text("Model: $activeModelName", style = MaterialTheme.typography.labelSmall)
                if (isWorking) { Spacer(Modifier.height(5.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = pCol) }
            }
        }
    }
}

@Composable
fun EmergencyStopBanner(agentStopped: Boolean, onStopClick: () -> Unit, onResumeClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(if (agentStopped) CrimsonDanger.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(if (agentStopped) "AGENT DIHENTIKAN" else "Kendali Keamanan Agent Aktif", style = MaterialTheme.typography.titleMedium)
            Text(if (agentStopped) "Seluruh tugas ditangguhkan." else "Mode Semi-Autonomous berjalan normal.", style = MaterialTheme.typography.bodyMedium)
        }
        if (agentStopped) Button(onClick = onResumeClick) { Text("Aktifkan") }
        else Button(onClick = onStopClick, colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)) { Text("Stop Agent", color = Color.White) }
    }
}""")

# 6. NexusViewModel.kt — Direct AI Execution + Animasi Fase 8-Step
vm_p = Path("android/app/src/main/java/com/nexus/ai/data/NexusViewModel.kt")
vm_old = vm_p.read_text(encoding="utf-8")
vm_tail = vm_old[vm_old.index("    fun refreshSkillsAndTools()"):]
vm_head = """package com.nexus.ai.data
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.ai.network.NexusApiClient
import com.nexus.ai.services.DeviceBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class NexusViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = TokenPreferences(application)
    private val api = NexusApiClient(getBaseUrl = { prefs.localEndpoint }, getToken = { prefs.apiToken })

    var engineOnline by mutableStateOf(false)
    var wsConnected by mutableStateOf(false)
    var agentStopped by mutableStateOf(false)
    var activeRuntime by mutableStateOf("local")
    var cloudEnabled by mutableStateOf(false)
    var cloudUrl by mutableStateOf("")
    var autoModelRouting by mutableStateOf(false)
    var streamingBuffer by mutableStateOf("")
    var statusBanner by mutableStateOf("Mode Hybrid Aktif (Direct AI + Local Engine)")
    var darkThemeEnabled by mutableStateOf(true)

    var agentBusy by mutableStateOf(false)
    var agentPhaseText by mutableStateOf("Siap menerima perintah tugas atau percakapan.")

    var activeProviderIdState by mutableStateOf(prefs.activeProviderId)
    var providerNameState by mutableStateOf(prefs.activeProviderName)
    var routerUrlState by mutableStateOf(prefs.customRouterUrl)
    var modelNameState by mutableStateOf(prefs.activeModelName)
    var maskedKeyState by mutableStateOf(prefs.maskedApiKey())
    var configStatusMessage by mutableStateOf("Pilih preset atau ketik Custom Router URL, Model ID, dan API Key di bawah.")

    val chatMessages = mutableStateListOf<ChatMessageModel>()
    val tasks = mutableStateListOf<TaskItemModel>()
    val providers = mutableStateListOf<ProviderItemModel>()
    val models = mutableStateListOf<AiModelItem>()
    val skills = mutableStateListOf<SkillItemModel>()
    val tools = mutableStateListOf<ToolItemModel>()
    val memories = mutableStateListOf<MemoryItemModel>()
    val automations = mutableStateListOf<AutomationItemModel>()
    val approvals = mutableStateListOf<ApprovalItemModel>()
    val logs = mutableStateListOf<ActivityLogModel>()
    val workspaceFiles = mutableStateListOf<WorkspaceFileItem>()

    init { seedDefaults(); refreshAll(); connectRealtimeSocket() }

    private fun seedDefaults() {
        if (providers.isEmpty()) {
            providers.addAll(listOf(
                ProviderItemModel("prov-gemini", "Google Gemini", "gemini", "https://generativelanguage.googleapis.com/v1beta", maskedKeyState, true, 1, "ready"),
                ProviderItemModel("prov-custom", "Custom Router / OpenRouter", "custom", "https://openrouter.ai/api/v1", maskedKeyState, true, 2, "ready"),
                ProviderItemModel("prov-deepseek", "DeepSeek API", "deepseek", "https://api.deepseek.com/v1", maskedKeyState, true, 3, "ready"),
                ProviderItemModel("prov-openai", "OpenAI API", "openai", "https://api.openai.com/v1", maskedKeyState, true, 4, "ready"),
                ProviderItemModel("prov-qwen", "Alibaba Qwen", "qwen", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", maskedKeyState, true, 5, "ready")
            ))
        }
        if (models.isEmpty()) {
            models.addAll(listOf(
                AiModelItem("mod-gemini-2.5-flash", "prov-gemini", "Google Gemini", "gemini-2.5-flash", "Gemini 2.5 Flash", modelNameState == "gemini-2.5-flash", true),
                AiModelItem("mod-gemini-2.0-flash", "prov-gemini", "Google Gemini", "gemini-2.0-flash", "Gemini 2.0 Flash", modelNameState == "gemini-2.0-flash", true),
                AiModelItem("mod-deepseek-chat", "prov-deepseek", "DeepSeek API", "deepseek-chat", "DeepSeek V3 Chat", modelNameState == "deepseek-chat", true),
                AiModelItem("mod-deepseek-reasoner", "prov-deepseek", "DeepSeek API", "deepseek-reasoner", "DeepSeek R1 Reasoner", modelNameState == "deepseek-reasoner", true),
                AiModelItem("mod-gpt-4o-mini", "prov-openai", "OpenAI API", "gpt-4o-mini", "GPT-4o Mini", modelNameState == "gpt-4o-mini", true),
                AiModelItem("mod-qwen-max", "prov-qwen", "Alibaba Qwen", "qwen-max", "Qwen Max", modelNameState == "qwen-max", true)
            ))
        }
    }

    fun saveAiRouterConfig(providerId: String, providerName: String, routerUrl: String, modelName: String, rawApiKey: String) {
        val u = routerUrl.trim().trimEnd('/').ifEmpty { "https://generativelanguage.googleapis.com/v1beta" }
        val m = modelName.trim().ifEmpty { "gemini-2.5-flash" }
        val k = rawApiKey.trim()
        prefs.activeProviderId = providerId; prefs.activeProviderName = providerName; prefs.customRouterUrl = u; prefs.activeModelName = m
        if (k.isNotEmpty()) prefs.providerApiKey = k
        activeProviderIdState = providerId; providerNameState = providerName; routerUrlState = u; modelNameState = m; maskedKeyState = prefs.maskedApiKey()
        for (i in models.indices) models[i] = models[i].copy(isDefault = (models[i].modelName == m))
        configStatusMessage = "BERHASIL DISIMPAN!\\nProvider: $providerName\\nRouter: $u\\nModel Aktif: $m\\nAPI Key: $maskedKeyState"
        viewModelScope.launch {
            api.requestJson("/providers/$providerId/key", "PUT", JSONObject().put("api_key", prefs.providerApiKey).put("base_url", u).put("enabled", true).put("model_name", m))
        }
    }

    fun testCurrentAiRouter(routerUrl: String = routerUrlState, modelName: String = modelNameState, rawApiKey: String = "") {
        if (rawApiKey.isNotBlank()) { prefs.providerApiKey = rawApiKey.trim(); maskedKeyState = prefs.maskedApiKey() }
        viewModelScope.launch {
            agentBusy = true
            agentPhaseText = "Menguji koneksi ke $modelName..."
            configStatusMessage = "Sedang menguji koneksi ke $routerUrl ($modelName)..."
            val res = api.callAiDirectly(routerUrl, modelName, prefs.providerApiKey, "Jawab singkat 1 kalimat: Koneksi NEXUS AI Berhasil!")
            agentBusy = false
            res.onSuccess { reply ->
                agentPhaseText = "Koneksi AI aktif ($modelName)."
                configStatusMessage = "KONEKSI BERHASIL!\\nModel: $modelName\\nBalasan AI: ${reply.trim()}"
            }.onFailure { err ->
                agentPhaseText = "Uji koneksi gagal. Cek API Key / Endpoint."
                configStatusMessage = "KONEKSI GAGAL:\\n${err.message}"
            }
        }
    }

    fun connectRealtimeSocket() {
        api.connectWebSocket(onEvent = { ev, data ->
            if (ev == "chat_stream") {
                streamingBuffer += data.optString("delta", "")
                if (data.optBoolean("done", false)) { streamingBuffer = ""; refreshTasks() }
            } else refreshAll()
        }, onStatusChange = { wsConnected = it })
    }

    fun refreshAll() {
        viewModelScope.launch {
            val h = api.requestJson("/health")
            engineOnline = h.isSuccess
            if (h.isSuccess) {
                agentStopped = (h.getOrNull() ?: JSONObject()).optBoolean("agent_stopped", false)
                statusBanner = "Hybrid Online (${prefs.localEndpoint}) • Model: $modelNameState"
                reportDeviceState(); refreshTasks(); refreshSkillsAndTools(); refreshMemories(); refreshAutomations(); refreshApprovalsAndLogs(); refreshWorkspaceFiles("."); refreshCloudStatus()
            } else {
                statusBanner = "Direct AI Router Aktif • Model: $modelNameState"
                seedDefaults()
            }
        }
    }

    private suspend fun reportDeviceState() {
        val b = DeviceBridge.readBatteryStatus(getApplication())
        api.requestJson("/device/report", "POST", JSONObject().put("battery_percent", b.percent).put("is_charging", b.isCharging).put("network_type", "local"))
    }

    fun sendChatCommand(messageText: String, runtime: String = activeRuntime) {
        if (messageText.isBlank()) return
        chatMessages.add(ChatMessageModel("u-${System.currentTimeMillis()}", "user", messageText))
        viewModelScope.launch {
            agentBusy = true
            for (p in listOf("Fase 1/8 (Understand): Menganalisis maksud perintah...", "Fase 2/8 (Plan): Menyusun rencana langkah kerja...", "Fase 4/8 (Select Model): Memanggil $modelNameState...", "Fase 5/8 (Execute): Menjalankan pemrosesan AI...")) {
                agentPhaseText = p; delay(200)
            }
            val directRes = api.callAiDirectly(routerUrlState, modelNameState, prefs.providerApiKey, messageText)
            agentBusy = false
            directRes.onSuccess { aiText ->
                agentPhaseText = "Fase 8/8 (Report): Selesai dijawab oleh $modelNameState"
                chatMessages.add(ChatMessageModel("a-${System.currentTimeMillis()}", "assistant", aiText, modelNameState))
                val steps = listOf(
                    TaskStepModel(1, "understand", "1. Understand", "Selesai", "completed", "low"),
                    TaskStepModel(2, "plan", "2. Plan", "Selesai", "completed", "low"),
                    TaskStepModel(3, "select_runtime", "3. Select Runtime", "Selesai", "completed", "low"),
                    TaskStepModel(4, "select_model", "4. Select Model ($modelNameState)", "Selesai", "completed", "low"),
                    TaskStepModel(5, "execute", "5. Execute", "Selesai", "completed", "low"),
                    TaskStepModel(6, "verify", "6. Verify", "Selesai", "completed", "low"),
                    TaskStepModel(7, "remember", "7. Remember", "Selesai", "completed", "low"),
                    TaskStepModel(8, "report", "8. Report", "Selesai", "completed", "low")
                )
                tasks.add(0, TaskItemModel("task-${System.currentTimeMillis()}", messageText.take(60), messageText, runtime, "completed", "low", modelNameState, aiText, null, null, "Baru saja", steps))
            }.onFailure { err ->
                agentPhaseText = "Gagal: Periksa API Key / Custom Router di Settings."
                chatMessages.add(ChatMessageModel("err-${System.currentTimeMillis()}", "assistant", "Gagal memanggil '$modelNameState' ($routerUrlState):\\n${err.message}\\n\\nBuka tab Settings untuk mengatur API Key, Custom Router URL, dan Model."))
            }
        }
    }

    fun stopAgentEmergency() { agentBusy = false; agentStopped = true; agentPhaseText = "AGENT DIHENTIKAN DARURAT."; viewModelScope.launch { api.requestJson("/agent/stop", "POST") } }
    fun resumeAgent() { agentStopped = false; agentPhaseText = "Agent aktif kembali."; viewModelScope.launch { api.requestJson("/agent/resume", "POST") } }
    fun cancelTask(taskId: String) { agentBusy = false; tasks.replaceAll { if (it.id == taskId) it.copy(status = "cancelled") else it } }
    fun refreshTasks() {}
    fun refreshProvidersAndModels() { seedDefaults() }
    fun selectModel(model: AiModelItem) {
        modelNameState = model.modelName; prefs.activeModelName = model.modelName
        providers.find { it.id == model.providerId }?.let { activeProviderIdState = it.id; providerNameState = it.name; routerUrlState = it.baseUrl; prefs.activeProviderId = it.id; prefs.activeProviderName = it.name; prefs.customRouterUrl = it.baseUrl }
        for (i in models.indices) models[i] = models[i].copy(isDefault = (models[i].id == model.id))
        configStatusMessage = "Model aktif diubah ke: ${model.displayName} (${model.modelName})"
    }
    fun toggleAutoRouting(enabled: Boolean) { autoModelRouting = enabled }
"""
vm_p.write_text(vm_head + "\n" + vm_tail, encoding="utf-8")

# 7. MainScreens.kt & SupportScreens.kt
ms_p = Path("android/app/src/main/java/com/nexus/ai/screens/MainScreens.kt")
ms_old = ms_p.read_text(encoding="utf-8")
ms_skills = ms_old[ms_old.index("@Composable\nfun SkillsScreen"):ms_old.index("@Composable\nfun SettingsScreen")]
ms_head = """@file:OptIn(ExperimentalMaterial3Api::class)
package com.nexus.ai.screens
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.ai.components.EmergencyStopBanner
import com.nexus.ai.components.NexusAgentCharacterCard
import com.nexus.ai.components.NexusCard
import com.nexus.ai.components.RiskBadge
import com.nexus.ai.data.NexusViewModel

@Composable
fun AiRouterConfigSection(vm: NexusViewModel) {
    var pid by remember(vm.activeProviderIdState) { mutableStateOf(vm.activeProviderIdState) }
    var pname by remember(vm.providerNameState) { mutableStateOf(vm.providerNameState) }
    var rUrl by remember(vm.routerUrlState) { mutableStateOf(vm.routerUrlState) }
    var mName by remember(vm.modelNameState) { mutableStateOf(vm.modelNameState) }
    var aKey by remember { mutableStateOf("") }
    val presets = listOf(
        Triple("prov-gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta" to "gemini-2.5-flash"),
        Triple("prov-custom", "Custom Router / OpenRouter", "https://openrouter.ai/api/v1" to "google/gemini-2.5-flash"),
        Triple("prov-deepseek", "DeepSeek API", "https://api.deepseek.com/v1" to "deepseek-chat"),
        Triple("prov-openai", "OpenAI API", "https://api.openai.com/v1" to "gpt-4o-mini"),
        Triple("prov-qwen", "Alibaba Qwen", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1" to "qwen-max")
    )
    val popModels = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-pro", "deepseek-chat", "deepseek-reasoner", "gpt-4o-mini", "qwen-max", "google/gemini-2.5-flash")
    NexusCard {
        Text("Pilih Model AI & Custom Router", style = MaterialTheme.typography.titleMedium)
        Text("Aktif: ${vm.providerNameState} • Model: ${vm.modelNameState}\\nRouter: ${vm.routerUrlState}\\nAPI Key: ${vm.maskedKeyState}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("1. Pilih Preset Provider / Custom Router:", style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (id, nm, pair) ->
                FilterChip(selected = pid == id, onClick = { pid = id; pname = nm; rUrl = pair.first; mName = pair.second }, label = { Text(nm) })
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = rUrl, onValueChange = { rUrl = it }, label = { Text("2. Custom Router / Endpoint URL (Bebas Edit)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = mName, onValueChange = { mName = it }, label = { Text("3. Nama Model / Model ID (Ketik / Pilih di Bawah)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            popModels.forEach { m -> FilterChip(selected = mName == m, onClick = { mName = m }, label = { Text(m) }) }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(value = aKey, onValueChange = { aKey = it }, label = { Text("4. API Key (Tersimpan: ${vm.maskedKeyState})") }, placeholder = { Text("Tempel API Key (AIza... / sk-...)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = { vm.saveAiRouterConfig(pid, pname, rUrl, mName, aKey); aKey = "" }) { Text("Simpan & Aktifkan") }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = { val k = aKey; vm.saveAiRouterConfig(pid, pname, rUrl, mName, k); aKey = ""; vm.testCurrentAiRouter(rUrl, mName, k) }) { Text("Uji Koneksi AI") }
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Text(vm.configStatusMessage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
fun HomeScreen(vm: NexusViewModel, onNavigate: (String) -> Unit) {
    val pending = vm.approvals.count { it.status == "pending" }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("NEXUS AI v2.0 • Cyber-Droid", style = MaterialTheme.typography.headlineMedium)
            Text(vm.statusBanner, style = MaterialTheme.typography.bodyMedium)
        }
        item { NexusAgentCharacterCard(vm.agentBusy, vm.agentPhaseText, "${vm.providerNameState} (${vm.modelNameState})") }
        item { EmergencyStopBanner(vm.agentStopped, { vm.stopAgentEmergency() }, { vm.resumeAgent() }) }
        item {
            NexusCard {
                Text("Model & Custom Router Aktif", style = MaterialTheme.typography.titleMedium)
                Text("Model: ${vm.modelNameState} (${vm.providerNameState})\\nRouter: ${vm.routerUrlState}\\nAPI Key: ${vm.maskedKeyState}")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onNavigate("providers") }) { Text("Atur Model & Router") }
                    OutlinedButton(onClick = { vm.testCurrentAiRouter() }) { Text("Tes Koneksi AI") }
                }
            }
        }
        item {
            NexusCard {
                Text("Pusat Kendali Pendukung", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(Modifier.weight(1f), onClick = { onNavigate("tools") }) { Text("Tools") }
                        OutlinedButton(Modifier.weight(1f), onClick = { onNavigate("memory") }) { Text("Memory") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(Modifier.weight(1f), onClick = { onNavigate("automations") }) { Text("Automation") }
                        OutlinedButton(Modifier.weight(1f), onClick = { onNavigate("device") }) { Text("Device Bridge") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(Modifier.weight(1f), onClick = { onNavigate("approvals") }) { Text("Approvals ($pending)") }
                        OutlinedButton(Modifier.weight(1f), onClick = { onNavigate("workspace") }) { Text("Workspace") }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: NexusViewModel) {
    var inputText by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Chat & Perintah Agent (${vm.modelNameState})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        NexusAgentCharacterCard(vm.agentBusy, vm.agentPhaseText, "${vm.providerNameState} (${vm.modelNameState})")
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.chatMessages) { msg ->
                NexusCard {
                    Text(if (msg.role == "user") "Anda" else "NEXUS AI (${msg.modelUsed ?: vm.modelNameState})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(msg.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ketik perintah atau pertanyaan...") }, maxLines = 3)
            Button(enabled = !vm.agentBusy, onClick = { val s = inputText; inputText = ""; vm.sendChatCommand(s) }) { Text(if (vm.agentBusy) "Kerja..." else "Kirim") }
        }
    }
}

@Composable
fun TasksScreen(vm: NexusViewModel) {
    var prompt by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Manajemen Tugas & 8-Step Workflow", style = MaterialTheme.typography.headlineMedium) }
        item { NexusAgentCharacterCard(vm.agentBusy, vm.agentPhaseText, "${vm.providerNameState} (${vm.modelNameState})") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.weight(1f), placeholder = { Text("Buat tugas bertahap baru...") })
                Button(enabled = !vm.agentBusy, onClick = { if (prompt.isNotBlank()) { vm.sendChatCommand(prompt); prompt = "" } }) { Text("Jalankan") }
            }
        }
        items(vm.tasks) { task ->
            NexusCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    RiskBadge(task.riskLevel)
                }
                Text("Status: ${task.status.uppercase()} • Model: ${task.modelId}")
                if (!task.resultSummary.isNullOrBlank() && task.resultSummary != "null") { Spacer(Modifier.height(6.dp)); Text(task.resultSummary, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(6.dp))
                task.steps.forEach { s -> Text("• ${s.title} [${s.status.uppercase()}]", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
"""

ms_tail = """@Composable
fun SettingsScreen(vm: NexusViewModel, onNavigate: (String) -> Unit) {
    var localEndpoint by remember { mutableStateOf(vm.prefs.localEndpoint) }
    var apiToken by remember { mutableStateOf(vm.prefs.apiToken) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Pengaturan & Custom AI Router", style = MaterialTheme.typography.headlineMedium) }
        item { AiRouterConfigSection(vm = vm) }
        item {
            NexusCard {
                Text("Tampilan & Tema", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode Gelap (Dark Mode)")
                    Switch(checked = vm.darkThemeEnabled, onCheckedChange = { vm.darkThemeEnabled = it })
                }
            }
        }
        item {
            NexusCard {
                Text("Koneksi Local Termux Engine", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = localEndpoint, onValueChange = { localEndpoint = it }, label = { Text("Alamat Local Runtime") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = apiToken, onValueChange = { apiToken = it }, label = { Text("Local Auth Token") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Button(onClick = { vm.prefs.localEndpoint = localEndpoint; vm.prefs.apiToken = apiToken; vm.refreshAll() }) { Text("Simpan Koneksi Termux") }
            }
        }
    }
}"""

ms_p.write_text(ms_head + "\n" + ms_skills + "\n" + ms_tail + "\n", encoding="utf-8")

ss_p = Path("android/app/src/main/java/com/nexus/ai/screens/SupportScreens.kt")
ss_old = ss_p.read_text(encoding="utf-8")
if not ss_old.startswith("@file:OptIn"):
    ss_old = "@file:OptIn(ExperimentalMaterial3Api::class)\n" + ss_old
if "import com.nexus.ai.components.NexusAgentCharacterCard" not in ss_old:
    ss_old = ss_old.replace("import com.nexus.ai.components.NexusCard", "import com.nexus.ai.components.NexusAgentCharacterCard\nimport com.nexus.ai.components.NexusCard")
ss_head = ss_old[:ss_old.index("@Composable\nfun ProviderManagerScreen")]
ss_tail = ss_old[ss_old.index("@Composable\nfun ActivityLogsScreen"):]
ss_prov = """@Composable
fun ProviderManagerScreen(vm: NexusViewModel, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SubScreenHeader("Multi-Provider & Custom Router", onBack) }
        item { NexusAgentCharacterCard(vm.agentBusy, vm.agentPhaseText, "${vm.providerNameState} (${vm.modelNameState})") }
        item { AiRouterConfigSection(vm = vm) }
        items(vm.models) { model ->
            val active = (vm.modelNameState == model.modelName)
            NexusCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${model.providerName} • ID: ${model.modelName}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (active) Button(onClick = {}) { Text("AKTIF") }
                    else OutlinedButton(onClick = { vm.selectModel(model) }) { Text("Pilih Model") }
                }
            }
        }
    }
}
"""
ss_p.write_text(ss_head + ss_prov + "\n" + ss_tail, encoding="utf-8")
print("UPDATE V2.0 BERHASIL DITULIS!")
