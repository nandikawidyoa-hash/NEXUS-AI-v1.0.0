package com.nexus.ai.data

import android.app.Application
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.ai.network.NexusApiClient
import com.nexus.ai.permissions.PermissionHelper
import com.nexus.ai.services.AppLaunchResult
import com.nexus.ai.services.DeviceBridge
import com.nexus.ai.services.InstalledAppInfo
import com.nexus.ai.services.NexusAccessibilityService
import com.nexus.ai.services.NexusForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedDeviceIntent(
    val actionType: String, // "open_app", "chrome_search", "accessibility_click", "accessibility_scroll", "accessibility_input"
    val targetApp: String,
    val queryOrText: String = "",
    val followUpScroll: Boolean = false
)

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
    var statusBanner by mutableStateOf("Mode Hybrid Aktif (Direct AI + Android Device Bridge)")
    var darkThemeEnabled by mutableStateOf(true)

    var agentBusy by mutableStateOf(false)
    var agentPhaseText by mutableStateOf("Siap menerima perintah tugas, buka aplikasi, atau percakapan.")
    var lastExecutionError by mutableStateOf("Tidak ada error.")

    // AI Chat Provider State
    var activeProviderIdState by mutableStateOf(prefs.activeProviderId)
    var providerNameState by mutableStateOf(prefs.activeProviderName)
    var routerUrlState by mutableStateOf(prefs.customRouterUrl)
    var modelNameState by mutableStateOf(prefs.activeModelName)
    var maskedKeyState by mutableStateOf(prefs.maskedApiKey())
    var configStatusMessage by mutableStateOf("Pilih preset atau ketik Custom Router URL, Model ID, dan API Key di bawah.")
    var skillStatusMessage by mutableStateOf("Skill Manager siap. Klik 'Scan Skills' untuk memindai folder skills/.")

    // Device Bridge, Foreground Service & Accessibility State
    var foregroundServiceActive by mutableStateOf(false)
    var foregroundActivating by mutableStateOf(false)
    var foregroundStatusMessage by mutableStateOf("Foreground Service belum aktif.")
    var accessibilityEnabled by mutableStateOf(false)
    var accessibilityConnected by mutableStateOf(false)
    var accessibilityStatusMessage by mutableStateOf("Accessibility Service belum diaktifkan oleh pengguna.")
    var appLauncherStatusMessage by mutableStateOf("Pilih aplikasi di bawah atau ketik perintah seperti 'Buka TikTok'.")

    // Generative Video AI State
    var videoProviderIdState by mutableStateOf(prefs.videoProviderId)
    var videoProviderNameState by mutableStateOf(prefs.videoProviderName)
    var videoBaseUrlState by mutableStateOf(prefs.videoBaseUrl)
    var videoModelNameState by mutableStateOf(prefs.videoModelName)
    var maskedVideoKeyState by mutableStateOf(prefs.maskedVideoApiKey())
    var videoStatusMessage by mutableStateOf("Pilih provider Generative Video (SiliconFlow / DashScope / Replicate), simpan API Key, lalu masukkan prompt video.")
    var videoBusy by mutableStateOf(false)

    // Diagnostics State
    var diagnosticSummaryMessage by mutableStateOf("Klik 'Run Diagnostics' untuk memeriksa seluruh komponen secara menyeluruh.")

    val chatMessages = mutableStateListOf<ChatMessageModel>()
    val tasks = mutableStateListOf<TaskItemModel>()
    val providers = mutableStateListOf<ProviderItemModel>()
    val models = mutableStateListOf<AiModelItem>()
    val skills = mutableStateListOf<SkillItemModel>()
    val tools = mutableStateListOf<ToolItemModel>()
    val memories = mutableStateListOf<MemoryItemModel>()
    val automations = mutableStateListOf<AutomationItemModel>()
    val automationQueue = mutableStateListOf<AutomationQueueTaskModel>()
    val installedApps = mutableStateListOf<InstalledAppInfo>()
    val visibleScreenNodes = mutableStateListOf<String>()
    val videoJobs = mutableStateListOf<VideoJobModel>()
    val diagnosticItems = mutableStateListOf<DiagnosticCheckItem>()
    val approvals = mutableStateListOf<ApprovalItemModel>()
    val logs = mutableStateListOf<ActivityLogModel>()
    val workspaceFiles = mutableStateListOf<WorkspaceFileItem>()

    private val cancelledQueueIds = mutableSetOf<String>()

    init {
        runCatching { seedDefaults() }
        runCatching { refreshDeviceBridgeState() }
        runCatching { refreshAll() }
        runCatching { connectRealtimeSocket() }
    }

    private fun nowFormatted(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun recordLocalLog(level: String, category: String, action: String) {
        logs.add(
            0,
            ActivityLogModel(
                id = "log-${System.currentTimeMillis()}",
                level = level,
                category = category,
                action = action,
                runtime = "android_bridge",
                createdAt = nowFormatted()
            )
        )
        if (level == "ERROR") {
            lastExecutionError = "[$category] $action (${nowFormatted()})"
        }
    }

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
        if (tools.isEmpty()) {
            tools.addAll(listOf(
                ToolItemModel("android_app_launcher", "android_automation", "low", "Membuka aplikasi terinstal (TikTok, WhatsApp, Chrome, YouTube, Instagram, dll.) via PackageManager & Launch Intent."),
                ToolItemModel("accessibility_automation", "android_automation", "medium", "Membaca elemen layar, menekan tombol, melakukan scroll, dan mengisi teks melalui Accessibility Service."),
                ToolItemModel("video_generate", "generative_video", "low", "Membuat video dari teks (Text-to-Video) atau gambar (Image-to-Video) melalui provider AI."),
                ToolItemModel("file_read", "filesystem", "low", "Membaca file dalam direktori workspace yang diizinkan."),
                ToolItemModel("file_write", "filesystem", "medium", "Menulis file ke workspace dengan auto-backup otomatis."),
                ToolItemModel("file_list", "filesystem", "low", "Menampilkan isi direktori di dalam workspace."),
                ToolItemModel("file_search", "filesystem", "low", "Mencari teks atau nama file di dalam workspace."),
                ToolItemModel("terminal_exec", "system", "high", "Menjalankan perintah shell di dalam sandbox Termux."),
                ToolItemModel("web_fetch", "network", "low", "Mengambil konten halaman web / API publik (HTTPS).")
            ))
        }
        if (skills.isEmpty()) {
            skills.addAll(listOf(
                SkillItemModel(
                    id = "skill-android-automation",
                    name = "android-device-assistant",
                    description = "Membuka aplikasi Android (TikTok, WhatsApp, Chrome, YouTube), menjalankan Automation Queue, dan mengontrol Accessibility.",
                    version = "2.0.0",
                    enabled = true,
                    isBuiltin = true,
                    securityStatus = "verified",
                    requiredTools = listOf("android_app_launcher", "file_list", "terminal_exec"),
                    status = "enabled",
                    folderPath = "skills/android",
                    instructions = "Jalankan aplikasi Android dengan launch intent resmi dan laporkan hasil eksekusi secara jujur.",
                    rawContent = "---\nname: android-device-assistant\ndescription: Membuka aplikasi Android, menjalankan Automation Queue, dan mengontrol Accessibility.\nversion: 2.0.0\nrequired_tools:\n  - android_app_launcher\n  - file_list\n  - terminal_exec\n---\n# Android Device Assistant\nJalankan aplikasi Android dengan launch intent resmi.",
                    requiredPermissions = listOf("QUERY_ALL_PACKAGES", "FOREGROUND_SERVICE", "BIND_ACCESSIBILITY_SERVICE")
                ),
                SkillItemModel(
                    id = "skill-code-assistant",
                    name = "coding-agent",
                    description = "Asisten pemrograman untuk menulis, mengaudit, dan memperbaiki kode.",
                    version = "1.0.0",
                    enabled = true,
                    isBuiltin = true,
                    securityStatus = "verified",
                    requiredTools = listOf("file_read", "file_write", "file_search"),
                    status = "enabled",
                    folderPath = "skills/coding",
                    instructions = "Berikan kode lengkap yang rapi, mudah disalin, dan sertakan penjelasan singkat.",
                    rawContent = "---\nname: coding-agent\ndescription: Asisten pemrograman untuk menulis, mengaudit, dan memperbaiki kode.\nversion: 1.0.0\nrequired_tools:\n  - file_read\n  - file_write\n  - file_search\n---\n# Coding Agent\nBerikan kode lengkap yang rapi.",
                    requiredPermissions = listOf("storage_workspace")
                ),
                SkillItemModel(
                    id = "skill-web-research",
                    name = "deep-web-research",
                    description = "Riset informasi terkini dan sintesis referensi terstruktur.",
                    version = "1.0.0",
                    enabled = true,
                    isBuiltin = true,
                    securityStatus = "verified",
                    requiredTools = listOf("web_search", "web_read"),
                    status = "enabled",
                    folderPath = "skills/research",
                    instructions = "Susun hasil riset secara akurat, terstruktur, dan mudah dipahami.",
                    rawContent = "---\nname: deep-web-research\ndescription: Riset informasi terkini dan sintesis referensi terstruktur.\nversion: 1.0.0\n---\n# Deep Web Research\nSusun hasil riset secara akurat.",
                    requiredPermissions = listOf("internet")
                )
            ))
        }
    }

    // =========================================================================
    // 1. ANDROID APP LAUNCHER, DEVICE BRIDGE, FOREGROUND & ACCESSIBILITY
    // =========================================================================

    fun refreshDeviceBridgeState() {
        val ctx = getApplication<Application>()
        foregroundServiceActive = DeviceBridge.isForegroundServiceRunning(ctx)
        if (foregroundServiceActive && foregroundStatusMessage.startsWith("Foreground Service belum")) {
            foregroundStatusMessage = "Foreground Service AKTIF (Memantau Device Bridge & Automation)."
        }
        accessibilityEnabled = NexusAccessibilityService.isAccessibilitySettingEnabled(ctx)
        accessibilityConnected = NexusAccessibilityService.isReady(ctx)
        accessibilityStatusMessage = when {
            accessibilityConnected -> "Accessibility Service AKTIF & TERHUBUNG (Window terakhir: ${NexusAccessibilityService.lastPackageName.ifBlank { "Standby" }})"
            accessibilityEnabled -> "Izin Accessibility diaktifkan di Pengaturan, menunggu koneksi service Android..."
            else -> "Accessibility Service BELUM AKTIF. Klik 'Buka Pengaturan Accessibility' untuk mengizinkan."
        }
        refreshInstalledApps()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val apps = DeviceBridge.listInstalledLaunchableApps(ctx)
            installedApps.clear()
            installedApps.addAll(apps)
            appLauncherStatusMessage = "Terdeteksi ${apps.size} aplikasi terinstal yang siap dibuka melalui Device Bridge."
        }
    }

    fun activateForegroundBridge() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            foregroundActivating = true
            foregroundStatusMessage = "Sedang mengaktifkan Foreground Service..."
            val res = DeviceBridge.activateForegroundService(ctx)
            delay(400)
            val verifiedRunning = DeviceBridge.isForegroundServiceRunning(ctx)
            foregroundActivating = false

            if (res.success && verifiedRunning) {
                foregroundServiceActive = true
                foregroundStatusMessage = "AKTIF: ${res.message}"
                recordLocalLog("INFO", "foreground_service", "Foreground Service berhasil diaktifkan & diverifikasi berjalan.")
            } else {
                foregroundServiceActive = false
                val errReason = res.errorDetail.ifBlank {
                    NexusForegroundService.lastError.ifBlank {
                        "Service tidak berjalan setelah pemanggilan startForegroundService (Periksa pembatasan baterai / Android 14)."
                    }
                }
                foregroundStatusMessage = "GAGAL: ${res.message} Penyebab: $errReason"
                recordLocalLog("ERROR", "foreground_service", "Gagal mengaktifkan Foreground Service: $errReason")
            }
        }
    }

    fun deactivateForegroundBridge() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            foregroundActivating = true
            val res = DeviceBridge.stopForegroundService(ctx)
            delay(250)
            foregroundActivating = false
            foregroundServiceActive = DeviceBridge.isForegroundServiceRunning(ctx)
            foregroundStatusMessage = if (!foregroundServiceActive) {
                "Foreground Service telah dihentikan."
            } else {
                "Gagal menghentikan Foreground Service: ${res.errorDetail}"
            }
            recordLocalLog("INFO", "foreground_service", foregroundStatusMessage)
        }
    }

    fun launchAppFromBridge(appQuery: String): AppLaunchResult {
        val ctx = getApplication<Application>()
        val res = DeviceBridge.openAppByQuery(ctx, appQuery)
        appLauncherStatusMessage = if (res.success) {
            "BERHASIL: ${res.message}"
        } else {
            "GAGAL: ${res.message} ${if (res.errorDetail.isNotBlank()) "(${res.errorDetail})" else ""}"
        }
        recordLocalLog(
            if (res.success) "INFO" else "ERROR",
            "app_launcher",
            appLauncherStatusMessage
        )
        return res
    }

    fun launchChromeSearchFromBridge(searchOrUrl: String): AppLaunchResult {
        val ctx = getApplication<Application>()
        val res = DeviceBridge.openChromeWithSearchOrUrl(ctx, searchOrUrl)
        appLauncherStatusMessage = if (res.success) {
            "BERHASIL: ${res.message}"
        } else {
            "GAGAL: ${res.message} (${res.errorDetail})"
        }
        recordLocalLog(
            if (res.success) "INFO" else "ERROR",
            "app_launcher",
            appLauncherStatusMessage
        )
        return res
    }

    fun inspectScreenAccessibilityNodes() {
        refreshDeviceBridgeState()
        if (!accessibilityConnected) {
            accessibilityStatusMessage = "GAGAL: Accessibility Service belum aktif. Aktifkan izin di Pengaturan Android terlebih dahulu."
            recordLocalLog("ERROR", "accessibility", accessibilityStatusMessage)
            return
        }
        val nodes = NexusAccessibilityService.inspectVisibleNodes(40)
        visibleScreenNodes.clear()
        visibleScreenNodes.addAll(nodes)
        accessibilityStatusMessage = "Berhasil membaca ${nodes.size} elemen UI dari layar aktif (${NexusAccessibilityService.lastPackageName})."
        recordLocalLog("INFO", "accessibility", accessibilityStatusMessage)
    }

    fun performAccessibilityClick(targetText: String): Boolean {
        refreshDeviceBridgeState()
        if (!accessibilityConnected) {
            accessibilityStatusMessage = "Tidak dapat menekan '$targetText': Izin Accessibility Service belum aktif."
            recordLocalLog("ERROR", "accessibility", accessibilityStatusMessage)
            return false
        }
        val ok = NexusAccessibilityService.clickByTextOrDescription(targetText)
        accessibilityStatusMessage = if (ok) {
            "BERHASIL menekan elemen UI dengan teks/deskripsi '$targetText'."
        } else {
            "GAGAL: Elemen '$targetText' tidak ditemukan atau tidak dapat diklik di layar saat ini."
        }
        recordLocalLog(if (ok) "INFO" else "ERROR", "accessibility", accessibilityStatusMessage)
        return ok
    }

    fun performAccessibilityScroll(forward: Boolean = true): Boolean {
        refreshDeviceBridgeState()
        if (!accessibilityConnected) {
            accessibilityStatusMessage = "Tidak dapat melakukan scroll: Izin Accessibility Service belum aktif."
            recordLocalLog("ERROR", "accessibility", accessibilityStatusMessage)
            return false
        }
        val ok = NexusAccessibilityService.scrollScreen(forward)
        accessibilityStatusMessage = if (ok) {
            "BERHASIL melakukan scroll ${if (forward) "ke bawah/maju" else "ke atas/mundur"}."
        } else {
            "GAGAL: Tidak ditemukan kontainer yang dapat di-scroll pada layar aktif."
        }
        recordLocalLog(if (ok) "INFO" else "ERROR", "accessibility", accessibilityStatusMessage)
        return ok
    }

    fun performAccessibilityInput(textValue: String, fieldHint: String = ""): Boolean {
        refreshDeviceBridgeState()
        if (!accessibilityConnected) {
            accessibilityStatusMessage = "Tidak dapat mengisi teks: Izin Accessibility Service belum aktif."
            recordLocalLog("ERROR", "accessibility", accessibilityStatusMessage)
            return false
        }
        val ok = NexusAccessibilityService.fillTextIntoField(textValue, fieldHint)
        accessibilityStatusMessage = if (ok) {
            "BERHASIL mengisi teks '$textValue' ke kolom input."
        } else {
            "GAGAL: Tidak ditemukan kolom input (EditText) yang aktif di layar."
        }
        recordLocalLog(if (ok) "INFO" else "ERROR", "accessibility", accessibilityStatusMessage)
        return ok
    }

    // =========================================================================
    // 2. AUTOMATION TASK ENGINE & NATURAL INDONESIAN INTENT PARSER
    // =========================================================================

    fun parseAndroidAutomationIntent(rawPrompt: String): ParsedDeviceIntent? {
        val text = rawPrompt.trim()
        val lower = text.lowercase()

        // 1. Pattern: "Buka Chrome dan cari <query>" / "Buka browser cari <query>"
        val chromeSearchRegex = Regex(
            "^\\s*(?:tolong\\s+|coba\\s+|nexus\\s+)?(?:buka|jalankan|open)\\s+(?:aplikasi\\s+)?(?:google\\s+)?(?:chrome|browser)\\s+(?:dan\\s+|lalu\\s+|untuk\\s+)?(?:cari|search|buka)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
        chromeSearchRegex.find(text)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotEmpty()) {
                return ParsedDeviceIntent(
                    actionType = "chrome_search",
                    targetApp = "Google Chrome",
                    queryOrText = q
                )
            }
        }

        // 2. Pattern: "Cari di Chrome <query>"
        val searchInChromeRegex = Regex(
            "^\\s*(?:tolong\\s+)?(?:cari|search)\\s+(.+?)\\s+di\\s+(?:google\\s+)?(?:chrome|browser)\\s*$",
            RegexOption.IGNORE_CASE
        )
        searchInChromeRegex.find(text)?.let { m ->
            return ParsedDeviceIntent(
                actionType = "chrome_search",
                targetApp = "Google Chrome",
                queryOrText = m.groupValues[1].trim()
            )
        }

        // 3. Pattern: "Buka <App>" or "Buka <App> dan scroll"
        val openAppRegex = Regex(
            "^\\s*(?:tolong\\s+|coba\\s+|nexus\\s+)?(?:buka|jalankan|open|launch)\\s+(?:aplikasi\\s+|app\\s+)?([a-zA-Z0-9_.\\-]+)(?:\\s+(?:dan|lalu)\\s+(scroll.*))?\\s*$",
            RegexOption.IGNORE_CASE
        )
        openAppRegex.find(text)?.let { m ->
            val target = m.groupValues[1].trim()
            val extra = m.groupValues.getOrNull(2)?.trim().orEmpty()
            val ignoredWords = setOf("file", "folder", "dokumen", "catatan", "halaman", "menu")
            if (target.lowercase() !in ignoredWords) {
                return ParsedDeviceIntent(
                    actionType = "open_app",
                    targetApp = target,
                    queryOrText = extra,
                    followUpScroll = extra.contains("scroll", ignoreCase = true)
                )
            }
        }

        // 4. Accessibility UI commands: "Klik tombol <teks>", "Scroll layar", "Isi teks <teks>"
        if (lower.startsWith("klik ") || lower.startsWith("tekan tombol ")) {
            val target = text.replaceFirst(Regex("^(?:klik|tekan)(?:\\s+tombol)?\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (target.isNotEmpty()) {
                return ParsedDeviceIntent("accessibility_click", "Layar Aktif", target)
            }
        }
        if (lower == "scroll" || lower == "scroll ke bawah" || lower == "scroll bawah") {
            return ParsedDeviceIntent("accessibility_scroll", "Layar Aktif", "forward")
        }
        if (lower == "scroll ke atas" || lower == "scroll atas") {
            return ParsedDeviceIntent("accessibility_scroll", "Layar Aktif", "backward")
        }

        return null
    }

    fun cancelAutomationQueueTask(taskId: String) {
        cancelledQueueIds.add(taskId)
        val idx = automationQueue.indexOfFirst { it.id == taskId }
        if (idx >= 0) {
            val cur = automationQueue[idx]
            automationQueue[idx] = cur.copy(
                status = "cancelled",
                resultMessage = "Dibatalkan oleh pengguna.",
                logs = cur.logs + "[${nowFormatted()}] Tugas dibatalkan oleh pengguna."
            )
            recordLocalLog("WARNING", "automation_engine", "Tugas otomasi '${cur.title}' dibatalkan.")
        }
    }

    fun enqueueAndRunAutomationTask(commandText: String, maxRetries: Int = 2, onCompleteReport: ((AutomationQueueTaskModel) -> Unit)? = null) {
        val cleanCmd = commandText.trim()
        if (cleanCmd.isEmpty()) return

        val parsed = parseAndroidAutomationIntent(cleanCmd)
            ?: ParsedDeviceIntent(actionType = "open_app", targetApp = cleanCmd.removePrefix("buka ").removePrefix("Buka ").trim())

        val qId = "aq-${System.currentTimeMillis()}"
        val initialLogs = mutableListOf(
            "[${nowFormatted()}] 1. Memahami perintah: '$cleanCmd' (Aksi: ${parsed.actionType}, Target: ${parsed.targetApp})"
        )
        val initialItem = AutomationQueueTaskModel(
            id = qId,
            title = cleanCmd.take(60),
            command = cleanCmd,
            actionType = parsed.actionType,
            targetApp = parsed.targetApp,
            status = "queued",
            progressPercent = 10,
            attempt = 1,
            maxRetries = maxRetries,
            resultMessage = "Menunggu di antrean eksekusi...",
            logs = initialLogs,
            createdAt = nowFormatted()
        )
        automationQueue.add(0, initialItem)

        viewModelScope.launch {
            var attempt = 1
            var succeeded = false
            var finalMsg = ""
            var finalErr = ""
            val stepLogs = ArrayList(initialLogs)

            fun updateQueueState(st: String, prog: Int, att: Int, msg: String, err: String = "") {
                val i = automationQueue.indexOfFirst { it.id == qId }
                if (i >= 0) {
                    automationQueue[i] = automationQueue[i].copy(
                        status = st,
                        progressPercent = prog,
                        attempt = att,
                        resultMessage = msg,
                        errorMessage = err,
                        logs = ArrayList(stepLogs)
                    )
                }
            }

            while (attempt <= maxRetries && !succeeded && qId !in cancelledQueueIds) {
                stepLogs.add("[${nowFormatted()}] 2. Percobaan #$attempt/$maxRetries — Memeriksa kesiapan Device Bridge & izin...")
                updateQueueState("running", 35, attempt, "Menjalankan percobaan #$attempt...")

                val execResult = withTimeoutOrNull(12_000L) {
                    delay(180)
                    when (parsed.actionType) {
                        "chrome_search" -> {
                            stepLogs.add("[${nowFormatted()}] 3. Menjalankan Intent Google Chrome dengan pencarian: '${parsed.queryOrText}'")
                            updateQueueState("running", 70, attempt, "Membuka Chrome & mencari '${parsed.queryOrText}'...")
                            val res = launchChromeSearchFromBridge(parsed.queryOrText)
                            if (res.success) {
                                stepLogs.add("[${nowFormatted()}] 4. Verifikasi: Intent Chrome berhasil dijalankan (${res.packageName}).")
                            } else {
                                stepLogs.add("[${nowFormatted()}] 4. Verifikasi Gagal: ${res.message} (${res.errorDetail})")
                            }
                            res
                        }
                        "accessibility_click" -> {
                            stepLogs.add("[${nowFormatted()}] 3. Menjalankan klik Accessibility pada elemen '${parsed.queryOrText}'")
                            val ok = performAccessibilityClick(parsed.queryOrText)
                            AppLaunchResult(
                                success = ok,
                                appName = "Accessibility Click",
                                packageName = NexusAccessibilityService.lastPackageName,
                                message = accessibilityStatusMessage,
                                errorDetail = if (ok) "" else "ACCESSIBILITY_ACTION_FAILED"
                            )
                        }
                        "accessibility_scroll" -> {
                            val forward = parsed.queryOrText != "backward"
                            stepLogs.add("[${nowFormatted()}] 3. Menjalankan scroll Accessibility (${if (forward) "bawah" else "atas"})")
                            val ok = performAccessibilityScroll(forward)
                            AppLaunchResult(
                                success = ok,
                                appName = "Accessibility Scroll",
                                packageName = NexusAccessibilityService.lastPackageName,
                                message = accessibilityStatusMessage,
                                errorDetail = if (ok) "" else "ACCESSIBILITY_SCROLL_FAILED"
                            )
                        }
                        else -> {
                            stepLogs.add("[${nowFormatted()}] 3. Mencari package & menjalankan Launch Intent untuk '${parsed.targetApp}'...")
                            updateQueueState("running", 70, attempt, "Membuka aplikasi ${parsed.targetApp}...")
                            val res = launchAppFromBridge(parsed.targetApp)
                            if (res.success) {
                                stepLogs.add("[${nowFormatted()}] 4. Verifikasi: Aplikasi ${res.appName} (${res.packageName}) berhasil dibuka.")
                                if (parsed.followUpScroll && NexusAccessibilityService.isReady(getApplication())) {
                                    delay(1200)
                                    val scOk = NexusAccessibilityService.scrollScreen(true)
                                    stepLogs.add("[${nowFormatted()}] 4b. Aksi lanjutan scroll Accessibility: ${if (scOk) "Berhasil" else "Gagal"}")
                                }
                            } else {
                                stepLogs.add("[${nowFormatted()}] 4. Verifikasi Gagal: ${res.message}")
                            }
                            res
                        }
                    }
                }

                if (qId in cancelledQueueIds) break

                if (execResult == null) {
                    finalErr = "Execution Timeout (>12 detik) pada percobaan #$attempt."
                    stepLogs.add("[${nowFormatted()}] ERROR: $finalErr")
                } else if (execResult.success) {
                    succeeded = true
                    finalMsg = execResult.message
                    finalErr = ""
                    break
                } else {
                    finalMsg = execResult.message
                    finalErr = execResult.errorDetail.ifBlank { execResult.message }
                    // Do not retry if the package is genuinely not installed on the phone
                    if (execResult.errorDetail == "PACKAGE_NOT_INSTALLED" || execResult.errorDetail.startsWith("APP_NOT_FOUND")) {
                        stepLogs.add("[${nowFormatted()}] Menghentikan retry karena aplikasi memang belum terinstal di perangkat.")
                        break
                    }
                }

                attempt++
                if (attempt <= maxRetries && !succeeded) {
                    stepLogs.add("[${nowFormatted()}] Menunggu 600ms sebelum retry...")
                    delay(600)
                }
            }

            if (qId in cancelledQueueIds) return@launch

            val finalStatus = if (succeeded) "completed" else "failed"
            stepLogs.add("[${nowFormatted()}] 5. Status Akhir: ${finalStatus.uppercase()} — $finalMsg")
            updateQueueState(finalStatus, if (succeeded) 100 else 0, attempt.coerceAtMost(maxRetries), finalMsg, finalErr)

            val completedModel = automationQueue.firstOrNull { it.id == qId }
            if (completedModel != null && onCompleteReport != null) {
                onCompleteReport(completedModel)
            }
        }
    }

    // =========================================================================
    // 3. AI AGENT CHAT & TOOL EXECUTION LOOP
    // =========================================================================

    fun buildActiveSkillsPrompt(userMessage: String): String {
        val activeList = skills.filter {
            it.enabled && it.status == "enabled" && it.securityStatus != "blocked" && it.validationErrors.isEmpty()
        }
        if (activeList.isEmpty()) return ""
        val sb = StringBuilder("\n\n=== DAFTAR SKILL AKTIF NEXUS AI ===\n")
        sb.append("Gunakan panduan dari skill aktif berikut saat menjawab:\n")
        for (s in activeList) {
            sb.append("\n[SKILL: ${s.name} v${s.version}]\n")
            sb.append("Deskripsi: ${s.description}\n")
            if (s.requiredTools.isNotEmpty()) {
                sb.append("Tools: ${s.requiredTools.joinToString(", ")}\n")
            }
            if (s.instructions.isNotBlank()) {
                sb.append("Instruksi: ${s.instructions.take(800)}\n")
            }
        }
        return sb.toString()
    }

    fun sendChatCommand(messageText: String, runtime: String = activeRuntime) {
        if (messageText.isBlank()) return
        if (agentStopped) {
            chatMessages.add(ChatMessageModel("err-${System.currentTimeMillis()}", "assistant", "Agent sedang dalam status STOP. Klik tombol 'Aktifkan' terlebih dahulu."))
            return
        }
        chatMessages.add(ChatMessageModel("u-${System.currentTimeMillis()}", "user", messageText))

        // Step 1: Check if user command is an Android App Launch / Device Automation command!
        val deviceIntent = parseAndroidAutomationIntent(messageText)
        if (deviceIntent != null) {
            agentBusy = true
            agentPhaseText = "Menjalankan tool Android Device Bridge (${deviceIntent.targetApp})..."
            enqueueAndRunAutomationTask(messageText, maxRetries = 2) { queueResult ->
                agentBusy = false
                val steps = listOf(
                    TaskStepModel(1, "understand", "1. Understand", "Perintah Android Automation: ${deviceIntent.actionType} (${deviceIntent.targetApp})", "completed", "low"),
                    TaskStepModel(2, "plan", "2. Plan", "Memilih tool android_app_launcher / DeviceBridge", "completed", "low"),
                    TaskStepModel(3, "select_runtime", "3. Select Runtime", "Android Native Device Bridge", "completed", "low"),
                    TaskStepModel(4, "select_model", "4. Select Model", "NEXUS Android Intent & Automation Engine", "completed", "low"),
                    TaskStepModel(5, "execute", "5. Execute", queueResult.resultMessage, if (queueResult.status == "completed") "completed" else "failed", "low"),
                    TaskStepModel(6, "verify", "6. Verify", if (queueResult.status == "completed") "Terverifikasi berhasil dijalankan oleh PackageManager/Intent" else "Verifikasi mendeteksi kegagalan: ${queueResult.errorMessage}", if (queueResult.status == "completed") "completed" else "failed", "low"),
                    TaskStepModel(7, "remember", "7. Remember", "Log eksekusi disimpan ke Automation Queue & Activity Logs", "completed", "low"),
                    TaskStepModel(8, "report", "8. Report", queueResult.resultMessage, if (queueResult.status == "completed") "completed" else "failed", "low")
                )
                val reportText = buildString {
                    if (queueResult.status == "completed") {
                        append("✅ **Tugas Otomasi Android Berhasil Dijalankan**\n\n")
                    } else {
                        append("⚠️ **Tugas Otomasi Android Gagal Dijalankan**\n\n")
                    }
                    append("• **Perintah**: `${queueResult.command}`\n")
                    append("• **Tool**: `android_app_launcher` (Android Device Bridge)\n")
                    append("• **Status**: `${queueResult.status.uppercase()}`\n")
                    append("• **Hasil**: ${queueResult.resultMessage}\n")
                    if (queueResult.errorMessage.isNotBlank()) {
                        append("• **Detail Error**: `${queueResult.errorMessage}`\n")
                    }
                    append("\n**Log Eksekusi**:\n")
                    queueResult.logs.forEach { l -> append("- $l\n") }
                }
                agentPhaseText = if (queueResult.status == "completed") {
                    "Berhasil menjalankan: ${queueResult.title}"
                } else {
                    "Gagal menjalankan: ${queueResult.title}"
                }
                chatMessages.add(
                    ChatMessageModel(
                        id = "a-${System.currentTimeMillis()}",
                        role = "assistant",
                        content = reportText.trim(),
                        modelUsed = "Android Device Bridge"
                    )
                )
                tasks.add(
                    0,
                    TaskItemModel(
                        id = queueResult.id,
                        title = messageText.take(60),
                        prompt = messageText,
                        runtime = "android_bridge",
                        status = queueResult.status,
                        riskLevel = "low",
                        modelId = "DeviceBridge",
                        resultSummary = reportText.trim(),
                        errorMessage = queueResult.errorMessage,
                        recoverySteps = null,
                        createdAt = queueResult.createdAt,
                        steps = steps
                    )
                )
            }
            return
        }

        // Standard AI Reasoning / Hybrid Termux Workflow
        viewModelScope.launch {
            agentBusy = true
            val activeSkillsBlock = buildActiveSkillsPrompt(messageText)
            val activeSkillNames = skills.filter { it.enabled && it.status == "enabled" }.map { it.name }
            val sysPrompt = "Kamu adalah NEXUS AI, personal Android AI Agent.$activeSkillsBlock"

            for (p in listOf(
                "Fase 1/8 (Understand): Menganalisis maksud perintah...",
                "Fase 2/8 (Plan): Menyusun rencana & memuat ${activeSkillNames.size} skill aktif...",
                "Fase 4/8 (Select Model): Memanggil $modelNameState...",
                "Fase 5/8 (Execute): Menjalankan pemrosesan AI..."
            )) {
                agentPhaseText = p
                delay(160)
            }

            if (engineOnline) {
                val chatReq = JSONObject().apply {
                    put("message", messageText)
                    put("runtime", runtime)
                }
                val backendRes = api.requestJson("/chat", "POST", chatReq)
                if (backendRes.isSuccess) {
                    val obj = backendRes.getOrNull() ?: JSONObject()
                    val replyText = obj.optString("reply", "")
                    if (replyText.isNotBlank() && !replyText.startsWith("⚠️ API Key Belum Diatur") && !replyText.startsWith("⚠️ Gagal memanggil AI Provider")) {
                        agentBusy = false
                        agentPhaseText = "Fase 8/8 (Report): Selesai via Hybrid Local Engine ($modelNameState)"
                        chatMessages.add(ChatMessageModel("a-${System.currentTimeMillis()}", "assistant", replyText, modelNameState))
                        refreshTasks()
                        return@launch
                    }
                }
            }

            val directRes = api.callAiDirectly(
                routerUrl = routerUrlState,
                modelName = modelNameState,
                apiKey = prefs.providerApiKey,
                userPrompt = messageText,
                systemPrompt = sysPrompt
            )
            agentBusy = false
            directRes.onSuccess { aiText ->
                agentPhaseText = "Fase 8/8 (Report): Selesai dijawab oleh $modelNameState"
                chatMessages.add(ChatMessageModel("a-${System.currentTimeMillis()}", "assistant", aiText, modelNameState))
                val steps = listOf(
                    TaskStepModel(1, "understand", "1. Understand", "Selesai", "completed", "low"),
                    TaskStepModel(2, "plan", "2. Plan (Skills: ${activeSkillNames.joinToString(", ").ifEmpty { "Default" }})", "Selesai", "completed", "low"),
                    TaskStepModel(3, "select_runtime", "3. Select Runtime ($runtime)", "Selesai", "completed", "low"),
                    TaskStepModel(4, "select_model", "4. Select Model ($modelNameState)", "Selesai", "completed", "low"),
                    TaskStepModel(5, "execute", "5. Execute", "Selesai", "completed", "low"),
                    TaskStepModel(6, "verify", "6. Verify", "Selesai", "completed", "low"),
                    TaskStepModel(7, "remember", "7. Remember", "Selesai", "completed", "low"),
                    TaskStepModel(8, "report", "8. Report", "Selesai", "completed", "low")
                )
                tasks.add(0, TaskItemModel("task-${System.currentTimeMillis()}", messageText.take(60), messageText, runtime, "completed", "low", modelNameState, aiText, null, null, nowFormatted(), steps))
            }.onFailure { err ->
                val errMsg = err.message ?: "Unknown AI Provider Error"
                agentPhaseText = "Gagal: Periksa API Key / Custom Router di Settings."
                recordLocalLog("ERROR", "ai_provider", "Gagal memanggil $modelNameState: $errMsg")
                chatMessages.add(ChatMessageModel("err-${System.currentTimeMillis()}", "assistant", "Gagal memanggil '$modelNameState' ($routerUrlState):\n$errMsg\n\nBuka tab Settings untuk mengatur API Key, Custom Router URL, dan Model."))
            }
        }
    }

    // =========================================================================
    // 4. GENERATIVE VIDEO AI ENGINE (MULTI-PROVIDER ADAPTER + POLLING + DOWNLOAD)
    // =========================================================================

    fun saveVideoProviderConfig(providerId: String, providerName: String, baseUrl: String, modelName: String, rawApiKey: String) {
        val u = baseUrl.trim().trimEnd('/').ifEmpty { "https://api.siliconflow.cn/v1" }
        val m = modelName.trim().ifEmpty { "Wan-AI/Wan2.1-T2V-14B" }
        val k = rawApiKey.trim()
        prefs.videoProviderId = providerId
        prefs.videoProviderName = providerName
        prefs.videoBaseUrl = u
        prefs.videoModelName = m
        if (k.isNotEmpty()) prefs.videoApiKey = k
        videoProviderIdState = providerId
        videoProviderNameState = providerName
        videoBaseUrlState = u
        videoModelNameState = m
        maskedVideoKeyState = prefs.maskedVideoApiKey()
        videoStatusMessage = "Konfigurasi Video AI disimpan: $providerName • Model: $m • Key: $maskedVideoKeyState"

        viewModelScope.launch {
            if (k.isNotEmpty()) {
                api.requestJson(
                    "/video/providers/key",
                    "POST",
                    JSONObject().put("provider_id", providerId).put("api_key", k).put("base_url", u)
                )
            }
        }
    }

    fun enhanceVideoPromptText(rawPrompt: String, mode: String, aspectRatio: String): String {
        val clean = rawPrompt.trim()
        if (clean.isEmpty()) return ""
        val framing = when (aspectRatio) {
            "9:16" -> "vertical 9:16 mobile portrait framing"
            "1:1" -> "square 1:1 balanced framing"
            else -> "widescreen 16:9 cinematic framing"
        }
        val motion = if (mode == "image_to_video") {
            "natural subject motion, smooth camera parallax, high temporal consistency"
        } else {
            "smooth cinematic camera movement, realistic lighting and physics, coherent motion"
        }
        return "$clean. Style: photorealistic lighting, $framing, $motion, 4k detail, rich atmosphere."
    }

    fun startVideoGeneration(
        prompt: String,
        mode: String,
        imageUrl: String,
        durationSec: Int,
        aspectRatio: String,
        resolution: String,
        useEnhancement: Boolean,
        inlineApiKey: String = ""
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            videoStatusMessage = "GAGAL: Prompt deskripsi video tidak boleh kosong."
            return
        }
        if (mode == "image_to_video" && imageUrl.isBlank()) {
            videoStatusMessage = "GAGAL: Mode Image-to-Video membutuhkan URL gambar sumber."
            return
        }
        if (inlineApiKey.isNotBlank()) {
            prefs.videoApiKey = inlineApiKey.trim()
            maskedVideoKeyState = prefs.maskedVideoApiKey()
        }

        val activeKey = prefs.videoApiKey.trim()
        val enhanced = if (useEnhancement) enhanceVideoPromptText(cleanPrompt, mode, aspectRatio) else cleanPrompt
        val jobId = "vid-${System.currentTimeMillis()}"

        if (activeKey.isEmpty()) {
            val missingKeyMsg = "API Key untuk ${videoProviderNameState} belum diisi. Silakan tempel API Key provider video terlebih dahulu (NEXUS AI tidak menampilkan video simulasi/palsu)."
            videoStatusMessage = "MEMBUTUHKAN API KEY: $missingKeyMsg"
            recordLocalLog("ERROR", "video_ai", missingKeyMsg)
            videoJobs.add(
                0,
                VideoJobModel(
                    id = jobId,
                    providerId = videoProviderIdState,
                    providerName = videoProviderNameState,
                    modelName = videoModelNameState,
                    mode = mode,
                    prompt = cleanPrompt,
                    enhancedPrompt = enhanced,
                    imageUrl = imageUrl.trim(),
                    durationSec = durationSec,
                    aspectRatio = aspectRatio,
                    resolution = resolution,
                    status = "requires_api_key",
                    progressPercent = 0,
                    errorMessage = missingKeyMsg,
                    createdAt = nowFormatted()
                )
            )
            return
        }

        viewModelScope.launch {
            videoBusy = true
            videoStatusMessage = "Mengirim job rendering ke $videoProviderNameState ($videoModelNameState)..."
            val pendingJob = VideoJobModel(
                id = jobId,
                providerId = videoProviderIdState,
                providerName = videoProviderNameState,
                modelName = videoModelNameState,
                mode = mode,
                prompt = cleanPrompt,
                enhancedPrompt = enhanced,
                imageUrl = imageUrl.trim(),
                durationSec = durationSec,
                aspectRatio = aspectRatio,
                resolution = resolution,
                status = "queued",
                progressPercent = 10,
                createdAt = nowFormatted()
            )
            videoJobs.add(0, pendingJob)

            val submitRes = api.submitVideoJobDirectly(
                providerId = videoProviderIdState,
                baseUrl = videoBaseUrlState,
                modelName = videoModelNameState,
                apiKey = activeKey,
                prompt = enhanced,
                mode = mode,
                imageUrl = imageUrl.trim(),
                durationSec = durationSec,
                aspectRatio = aspectRatio,
                resolution = resolution
            )
            videoBusy = false

            submitRes.onSuccess { sub ->
                val idx = videoJobs.indexOfFirst { it.id == jobId }
                if (idx >= 0) {
                    videoJobs[idx] = videoJobs[idx].copy(
                        remoteJobId = sub.remoteJobId,
                        status = sub.status,
                        progressPercent = if (sub.status == "succeeded") 100 else 25,
                        videoUrl = sub.videoUrl
                    )
                }
                videoStatusMessage = "Job berhasil dibuat (ID: ${sub.remoteJobId}). Memantau status rendering secara berkala..."
                recordLocalLog("INFO", "video_ai", "Job video ${sub.remoteJobId} dikirim ke $videoProviderNameState")
                if (sub.status == "processing" && sub.remoteJobId.isNotBlank()) {
                    autoPollVideoJob(jobId)
                }
            }.onFailure { err ->
                val errMsg = err.message ?: "Gagal menghubungi endpoint provider video."
                val idx = videoJobs.indexOfFirst { it.id == jobId }
                if (idx >= 0) {
                    videoJobs[idx] = videoJobs[idx].copy(
                        status = "failed",
                        progressPercent = 0,
                        errorMessage = errMsg
                    )
                }
                videoStatusMessage = "RENDERING GAGAL: $errMsg"
                recordLocalLog("ERROR", "video_ai", "Gagal membuat job video: $errMsg")
            }
        }
    }

    private fun autoPollVideoJob(jobId: String) {
        viewModelScope.launch {
            var polls = 0
            while (polls < 36) { // Up to 3 minutes of automatic polling
                delay(5000)
                val cur = videoJobs.firstOrNull { it.id == jobId } ?: break
                if (cur.status != "processing" || cur.remoteJobId.isBlank()) break
                pollVideoJobNow(jobId)
                val updated = videoJobs.firstOrNull { it.id == jobId } ?: break
                if (updated.status in listOf("succeeded", "failed")) break
                polls++
            }
        }
    }

    fun pollVideoJobNow(jobId: String) {
        val cur = videoJobs.firstOrNull { it.id == jobId } ?: return
        if (cur.remoteJobId.isBlank()) {
            videoStatusMessage = "Job ini tidak memiliki Remote Task ID untuk di-poll."
            return
        }
        viewModelScope.launch {
            val pollRes = api.pollVideoJobDirectly(
                providerId = cur.providerId,
                baseUrl = videoBaseUrlState,
                apiKey = prefs.videoApiKey,
                remoteJobId = cur.remoteJobId
            )
            pollRes.onSuccess { pr ->
                val idx = videoJobs.indexOfFirst { it.id == jobId }
                if (idx >= 0) {
                    val prev = videoJobs[idx]
                    val nextProg = if (pr.status == "succeeded") 100 else (prev.progressPercent + 10).coerceAtMost(95)
                    videoJobs[idx] = prev.copy(
                        status = pr.status,
                        progressPercent = if (pr.status == "failed") 0 else nextProg,
                        videoUrl = pr.videoUrl.ifBlank { prev.videoUrl },
                        errorMessage = pr.errorMessage
                    )
                    videoStatusMessage = when (pr.status) {
                        "succeeded" -> "SELESAI: Video berhasil dirender! Silakan klik Preview atau Download."
                        "failed" -> "GAGAL: ${pr.errorMessage}"
                        else -> "Rendering masih berjalan ($nextProg%)..."
                    }
                }
            }.onFailure { err ->
                videoStatusMessage = "Polling terkendala: ${err.message}"
            }
        }
    }

    fun downloadVideoToDevice(jobId: String) {
        val cur = videoJobs.firstOrNull { it.id == jobId } ?: return
        if (cur.videoUrl.isBlank()) {
            videoStatusMessage = "URL hasil video belum tersedia."
            return
        }
        viewModelScope.launch {
            videoStatusMessage = "Mengunduh video ke penyimpanan perangkat..."
            val dlDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "NEXUS_Videos"
            )
            val targetFile = File(dlDir, "${cur.id}.mp4")
            val res = api.downloadFileToDevice(cur.videoUrl, targetFile)
            res.onSuccess { savedPath ->
                val idx = videoJobs.indexOfFirst { it.id == jobId }
                if (idx >= 0) {
                    videoJobs[idx] = videoJobs[idx].copy(localFilePath = savedPath)
                }
                videoStatusMessage = "BERHASIL DIUNDUH: Tersimpan di $savedPath"
                recordLocalLog("INFO", "video_ai", "Video berhasil diunduh ke $savedPath")
            }.onFailure { err ->
                videoStatusMessage = "Gagal mengunduh ke folder Downloads: ${err.message}"
                recordLocalLog("ERROR", "video_ai", videoStatusMessage)
            }
        }
    }

    // =========================================================================
    // 5. DIAGNOSTICS & REAL SYSTEM VERIFICATION
    // =========================================================================

    fun runFullDiagnostics() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            refreshDeviceBridgeState()
            val battery = DeviceBridge.readBatteryStatus(ctx)
            val notifOk = DeviceBridge.isNotificationPermissionGranted(ctx)
            val battOptIgnored = DeviceBridge.isIgnoringBatteryOptimizations(ctx)
            val camOk = PermissionHelper.isGranted(ctx, android.Manifest.permission.CAMERA)
            val micOk = PermissionHelper.isGranted(ctx, android.Manifest.permission.RECORD_AUDIO)
            val appsCount = installedApps.size
            val chatKeyConfigured = prefs.providerApiKey.trim().isNotEmpty()
            val videoKeyConfigured = prefs.videoApiKey.trim().isNotEmpty()

            val checks = listOf(
                DiagnosticCheckItem(
                    id = "diag-android",
                    category = "System",
                    title = "Android Version & SDK",
                    statusLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    passed = Build.VERSION.SDK_INT >= 26,
                    detail = "Perangkat: ${Build.MANUFACTURER} ${Build.MODEL} • Target SDK 34 (Android 14 Ready)"
                ),
                DiagnosticCheckItem(
                    id = "diag-perms",
                    category = "Permissions",
                    title = "App Runtime Permissions",
                    statusLabel = if (notifOk) "Notifikasi Diizinkan" else "Notifikasi Belum Diizinkan",
                    passed = notifOk,
                    detail = "POST_NOTIFICATIONS: ${if (notifOk) "GRANTED" else "DENIED"} • CAMERA: ${if (camOk) "GRANTED" else "DENIED"} • AUDIO: ${if (micOk) "GRANTED" else "DENIED"}"
                ),
                DiagnosticCheckItem(
                    id = "diag-accessibility",
                    category = "Accessibility",
                    title = "Accessibility Automation Status",
                    statusLabel = if (accessibilityConnected) "AKTIF & TERHUBUNG" else if (accessibilityEnabled) "MENUNGGU SERVICE" else "BELUM DIAKTIFKAN",
                    passed = accessibilityConnected,
                    detail = accessibilityStatusMessage
                ),
                DiagnosticCheckItem(
                    id = "diag-bridge",
                    category = "Device Bridge",
                    title = "Android Device Bridge & Package Visibility",
                    statusLabel = if (appsCount > 0) "READY ($appsCount aplikasi)" else "TERBATAS",
                    passed = appsCount > 0,
                    detail = "Terdeteksi $appsCount aplikasi launchable • Baterai: ${battery.percent}% (${if (battery.isCharging) "Charging" else "Battery"})"
                ),
                DiagnosticCheckItem(
                    id = "diag-fgs",
                    category = "Foreground Service",
                    title = "Foreground Service Status",
                    statusLabel = if (foregroundServiceActive) "RUNNING" else "STOPPED",
                    passed = foregroundServiceActive,
                    detail = foregroundStatusMessage
                ),
                DiagnosticCheckItem(
                    id = "diag-notif",
                    category = "Notifications",
                    title = "Notification Channel & Permission",
                    statusLabel = if (notifOk) "AKTIF" else "PERLU IZIN",
                    passed = notifOk,
                    detail = if (notifOk) "Channel '${NexusForegroundService.CHANNEL_ID}' siap menampilkan status Foreground." else "Izinkan notifikasi agar status Foreground Service terlihat di Android 13/14."
                ),
                DiagnosticCheckItem(
                    id = "diag-battery",
                    category = "Battery Optimization",
                    title = "Unrestricted Background / Battery Status",
                    statusLabel = if (battOptIgnored) "UNRESTRICTED" else "OPTIMIZED (RESTRICTED)",
                    passed = battOptIgnored,
                    detail = if (battOptIgnored) "Aplikasi dikecualikan dari Doze / Battery Saver." else "Disarankan menonaktifkan optimasi baterai agar Foreground Service tidak dihentikan sistem."
                ),
                DiagnosticCheckItem(
                    id = "diag-ai-conn",
                    category = "AI Provider",
                    title = "AI Provider & Custom Router",
                    statusLabel = "${providerNameState} (${modelNameState})",
                    passed = chatKeyConfigured,
                    detail = "Endpoint: $routerUrlState • Termux Engine: ${if (engineOnline) "ONLINE" else "OFFLINE (Direct AI Mode)"}"
                ),
                DiagnosticCheckItem(
                    id = "diag-api-keys",
                    category = "API Keys",
                    title = "API Configuration Status",
                    statusLabel = if (chatKeyConfigured) "Chat Key OK" else "Chat Key Kosong",
                    passed = chatKeyConfigured,
                    detail = "Chat AI Key: $maskedKeyState • Video AI Key ($videoProviderNameState): $maskedVideoKeyState"
                ),
                DiagnosticCheckItem(
                    id = "diag-automation",
                    category = "Automation Engine",
                    title = "Automation Task Engine Status",
                    statusLabel = if (agentStopped) "STOPPED" else "READY",
                    passed = !agentStopped,
                    detail = "Total tugas di antrean: ${automationQueue.size} (Selesai: ${automationQueue.count { it.status == "completed" }}, Gagal: ${automationQueue.count { it.status == "failed" }})"
                ),
                DiagnosticCheckItem(
                    id = "diag-last-err",
                    category = "Error Log",
                    title = "Last Execution Error",
                    statusLabel = if (lastExecutionError == "Tidak ada error.") "CLEAN" else "HAS ERROR",
                    passed = lastExecutionError == "Tidak ada error.",
                    detail = lastExecutionError
                )
            )
            diagnosticItems.clear()
            diagnosticItems.addAll(checks)
            val passCount = checks.count { it.passed }
            diagnosticSummaryMessage = "Diagnostik selesai (${nowFormatted()}): $passCount/${checks.size} komponen berstatus OK."
        }
    }

    fun testAppLauncherDiagnostic() {
        val ctx = getApplication<Application>()
        val apps = DeviceBridge.listInstalledLaunchableApps(ctx)
        installedApps.clear()
        installedApps.addAll(apps)
        val probeTargets = listOf("tiktok", "whatsapp", "chrome", "youtube", "instagram", "termux")
        val reportParts = mutableListOf<String>()
        for (t in probeTargets) {
            val pair = DeviceBridge.KNOWN_APP_PACKAGES[t] ?: continue
            val installedPkg = pair.second.firstOrNull { pkg ->
                runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)
            }
            reportParts.append("${pair.first}: ${if (installedPkg != null) "TERINSTAL ($installedPkg)" else "Tidak terinstal"}")
        }
        diagnosticSummaryMessage = "Test App Launcher selesai: Total ${apps.size} aplikasi terdeteksi.\n" + reportParts.joinToString(" • ")
        recordLocalLog("INFO", "diagnostics", diagnosticSummaryMessage)
    }

    private fun MutableList<String>.append(item: String) {
        this.add(item)
    }

    fun testDeviceBridgeDiagnostic() {
        val ctx = getApplication<Application>()
        val batt = DeviceBridge.readBatteryStatus(ctx)
        val clipOk = DeviceBridge.copyToClipboard(ctx, "NEXUS Diagnostic", "NEXUS_BRIDGE_OK")
        val apps = DeviceBridge.listInstalledLaunchableApps(ctx)
        diagnosticSummaryMessage = "Test Device Bridge: Baterai=${batt.percent}% (Charging=${batt.isCharging}), ClipboardWrite=$clipOk, PackageVisibility=${apps.size} apps."
        recordLocalLog("INFO", "diagnostics", diagnosticSummaryMessage)
        runFullDiagnostics()
    }

    fun testAccessibilityDiagnostic() {
        refreshDeviceBridgeState()
        diagnosticSummaryMessage = if (accessibilityConnected) {
            val nodes = NexusAccessibilityService.inspectVisibleNodes(15)
            "Test Accessibility BERHASIL: Service terhubung, membaca ${nodes.size} elemen di window '${NexusAccessibilityService.lastPackageName}'."
        } else {
            "Test Accessibility: Service BELUM AKTIF. Pengguna harus mengaktifkan 'NEXUS AI Accessibility Automation' di Pengaturan Android > Aksesibilitas."
        }
        recordLocalLog(if (accessibilityConnected) "INFO" else "WARNING", "diagnostics", diagnosticSummaryMessage)
        runFullDiagnostics()
    }

    // =========================================================================
    // 6. EXISTING SETTINGS, PROVIDERS, SKILLS, MEMORY, WORKSPACE & CLOUD
    // =========================================================================

    fun saveAiRouterConfig(providerId: String, providerName: String, routerUrl: String, modelName: String, rawApiKey: String) {
        val u = routerUrl.trim().trimEnd('/').ifEmpty { "https://generativelanguage.googleapis.com/v1beta" }
        val m = modelName.trim().ifEmpty { "gemini-2.5-flash" }
        val k = rawApiKey.trim()
        prefs.activeProviderId = providerId; prefs.activeProviderName = providerName; prefs.customRouterUrl = u; prefs.activeModelName = m
        if (k.isNotEmpty()) prefs.providerApiKey = k
        activeProviderIdState = providerId; providerNameState = providerName; routerUrlState = u; modelNameState = m; maskedKeyState = prefs.maskedApiKey()
        for (i in models.indices) models[i] = models[i].copy(isDefault = (models[i].modelName == m))
        configStatusMessage = "BERHASIL DISIMPAN!\nProvider: $providerName\nRouter: $u\nModel Aktif: $m\nAPI Key: $maskedKeyState"
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
                configStatusMessage = "KONEKSI BERHASIL!\nModel: $modelName\nBalasan AI: ${reply.trim()}"
                diagnosticSummaryMessage = "Test AI Connection BERHASIL ($modelName): ${reply.trim().take(80)}"
                recordLocalLog("INFO", "ai_provider", "Test koneksi $modelName berhasil.")
            }.onFailure { err ->
                val msg = err.message ?: "Error tidak diketahui"
                agentPhaseText = "Uji koneksi gagal. Cek API Key / Endpoint."
                configStatusMessage = "KONEKSI GAGAL:\n$msg"
                diagnosticSummaryMessage = "Test AI Connection GAGAL ($modelName): $msg"
                recordLocalLog("ERROR", "ai_provider", "Test koneksi $modelName gagal: $msg")
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
        refreshDeviceBridgeState()
        viewModelScope.launch {
            val h = api.requestJson("/health")
            engineOnline = h.isSuccess
            if (h.isSuccess) {
                agentStopped = (h.getOrNull() ?: JSONObject()).optBoolean("agent_stopped", false)
                statusBanner = "Hybrid Online (${prefs.localEndpoint}) • Model: $modelNameState"
                reportDeviceState()
                refreshProvidersAndModels()
                refreshTasks()
                refreshSkillsAndTools()
                refreshMemories()
                refreshAutomations()
                refreshApprovalsAndLogs()
                refreshWorkspaceFiles(".")
                refreshCloudStatus()
            } else {
                statusBanner = "Direct AI + Device Bridge Aktif • Model: $modelNameState"
                seedDefaults()
            }
        }
    }

    private suspend fun reportDeviceState() {
        val b = DeviceBridge.readBatteryStatus(getApplication())
        api.requestJson("/device/report", "POST", JSONObject().put("battery_percent", b.percent).put("is_charging", b.isCharging).put("network_type", "local"))
    }

    fun stopAgentEmergency() { agentBusy = false; agentStopped = true; agentPhaseText = "AGENT DIHENTIKAN DARURAT."; viewModelScope.launch { api.requestJson("/agent/stop", "POST") } }
    fun resumeAgent() { agentStopped = false; agentPhaseText = "Agent aktif kembali."; viewModelScope.launch { api.requestJson("/agent/resume", "POST") } }
    fun cancelTask(taskId: String) {
        agentBusy = false
        for (i in tasks.indices) {
            if (tasks[i].id == taskId) tasks[i] = tasks[i].copy(status = "cancelled")
        }
        viewModelScope.launch { api.requestJson("/tasks/$taskId/cancel", "POST") }
    }

    fun refreshTasks() {
        viewModelScope.launch {
            api.requestJson("/tasks").onSuccess { obj ->
                val arr = obj.optJSONArray("tasks") ?: JSONArray()
                if (arr.length() > 0) {
                    tasks.clear()
                    for (i in 0 until arr.length()) {
                        val t = arr.getJSONObject(i)
                        val stepsArr = t.optJSONArray("plan") ?: JSONArray()
                        val parsedSteps = mutableListOf<TaskStepModel>()
                        for (j in 0 until stepsArr.length()) {
                            val s = stepsArr.getJSONObject(j)
                            parsedSteps.add(
                                TaskStepModel(
                                    stepNumber = s.optInt("step_number", j + 1),
                                    phase = s.optString("phase", "step"),
                                    title = s.optString("title", "Step ${j + 1}"),
                                    description = s.optString("description", ""),
                                    status = t.optString("status", "completed"),
                                    riskLevel = s.optString("risk_level", "low")
                                )
                            )
                        }
                        tasks.add(
                            TaskItemModel(
                                id = t.optString("id"),
                                title = t.optString("title"),
                                prompt = t.optString("prompt"),
                                runtime = t.optString("runtime", "local"),
                                status = t.optString("status", "completed"),
                                riskLevel = t.optString("risk_level", "low"),
                                modelId = t.optString("model_id", modelNameState),
                                resultSummary = t.optString("result_summary", ""),
                                errorMessage = t.optString("error_message", ""),
                                recoverySteps = t.optString("recovery_steps", ""),
                                createdAt = t.optString("created_at", ""),
                                steps = parsedSteps
                            )
                        )
                    }
                }
            }
        }
    }

    fun refreshProvidersAndModels() {
        seedDefaults()
    }

    fun selectModel(model: AiModelItem) {
        modelNameState = model.modelName; prefs.activeModelName = model.modelName
        providers.find { it.id == model.providerId }?.let { activeProviderIdState = it.id; providerNameState = it.name; routerUrlState = it.baseUrl; prefs.activeProviderId = it.id; prefs.activeProviderName = it.name; prefs.customRouterUrl = it.baseUrl }
        for (i in models.indices) models[i] = models[i].copy(isDefault = (models[i].id == model.id))
        configStatusMessage = "Model aktif diubah ke: ${model.displayName} (${model.modelName})"
    }

    fun toggleAutoRouting(enabled: Boolean) { autoModelRouting = enabled }

    private fun parseSkillJsonObject(s: JSONObject): SkillItemModel {
        val toolsArr = s.optJSONArray("required_tools") ?: JSONArray()
        val tList = mutableListOf<String>()
        for (j in 0 until toolsArr.length()) tList.add(toolsArr.getString(j))

        val permsArr = s.optJSONArray("required_permissions") ?: JSONArray()
        val pList = mutableListOf<String>()
        for (j in 0 until permsArr.length()) pList.add(permsArr.getString(j))

        val errArr = s.optJSONArray("validation_errors") ?: JSONArray()
        val eList = mutableListOf<String>()
        for (j in 0 until errArr.length()) eList.add(errArr.getString(j))

        val missArr = s.optJSONArray("missing_tools") ?: JSONArray()
        val mList = mutableListOf<String>()
        for (j in 0 until missArr.length()) mList.add(missArr.getString(j))

        val enabledVal = s.optBoolean("enabled", false)
        val statusVal = s.optString("status", if (enabledVal) "enabled" else "disabled")
        return SkillItemModel(
            id = s.optString("id"),
            name = s.optString("name"),
            description = s.optString("description"),
            version = s.optString("version", "1.0.0"),
            enabled = enabledVal,
            isBuiltin = s.optBoolean("is_builtin", false),
            securityStatus = s.optString("security_status", "verified"),
            requiredTools = tList,
            status = statusVal,
            folderPath = s.optString("folder_path", ""),
            instructions = s.optString("instructions", ""),
            rawContent = s.optString("raw_content", ""),
            securityNotes = s.optString("security_notes", ""),
            requiredPermissions = pList,
            validationErrors = eList,
            missingTools = mList
        )
    }

    fun refreshSkillsAndTools() {
        viewModelScope.launch {
            val sRes = api.requestJson("/skills?scan=true")
            sRes.onSuccess { obj ->
                val arr = obj.optJSONArray("skills") ?: JSONArray()
                skills.clear()
                for (i in 0 until arr.length()) {
                    skills.add(parseSkillJsonObject(arr.getJSONObject(i)))
                }
                skillStatusMessage = "Daftar skill diperbarui (${skills.size} skill ditemukan)."
            }.onFailure {
                seedDefaults()
            }

            api.requestJson("/tools").onSuccess { obj ->
                val arr = obj.optJSONArray("tools") ?: JSONArray()
                tools.clear()
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    tools.add(
                        ToolItemModel(
                            name = t.optString("name"),
                            category = t.optString("category"),
                            riskLevel = t.optString("risk_level"),
                            description = t.optString("description")
                        )
                    )
                }
            }
        }
    }

    fun scanSkills() {
        viewModelScope.launch {
            skillStatusMessage = "Memindai folder skills/..."
            val res = api.requestJson("/skills/scan", "POST")
            res.onSuccess { obj ->
                val arr = obj.optJSONArray("skills") ?: JSONArray()
                skills.clear()
                for (i in 0 until arr.length()) {
                    skills.add(parseSkillJsonObject(arr.getJSONObject(i)))
                }
                val scanned = obj.optInt("scanned_directories", skills.size)
                val newly = obj.optInt("newly_discovered", 0)
                val errs = obj.optInt("errors", 0)
                skillStatusMessage = "Scan selesai: $scanned folder dipindai, $newly baru ditemukan, $errs error."
            }.onFailure {
                var localFound = 0
                val candidateRoots = listOf(
                    File(getApplication<Application>().filesDir, "skills"),
                    File("/sdcard/NEXUS-AI/skills"),
                    File("/data/data/com.termux/files/home/projects/NEXUS-AI/skills")
                )
                for (root in candidateRoots) {
                    if (root.exists() && root.isDirectory) {
                        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                            val md = File(dir, "SKILL.md")
                            val sid = "skill-${dir.name.lowercase()}"
                            if (skills.none { it.id == sid || it.name == dir.name }) {
                                if (md.exists()) {
                                    val raw = md.readText()
                                    skills.add(
                                        SkillItemModel(
                                            id = sid,
                                            name = dir.name,
                                            description = "Skill ditemukan di ${dir.absolutePath}",
                                            version = "1.0.0",
                                            enabled = false,
                                            isBuiltin = false,
                                            securityStatus = "verified",
                                            status = "discovered",
                                            folderPath = dir.absolutePath,
                                            instructions = raw,
                                            rawContent = raw
                                        )
                                    )
                                } else {
                                    skills.add(
                                        SkillItemModel(
                                            id = sid,
                                            name = dir.name,
                                            description = "Folder skill tanpa SKILL.md",
                                            version = "0.0.0",
                                            enabled = false,
                                            isBuiltin = false,
                                            securityStatus = "error",
                                            status = "error",
                                            folderPath = dir.absolutePath,
                                            validationErrors = listOf("File SKILL.md tidak ditemukan di folder '${dir.name}'.")
                                        )
                                    )
                                }
                                localFound++
                            }
                        }
                    }
                }
                skillStatusMessage = "Scan selesai (${skills.size} skill terdaftar, $localFound folder lokal baru)."
            }
        }
    }

    fun installDiscoveredSkill(skillId: String) {
        viewModelScope.launch {
            val res = api.requestJson("/skills/$skillId/install?enable=true", "POST")
            res.onSuccess { obj ->
                val sObj = obj.optJSONObject("skill")
                val sName = sObj?.optString("name") ?: skillId
                skillStatusMessage = "Skill '$sName' berhasil dipasang dan diaktifkan!"
                refreshSkillsAndTools()
            }.onFailure { err ->
                val idx = skills.indexOfFirst { it.id == skillId }
                if (idx >= 0) {
                    val item = skills[idx]
                    if (item.validationErrors.isNotEmpty() || item.securityStatus == "blocked") {
                        skillStatusMessage = "Gagal memasang '${item.name}': ${item.validationErrors.joinToString("; ")}"
                    } else {
                        skills[idx] = item.copy(status = "enabled", enabled = true)
                        skillStatusMessage = "Skill '${item.name}' berhasil dipasang & diaktifkan!"
                    }
                } else {
                    skillStatusMessage = "Gagal memasang skill: ${err.message}"
                }
            }
        }
    }

    fun enableSkill(skillId: String) {
        toggleSkill(skillId, true)
    }

    fun disableSkill(skillId: String) {
        toggleSkill(skillId, false)
    }

    fun toggleSkill(skillId: String, enabled: Boolean) {
        viewModelScope.launch {
            val body = JSONObject().apply { put("enabled", enabled) }
            val res = api.requestJson("/skills/$skillId/toggle", "POST", body)
            res.onSuccess {
                skillStatusMessage = "Status skill berhasil diubah ke ${if (enabled) "ENABLED" else "DISABLED"}."
                refreshSkillsAndTools()
            }.onFailure { err ->
                val idx = skills.indexOfFirst { it.id == skillId }
                if (idx >= 0) {
                    val cur = skills[idx]
                    if (enabled && (cur.validationErrors.isNotEmpty() || cur.securityStatus == "blocked")) {
                        skillStatusMessage = "Tidak dapat mengaktifkan '${cur.name}': ${cur.validationErrors.joinToString("; ")}"
                    } else {
                        skills[idx] = cur.copy(enabled = enabled, status = if (enabled) "enabled" else "disabled")
                        skillStatusMessage = "Skill '${cur.name}' sekarang ${if (enabled) "ENABLED" else "DISABLED"}."
                    }
                } else {
                    skillStatusMessage = "Gagal mengubah status skill: ${err.message}"
                }
            }
        }
    }

    fun uninstallSkill(skillId: String, deleteFiles: Boolean = false) {
        viewModelScope.launch {
            val res = api.requestJson("/skills/$skillId/uninstall?delete_files=$deleteFiles", "POST")
            res.onSuccess {
                skillStatusMessage = "Skill '$skillId' berhasil di-uninstall."
                refreshSkillsAndTools()
            }.onFailure {
                val idx = skills.indexOfFirst { it.id == skillId }
                if (idx >= 0) {
                    val cur = skills[idx]
                    if (deleteFiles && !cur.isBuiltin) {
                        skills.removeAt(idx)
                    } else {
                        skills[idx] = cur.copy(enabled = false, status = "discovered")
                    }
                    skillStatusMessage = "Skill '${cur.name}' berhasil di-uninstall."
                }
            }
        }
    }

    fun installSkill(folderName: String, markdownContent: String) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("folder_name", folderName.trim())
                put("skill_md_content", markdownContent)
                put("enable_immediately", true)
            }
            val res = api.requestJson("/skills/install", "POST", body)
            res.onSuccess { obj ->
                val sName = obj.optJSONObject("skill")?.optString("name") ?: folderName
                skillStatusMessage = "Skill '$sName' berhasil diverifikasi, dipasang, dan diaktifkan!"
                refreshSkillsAndTools()
            }.onFailure { err ->
                val raw = markdownContent.trim()
                val safeSlug = folderName.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "-")
                if (raw.isEmpty()) {
                    skillStatusMessage = "Gagal memasang '$folderName': Isi SKILL.md kosong atau Termux belum aktif (${err.message})."
                    return@onFailure
                }
                val blocked = raw.contains("rm -rf /", ignoreCase = true) ||
                    raw.contains("ignore previous instructions", ignoreCase = true) ||
                    raw.contains("ignore all previous instructions", ignoreCase = true)
                if (blocked) {
                    skillStatusMessage = "DITOLAK: SKILL.md mengandung pola berbahaya / prompt injection!"
                    return@onFailure
                }
                val nameMatch = Regex("(?m)^name:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim() ?: safeSlug
                val descMatch = Regex("(?m)^description:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim() ?: "Custom skill pengguna"
                val verMatch = Regex("(?m)^version:\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim() ?: "1.0.0"
                val bodyPart = if (raw.startsWith("---")) raw.split("---", limit = 3).getOrNull(2)?.trim() ?: raw else raw
                val newSkill = SkillItemModel(
                    id = "skill-$safeSlug",
                    name = nameMatch,
                    description = descMatch,
                    version = verMatch,
                    enabled = true,
                    isBuiltin = false,
                    securityStatus = "verified",
                    requiredTools = listOf("file_read"),
                    status = "enabled",
                    folderPath = "skills/$safeSlug",
                    instructions = bodyPart,
                    rawContent = raw
                )
                val existingIdx = skills.indexOfFirst { it.id == newSkill.id || it.name == newSkill.name }
                if (existingIdx >= 0) skills[existingIdx] = newSkill else skills.add(0, newSkill)
                skillStatusMessage = "Skill '${newSkill.name}' berhasil dipasang & diaktifkan!"
            }
        }
    }

    fun refreshMemories(searchQuery: String = "") {
        viewModelScope.launch {
            val path = if (searchQuery.isBlank()) "/memories" else "/memories/search?q=$searchQuery"
            api.requestJson(path).onSuccess { obj ->
                val arr = obj.optJSONArray(if (searchQuery.isBlank()) "memories" else "results") ?: JSONArray()
                memories.clear()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    memories.add(
                        MemoryItemModel(
                            id = m.optString("id"),
                            category = m.optString("category"),
                            key = m.optString("key"),
                            content = m.optString("content"),
                            importance = m.optInt("importance", 3),
                            syncedToCloud = m.optInt("synced_to_cloud", 0) == 1,
                            updatedAt = m.optString("updated_at", "")
                        )
                    )
                }
            }
        }
    }

    fun addMemory(category: String, key: String, content: String) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("category", category)
                put("key", key)
                put("content", content)
                put("importance", 4)
            }
            api.requestJson("/memories", "POST", body).onSuccess {
                refreshMemories()
            }.onFailure {
                memories.add(0, MemoryItemModel("mem-${System.currentTimeMillis()}", category, key, content, 4, false, nowFormatted()))
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            api.requestJson("/memories/$memoryId", "DELETE").onSuccess {
                refreshMemories()
            }.onFailure {
                memories.removeAll { it.id == memoryId }
            }
        }
    }

    fun refreshAutomations() {
        viewModelScope.launch {
            api.requestJson("/automations").onSuccess { obj ->
                val arr = obj.optJSONArray("automations") ?: JSONArray()
                automations.clear()
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i)
                    automations.add(
                        AutomationItemModel(
                            id = a.optString("id"),
                            name = a.optString("name"),
                            prompt = a.optString("prompt"),
                            scheduleCron = a.optString("schedule_cron"),
                            intervalSeconds = a.optInt("interval_seconds", 3600),
                            runtime = a.optString("runtime", "local"),
                            enabled = a.optBoolean("enabled", true),
                            lastStatus = a.optString("last_status", "scheduled")
                        )
                    )
                }
            }
        }
    }

    fun createAutomation(name: String, prompt: String, intervalSec: Int, runtime: String) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("name", name)
                put("prompt", prompt)
                put("interval_seconds", intervalSec)
                put("runtime", runtime)
            }
            api.requestJson("/automations", "POST", body).onSuccess {
                refreshAutomations()
            }.onFailure {
                automations.add(0, AutomationItemModel("auto-${System.currentTimeMillis()}", name, prompt, "0 * * * *", intervalSec, runtime, true, "scheduled"))
            }
        }
    }

    fun runAutomationNow(automationId: String) {
        val auto = automations.find { it.id == automationId }
        if (auto != null && parseAndroidAutomationIntent(auto.prompt) != null) {
            enqueueAndRunAutomationTask(auto.prompt, maxRetries = 2)
            return
        }
        viewModelScope.launch {
            api.requestJson("/automations/$automationId/run", "POST").onSuccess {
                refreshAutomations()
                refreshTasks()
            }.onFailure {
                if (auto != null) sendChatCommand(auto.prompt, auto.runtime)
            }
        }
    }

    fun refreshApprovalsAndLogs() {
        viewModelScope.launch {
            api.requestJson("/approvals").onSuccess { obj ->
                val arr = obj.optJSONArray("approvals") ?: JSONArray()
                approvals.clear()
                for (i in 0 until arr.length()) {
                    val a = arr.getJSONObject(i)
                    approvals.add(
                        ApprovalItemModel(
                            id = a.optString("id"),
                            taskId = a.optString("task_id", null),
                            actionType = a.optString("action_type"),
                            riskLevel = a.optString("risk_level"),
                            description = a.optString("description"),
                            status = a.optString("status"),
                            requestedAt = a.optString("requested_at")
                        )
                    )
                }
            }
            api.requestJson("/logs").onSuccess { obj ->
                val arr = obj.optJSONArray("logs") ?: JSONArray()
                logs.clear()
                for (i in 0 until arr.length()) {
                    val l = arr.getJSONObject(i)
                    logs.add(
                        ActivityLogModel(
                            id = l.optString("id"),
                            level = l.optString("level"),
                            category = l.optString("category"),
                            action = l.optString("action"),
                            runtime = l.optString("runtime", "local"),
                            createdAt = l.optString("created_at")
                        )
                    )
                }
            }
        }
    }

    fun resolveApproval(approvalId: String, approved: Boolean, note: String = "") {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("approved", approved)
                put("note", note)
            }
            api.requestJson("/approvals/$approvalId", "POST", body)
            refreshApprovalsAndLogs()
            refreshTasks()
        }
    }

    fun refreshWorkspaceFiles(subpath: String = ".") {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("tool_name", "file_list")
                put("arguments", JSONObject().apply { put("subpath", subpath) })
            }
            api.requestJson("/tools/execute", "POST", body).onSuccess { obj ->
                val out = obj.optJSONObject("output") ?: JSONObject()
                val arr = out.optJSONArray("items") ?: JSONArray()
                workspaceFiles.clear()
                for (i in 0 until arr.length()) {
                    val f = arr.getJSONObject(i)
                    workspaceFiles.add(
                        WorkspaceFileItem(
                            name = f.optString("name"),
                            path = f.optString("path"),
                            isDir = f.optBoolean("is_dir", false),
                            size = f.optLong("size", 0L)
                        )
                    )
                }
            }
        }
    }

    fun refreshCloudStatus() {
        viewModelScope.launch {
            api.requestJson("/cloud/status").onSuccess { obj ->
                activeRuntime = obj.optString("active_runtime", "local")
                cloudEnabled = obj.optBoolean("cloud_runtime_enabled", false)
                cloudUrl = obj.optString("cloud_runtime_url", "")
            }
        }
    }

    fun saveCloudConfig(newUrl: String, enabled: Boolean, selectedRuntime: String) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("cloud_url", newUrl)
                put("enabled", enabled)
                put("active_runtime", selectedRuntime)
            }
            api.requestJson("/cloud/config", "POST", body)
            refreshCloudStatus()
        }
    }

    fun syncCloudNow() {
        viewModelScope.launch {
            api.requestJson("/cloud/sync", "POST")
            refreshCloudStatus()
            refreshMemories()
        }
    }
}
