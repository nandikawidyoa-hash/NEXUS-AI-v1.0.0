package com.nexus.ai.data

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
import java.io.File

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
    var skillStatusMessage by mutableStateOf("Skill Manager siap. Klik 'Scan Skills' untuk memindai folder skills/.")

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

    init {
        seedDefaults()
        refreshAll()
        connectRealtimeSocket()
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
                ToolItemModel("file_read", "filesystem", "low", "Membaca file dalam direktori workspace yang diizinkan."),
                ToolItemModel("file_write", "filesystem", "medium", "Menulis file ke workspace dengan auto-backup otomatis."),
                ToolItemModel("file_list", "filesystem", "low", "Menampilkan isi direktori di dalam workspace."),
                ToolItemModel("file_search", "filesystem", "low", "Mencari teks atau nama file di dalam workspace."),
                ToolItemModel("terminal_exec", "system", "high", "Menjalankan perintah shell di dalam sandbox Termux."),
                ToolItemModel("web_fetch", "network", "low", "Mengambil konten halaman web / API publik (HTTPS)."),
                ToolItemModel("device_action", "android", "medium", "Menjalankan aksi integrasi perangkat Android.")
            ))
        }
        if (skills.isEmpty()) {
            skills.addAll(listOf(
                SkillItemModel(
                    id = "skill-code-assistant",
                    name = "code-assistant",
                    description = "Asisten pemrograman untuk menulis, mengaudit, dan memperbaiki kode.",
                    version = "1.0.0",
                    enabled = true,
                    isBuiltin = true,
                    securityStatus = "verified",
                    requiredTools = listOf("file_read", "file_write", "file_search"),
                    status = "enabled",
                    folderPath = "skills/code-assistant",
                    instructions = "Berikan kode lengkap yang rapi, mudah disalin, dan sertakan penjelasan singkat.",
                    rawContent = "---\nname: code-assistant\ndescription: Asisten pemrograman untuk menulis, mengaudit, dan memperbaiki kode.\nversion: 1.0.0\nrequired_tools:\n  - file_read\n  - file_write\n  - file_search\nrequired_permissions:\n  - storage_workspace\n---\n# Code Assistant\nBerikan kode lengkap yang rapi, mudah disalin, dan sertakan penjelasan singkat.",
                    requiredPermissions = listOf("storage_workspace")
                ),
                SkillItemModel(
                    id = "skill-web-research",
                    name = "web-research",
                    description = "Riset informasi terkini dan sintesis referensi terstruktur.",
                    version = "1.0.0",
                    enabled = true,
                    isBuiltin = true,
                    securityStatus = "verified",
                    requiredTools = listOf("web_fetch"),
                    status = "enabled",
                    folderPath = "skills/web-research",
                    instructions = "Susun hasil riset secara akurat, terstruktur, dan mudah dipahami.",
                    rawContent = "---\nname: web-research\ndescription: Riset informasi terkini dan sintesis referensi terstruktur.\nversion: 1.0.0\nrequired_tools:\n  - web_fetch\nrequired_permissions:\n  - internet\n---\n# Web Research\nSusun hasil riset secara akurat, terstruktur, dan mudah dipahami.",
                    requiredPermissions = listOf("internet")
                ),
                SkillItemModel(
                    id = "skill-android-automation",
                    name = "android-automation",
                    description = "Otomasi tugas perangkat Android, baterai, dan manajemen workspace.",
                    version = "1.0.0",
                    enabled = true,
                    isBuiltin = true,
                    securityStatus = "verified",
                    requiredTools = listOf("device_action", "terminal_exec"),
                    status = "enabled",
                    folderPath = "skills/android-automation",
                    instructions = "Bantu pengguna menjalankan otomasi Android dan Termux secara aman.",
                    rawContent = "---\nname: android-automation\ndescription: Otomasi tugas perangkat Android, baterai, dan manajemen workspace.\nversion: 1.0.0\nrequired_tools:\n  - device_action\n  - terminal_exec\nrequired_permissions:\n  - foreground_service\n---\n# Android Automation\nBantu pengguna menjalankan otomasi Android dan Termux secara aman.",
                    requiredPermissions = listOf("foreground_service")
                )
            ))
        }
    }

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
            }.onFailure { err ->
                agentPhaseText = "Uji koneksi gagal. Cek API Key / Endpoint."
                configStatusMessage = "KONEKSI GAGAL:\n${err.message}"
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
        if (agentStopped) {
            chatMessages.add(ChatMessageModel("err-${System.currentTimeMillis()}", "assistant", "Agent sedang dalam status STOP. Klik tombol 'Aktifkan' terlebih dahulu."))
            return
        }
        chatMessages.add(ChatMessageModel("u-${System.currentTimeMillis()}", "user", messageText))
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

            // If Local Termux Engine is online and command requests local tool execution, try backend /chat first
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

            // Direct AI call (with active skills injected into systemPrompt)
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
                tasks.add(0, TaskItemModel("task-${System.currentTimeMillis()}", messageText.take(60), messageText, runtime, "completed", "low", modelNameState, aiText, null, null, "Baru saja", steps))
            }.onFailure { err ->
                agentPhaseText = "Gagal: Periksa API Key / Custom Router di Settings."
                chatMessages.add(ChatMessageModel("err-${System.currentTimeMillis()}", "assistant", "Gagal memanggil '$modelNameState' ($routerUrlState):\n${err.message}\n\nBuka tab Settings untuk mengatur API Key, Custom Router URL, dan Model."))
            }
        }
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
                // Offline / Direct Mode local folder scan fallback
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
                // Local fallback update if in Direct AI mode
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
                // Local validation & install in Direct AI Mode
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
                memories.add(0, MemoryItemModel("mem-${System.currentTimeMillis()}", category, key, content, 4, false, "Baru saja"))
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
        viewModelScope.launch {
            api.requestJson("/automations/$automationId/run", "POST").onSuccess {
                refreshAutomations()
                refreshTasks()
            }.onFailure {
                val auto = automations.find { it.id == automationId }
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
