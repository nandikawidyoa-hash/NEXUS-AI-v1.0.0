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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class NexusViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = TokenPreferences(application)
    private val api = NexusApiClient(
        getBaseUrl = { prefs.localEndpoint },
        getToken = { prefs.apiToken }
    )

    var engineOnline by mutableStateOf(false)
    var wsConnected by mutableStateOf(false)
    var agentStopped by mutableStateOf(false)
    var activeRuntime by mutableStateOf("local")
    var cloudEnabled by mutableStateOf(false)
    var cloudUrl by mutableStateOf("")
    var autoModelRouting by mutableStateOf(true)
    var streamingBuffer by mutableStateOf("")
    var statusBanner by mutableStateOf("Menghubungkan ke Local Engine (127.0.0.1:8765)...")
    var darkThemeEnabled by mutableStateOf(true)

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
        refreshAll()
        connectRealtimeSocket()
    }

    fun connectRealtimeSocket() {
        api.connectWebSocket(
            onEvent = { event, data ->
                when (event) {
                    "chat_stream" -> {
                        val delta = data.optString("delta", "")
                        val done = data.optBoolean("done", false)
                        streamingBuffer += delta
                        if (done) {
                            streamingBuffer = ""
                            refreshTasks()
                        }
                    }
                    "task_progress", "approval_requested", "agent_stopped", "runtime_status" -> {
                        refreshAll()
                    }
                }
            },
            onStatusChange = { connected ->
                wsConnected = connected
            }
        )
    }

    fun refreshAll() {
        viewModelScope.launch {
            val health = api.requestJson("/health")
            engineOnline = health.isSuccess
            if (health.isSuccess) {
                val hObj = health.getOrNull() ?: JSONObject()
                agentStopped = hObj.optBoolean("agent_stopped", false)
                statusBanner = "Terhubung ke Local Engine (${prefs.localEndpoint})"
                reportDeviceState()
                refreshTasks()
                refreshProvidersAndModels()
                refreshSkillsAndTools()
                refreshMemories()
                refreshAutomations()
                refreshApprovalsAndLogs()
                refreshWorkspaceFiles(".")
                refreshCloudStatus()
            } else {
                statusBanner = "Local Engine offline. Jalankan bash scripts/start.sh di Termux."
            }
        }
    }

    private suspend fun reportDeviceState() {
        val battery = DeviceBridge.readBatteryStatus(getApplication())
        val payload = JSONObject().apply {
            put("battery_percent", battery.percent)
            put("is_charging", battery.isCharging)
            put("network_type", "local_loopback")
            put("saf_directories", JSONArray().apply {
                if (prefs.allowedSafUri.isNotBlank()) put(prefs.allowedSafUri)
            })
        }
        api.requestJson("/device/report", "POST", payload)
    }

    fun sendChatCommand(messageText: String, runtime: String = activeRuntime) {
        if (messageText.isBlank()) return
        chatMessages.add(ChatMessageModel(id = "u-${System.currentTimeMillis()}", role = "user", content = messageText))
        viewModelScope.launch {
            val payload = JSONObject().apply {
                put("message", messageText)
                put("runtime", runtime)
            }
            val res = api.requestJson("/chat", "POST", payload)
            res.onSuccess { obj ->
                val reply = obj.optString("reply", "Tugas selesai.")
                chatMessages.add(ChatMessageModel(id = "a-${System.currentTimeMillis()}", role = "assistant", content = reply))
                refreshTasks()
                refreshApprovalsAndLogs()
                refreshMemories()
            }.onFailure { err ->
                chatMessages.add(
                    ChatMessageModel(
                        id = "err-${System.currentTimeMillis()}",
                        role = "assistant",
                        content = "Gagal menghubungi Engine: ${err.message}"
                    )
                )
            }
        }
    }

    fun stopAgentEmergency() {
        viewModelScope.launch {
            api.requestJson("/agent/stop", "POST")
            agentStopped = true
            statusBanner = "STOP AGENT AKTIF — Seluruh tugas berjalan telah dihentikan."
            refreshTasks()
        }
    }

    fun resumeAgent() {
        viewModelScope.launch {
            api.requestJson("/agent/resume", "POST")
            agentStopped = false
            statusBanner = "Agent aktif kembali."
            refreshTasks()
        }
    }

    fun cancelTask(taskId: String) {
        viewModelScope.launch {
            api.requestJson("/tasks/$taskId/cancel", "POST")
            refreshTasks()
        }
    }

    fun refreshTasks() {
        viewModelScope.launch {
            api.requestJson("/tasks").onSuccess { obj ->
                val arr = obj.optJSONArray("tasks") ?: JSONArray()
                tasks.clear()
                for (i in 0 until arr.length()) {
                    val t = arr.getJSONObject(i)
                    val planArr = t.optJSONArray("plan") ?: JSONArray()
                    val stepsList = mutableListOf<TaskStepModel>()
                    for (j in 0 until planArr.length()) {
                        val s = planArr.getJSONObject(j)
                        stepsList.add(
                            TaskStepModel(
                                stepNumber = s.optInt("step_number", j + 1),
                                phase = s.optString("phase", ""),
                                title = s.optString("title", ""),
                                description = s.optString("description", ""),
                                status = t.optString("status", "pending"),
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
                            status = t.optString("status", "pending"),
                            riskLevel = t.optString("risk_level", "low"),
                            modelId = t.optString("model_id", ""),
                            resultSummary = t.optString("result_summary", null),
                            errorMessage = t.optString("error_message", null),
                            recoverySteps = t.optString("recovery_steps", null),
                            createdAt = t.optString("created_at", ""),
                            steps = stepsList
                        )
                    )
                }
            }
        }
    }

    fun refreshProvidersAndModels() {
        viewModelScope.launch {
            api.requestJson("/providers").onSuccess { obj ->
                val arr = obj.optJSONArray("providers") ?: JSONArray()
                providers.clear()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    providers.add(
                        ProviderItemModel(
                            id = p.optString("id"),
                            name = p.optString("name"),
                            providerType = p.optString("provider_type"),
                            baseUrl = p.optString("base_url"),
                            keyMasked = p.optString("key_masked", "Not configured"),
                            enabled = p.optBoolean("enabled", true),
                            priority = p.optInt("priority", 10),
                            lastTestStatus = p.optString("last_test_status", "untested")
                        )
                    )
                }
            }
            api.requestJson("/models").onSuccess { obj ->
                autoModelRouting = obj.optBoolean("auto_model_routing", true)
                val arr = obj.optJSONArray("models") ?: JSONArray()
                models.clear()
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    models.add(
                        AiModelItem(
                            id = m.optString("id"),
                            providerId = m.optString("provider_id"),
                            providerName = m.optString("provider_name"),
                            modelName = m.optString("model_name"),
                            displayName = m.optString("display_name"),
                            isDefault = m.optBoolean("is_default", false),
                            enabled = m.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }
    }

    fun updateProviderApiKey(providerId: String, newKey: String, baseUrl: String) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("api_key", newKey)
                put("base_url", baseUrl)
                put("enabled", true)
            }
            api.requestJson("/providers/$providerId/key", "PUT", body)
            refreshProvidersAndModels()
        }
    }

    fun addCustomProvider(name: String, providerType: String, baseUrl: String, apiKey: String, modelName: String) {
        viewModelScope.launch {
            val body = JSONObject().apply {
                put("name", name)
                put("provider_type", providerType)
                put("base_url", baseUrl)
                put("api_key", apiKey)
                put("default_model_name", modelName)
            }
            api.requestJson("/providers", "POST", body)
            refreshProvidersAndModels()
        }
    }

    fun testProvider(providerId: String) {
        viewModelScope.launch {
            val body = JSONObject().apply { put("provider_id", providerId) }
            api.requestJson("/providers/test", "POST", body)
            refreshProvidersAndModels()
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            api.requestJson("/models/$modelId/select", "POST")
            refreshProvidersAndModels()
        }
    }

    fun toggleAutoRouting(enabled: Boolean) {
        viewModelScope.launch {
            api.requestJson("/models/routing?enabled=$enabled", "POST")
            autoModelRouting = enabled
        }
    }

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
