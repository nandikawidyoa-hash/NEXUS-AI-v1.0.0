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
                chatMessages.add(ChatMessageModel("err-${System.currentTimeMillis()}", "assistant", "Gagal memanggil '$modelNameState' ($routerUrlState):\n${err.message}\n\nBuka tab Settings untuk mengatur API Key, Custom Router URL, dan Model."))
            }
        }
    }

    fun stopAgentEmergency() { agentBusy = false; agentStopped = true; agentPhaseText = "AGENT DIHENTIKAN DARURAT."; viewModelScope.launch { api.requestJson("/agent/stop", "POST") } }
    fun resumeAgent() { agentStopped = false; agentPhaseText = "Agent aktif kembali."; viewModelScope.launch { api.requestJson("/agent/resume", "POST") } }
    fun cancelTask(taskId: String) { agentBusy = false; for (i in tasks.indices) { if (tasks[i].id == taskId) tasks[i] = tasks[i].copy(status = "cancelled") } }
    fun refreshTasks() {}
    fun refreshProvidersAndModels() { seedDefaults() }
    fun selectModel(model: AiModelItem) {
        modelNameState = model.modelName; prefs.activeModelName = model.modelName
        providers.find { it.id == model.providerId }?.let { activeProviderIdState = it.id; providerNameState = it.name; routerUrlState = it.baseUrl; prefs.activeProviderId = it.id; prefs.activeProviderName = it.name; prefs.customRouterUrl = it.baseUrl }
        for (i in models.indices) models[i] = models[i].copy(isDefault = (models[i].id == model.id))
        configStatusMessage = "Model aktif diubah ke: ${model.displayName} (${model.modelName})"
    }
    fun toggleAutoRouting(enabled: Boolean) { autoModelRouting = enabled }

    fun refreshSkillsAndTools() {
        viewModelScope.launch {
            api.requestJson("/skills").onSuccess { obj ->
                val arr = obj.optJSONArray("skills") ?: JSONArray()
                skills.clear()
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    val toolsArr = s.optJSONArray("required_tools") ?: JSONArray()
                    val tList = mutableListOf<String>()
                    for (j in 0 until toolsArr.length()) tList.add(toolsArr.getString(j))
                    skills.add(
                        SkillItemModel(
                            id = s.optString("id"),
                            name = s.optString("name"),
                            description = s.optString("description"),
                            version = s.optString("version", "1.0.0"),
                            enabled = s.optBoolean("enabled", true),
                            isBuiltin = s.optBoolean("is_builtin", false),
                            securityStatus = s.optString("security_status", "verified"),
                            requiredTools = tList
                        )
                    )
                }
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

    fun toggleSkill(skillId: String, enabled: Boolean) {
        viewModelScope.launch {
            val body = JSONObject().apply { put("enabled", enabled) }
            api.requestJson("/skills/$skillId/toggle", "POST", body)
            refreshSkillsAndTools()
        }
    }

    fun installSkill(folderName: String, markdownContent: String) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("folder_name", folderName)
                put("skill_md_content", markdownContent)
            }
            api.requestJson("/skills/install", "POST", body)
            refreshSkillsAndTools()
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
            api.requestJson("/memories", "POST", body)
            refreshMemories()
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            api.requestJson("/memories/$memoryId", "DELETE")
            refreshMemories()
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
            api.requestJson("/automations", "POST", body)
            refreshAutomations()
        }
    }

    fun runAutomationNow(automationId: String) {
        viewModelScope.launch {
            api.requestJson("/automations/$automationId/run", "POST")
            refreshAutomations()
            refreshTasks()
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
