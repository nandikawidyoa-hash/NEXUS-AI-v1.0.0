@file:OptIn(ExperimentalMaterial3Api::class)
package com.nexus.ai.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexus.ai.components.ChatMessageContent
import com.nexus.ai.components.EmergencyStopBanner
import com.nexus.ai.components.NexusAgentCharacterCard
import com.nexus.ai.components.NexusCard
import com.nexus.ai.components.RiskBadge
import com.nexus.ai.data.NexusViewModel
import com.nexus.ai.data.SkillItemModel
import com.nexus.ai.services.DeviceBridge
import com.nexus.ai.theme.CrimsonDanger
import com.nexus.ai.theme.EmeraldOk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        Text("Aktif: ${vm.providerNameState} • Model: ${vm.modelNameState}\nRouter: ${vm.routerUrlState}\nAPI Key: ${vm.maskedKeyState}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
    val activeSkillsCount = vm.skills.count { it.enabled && it.status == "enabled" }
    val runningAutoCount = vm.autonomousRuns.count { it.status == "running" }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("NEXUS AI v3.0 • Autonomous Multi-Agent Platform", style = MaterialTheme.typography.headlineMedium)
            Text(vm.statusBanner, style = MaterialTheme.typography.bodyMedium)
        }
        item { NexusAgentCharacterCard(vm.agentBusy, vm.agentPhaseText, "${vm.providerNameState} (${vm.modelNameState})") }
        item { EmergencyStopBanner(vm.agentStopped, { vm.stopAgentEmergency() }, { vm.resumeAgent() }) }

        // 1. AUTONOMOUS MODE TOGGLE & 3-TIER AUTONOMY SECURITY
        item {
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Autonomous Agent Mode & 3-Tier Security", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Mode: ${if (vm.autonomousModeEnabled) "AUTONOMOUS AKTIF" else "MANUAL CHAT"} • Tier: ${vm.activeAutonomyTier} • Run Aktif: $runningAutoCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Switch(
                        checked = vm.autonomousModeEnabled,
                        onCheckedChange = { vm.updateAutonomyMode(it, vm.activeAutonomyTier) }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "LEVEL_1_AUTONOMOUS" to "Level 1: Full Autonomous (Riset/Draft/Analisis)",
                        "LEVEL_2_CONTROLLED" to "Level 2: Controlled (File/Video/App/Code)",
                        "LEVEL_3_CONFIRMATION_REQUIRED" to "Level 3: Strict Confirmation"
                    ).forEach { (tierCode, tierLabel) ->
                        FilterChip(
                            selected = vm.activeAutonomyTier == tierCode,
                            onClick = { vm.updateAutonomyMode(vm.autonomousModeEnabled, tierCode) },
                            label = { Text(tierLabel, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("10 Specialist Multi-Agent System Ready:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    vm.specialistAgents.forEach { agent ->
                        FilterChip(
                            selected = vm.selectedSpecialistId == agent.id,
                            onClick = { vm.selectedSpecialistId = agent.id },
                            label = { Text(agent.name, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // 2. ANDROID AUTOMATION & APP LAUNCHER SHORTCUTS
        item {
            NexusCard {
                Text("Pintasan Buka Aplikasi & Android Automation", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Foreground Bridge: ${if (vm.foregroundServiceActive) "AKTIF" else "BELUM AKTIF"} • Accessibility: ${if (vm.accessibilityConnected) "AKTIF" else "BELUM AKTIF"} • Terdeteksi ${vm.installedApps.size} Aplikasi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("TikTok", "WhatsApp", "Chrome", "YouTube", "Instagram", "Termux").forEach { appName ->
                        OutlinedButton(onClick = { vm.enqueueAndRunAutomationTask("Buka $appName") }) {
                            Text("Buka $appName")
                        }
                    }
                }
                if (vm.appLauncherStatusMessage.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(vm.appLauncherStatusMessage, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 3. MAIN CONTROL CENTER & STUDIOS
        item {
            NexusCard {
                Text("Pusat Kendali Utama & Autonomous Studios", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.weight(1f), onClick = { onNavigate("tasks") }) { Text("Autonomous Kanban") }
                        Button(modifier = Modifier.weight(1f), onClick = { onNavigate("marketing") }) { Text("Marketing Studio") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.weight(1f), onClick = { onNavigate("video") }) { Text("Video Studio 15-Tahap") }
                        Button(modifier = Modifier.weight(1f), onClick = { onNavigate("device") }) { Text("Device Bridge & App") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("diagnostics") }) { Text("Diagnostics & Test") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("automations") }) { Text("Automation Queue") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("providers") }) { Text("Model & Router") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("tools") }) { Text("Tools (${vm.tools.size})") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("memory") }) { Text("Memory") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("logs") }) { Text("Logs (${vm.logs.size})") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("approvals") }) { Text("Approvals ($pending)") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("workspace") }) { Text("Workspace (${vm.generatedAssets.size})") }
                    }
                }
            }
        }

        item {
            NexusCard {
                Text("Model, Router & Skills Aktif", style = MaterialTheme.typography.titleMedium)
                Text("Model: ${vm.modelNameState} (${vm.providerNameState})\nRouter: ${vm.routerUrlState}\nAPI Key: ${vm.maskedKeyState}\nSkills Aktif: $activeSkillsCount dari ${vm.skills.size} skill")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onNavigate("providers") }) { Text("Atur Model & Router") }
                    OutlinedButton(onClick = { vm.testCurrentAiRouter() }) { Text("Tes Koneksi AI") }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: NexusViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var copyBannerText by remember { mutableStateOf("") }
    var copyBannerOk by remember { mutableStateOf(true) }

    fun notifyCopy(ok: Boolean, message: String) {
        copyBannerOk = ok
        copyBannerText = message
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        scope.launch {
            delay(3000)
            if (copyBannerText == message) copyBannerText = ""
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chat & Multi-Agent Command (${vm.modelNameState})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = vm.autonomousModeEnabled,
                onClick = { vm.updateAutonomyMode(!vm.autonomousModeEnabled, vm.activeAutonomyTier) },
                label = { Text(if (vm.autonomousModeEnabled) "Auto: ON" else "Auto: OFF", style = MaterialTheme.typography.labelSmall) }
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            vm.specialistAgents.forEach { agent ->
                FilterChip(
                    selected = vm.selectedSpecialistId == agent.id,
                    onClick = { vm.selectedSpecialistId = agent.id },
                    label = { Text(agent.name, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Buat video promosi produk ini dan siapkan caption untuk TikTok",
                "Riset kompetitor Prime Apps dan buat strategi marketing selama 30 hari",
                "Buat website bisnis saya sampai siap diuji",
                "Analisis file ini dan buatkan laporan",
                "Buka TikTok",
                "Buka WhatsApp"
            ).forEach { quickCmd ->
                FilterChip(
                    selected = false,
                    onClick = { vm.sendChatCommand(quickCmd) },
                    label = { Text(quickCmd, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        NexusAgentCharacterCard(vm.agentBusy, vm.agentPhaseText, "${vm.providerNameState} (${vm.modelNameState})")

        if (copyBannerText.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = if (copyBannerOk) EmeraldOk.copy(alpha = 0.2f) else CrimsonDanger.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = copyBannerText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (copyBannerOk) EmeraldOk else CrimsonDanger,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(vm.chatMessages, key = { it.id }) { msg ->
                var msgCopied by remember(msg.id) { mutableStateOf(false) }
                NexusCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (msg.role == "user") "Anda" else "NEXUS AI (${msg.modelUsed ?: vm.modelNameState})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                val ok = DeviceBridge.copyToClipboard(
                                    context,
                                    "NEXUS AI Message",
                                    msg.content
                                )
                                if (ok) {
                                    msgCopied = true
                                    notifyCopy(true, "Seluruh teks (${msg.content.length} karakter) berhasil disalin.")
                                    scope.launch {
                                        delay(2000)
                                        msgCopied = false
                                    }
                                } else {
                                    notifyCopy(false, "Gagal menyalin teks ke clipboard.")
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = if (msgCopied) "✓ Tersalin" else "Salin Semua",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    ChatMessageContent(
                        rawText = msg.content,
                        onCopyFeedback = { ok, note -> notifyCopy(ok, note) }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ketik goal otonom, 'Buat video promosi...', 'Buka TikTok', atau tanya AI...") },
                maxLines = 4
            )
            Button(
                enabled = !vm.agentBusy,
                onClick = {
                    val s = inputText.trim()
                    if (s.isNotEmpty()) {
                        inputText = ""
                        vm.sendChatCommand(s)
                    }
                }
            ) {
                Text(if (vm.agentBusy) "Kerja..." else "Kirim")
            }
        }
    }
}

@Composable
fun TasksScreen(vm: NexusViewModel) {
    val context = LocalContext.current
    var prompt by remember { mutableStateOf("") }
    val kanbanFilters = listOf(
        "all" to "Semua (${vm.autonomousRuns.size})",
        "running" to "Running (${vm.autonomousRuns.count { it.status == "running" }})",
        "paused" to "Paused/Queued (${vm.autonomousRuns.count { it.status == "paused" || it.status == "queued" }})",
        "completed" to "Completed (${vm.autonomousRuns.count { it.status == "completed" }})",
        "failed" to "Failed (${vm.autonomousRuns.count { it.status == "failed" || it.status == "cancelled" }})"
    )
    val filteredRuns = remember(vm.autonomousRuns, vm.kanbanFilter) {
        when (vm.kanbanFilter) {
            "running" -> vm.autonomousRuns.filter { it.status == "running" }
            "paused" -> vm.autonomousRuns.filter { it.status == "paused" || it.status == "queued" }
            "completed" -> vm.autonomousRuns.filter { it.status == "completed" }
            "failed" -> vm.autonomousRuns.filter { it.status == "failed" || it.status == "cancelled" }
            else -> vm.autonomousRuns
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Autonomous Workspace & Kanban Board", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.refreshTasks(); vm.refreshAutonomousWorkspace() }) { Text("Refresh") }
            }
        }
        item { NexusAgentCharacterCard(vm.agentBusy, vm.agentPhaseText, "${vm.providerNameState} (${vm.modelNameState})") }

        // 1. AUTONOMOUS GOAL LAUNCHER & CHECKPOINT RECOVERY
        item {
            NexusCard {
                Text("Jalankan Goal Otonom Multi-Agent (Long-Running + Checkpoint)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Orchestrator akan membagi tujuan menjadi sub-tugas lintas 10 Specialist Agent, menyimpan checkpoint SQLite, dan mengevaluasi hasil secara mandiri.",
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Buat video promosi produk ini dan siapkan caption untuk TikTok",
                        "Riset kompetitor Prime Apps dan buat strategi marketing selama 30 hari",
                        "Buat website bisnis saya sampai siap diuji",
                        "Analisis file ini dan buatkan laporan"
                    ).forEach { sampleGoal ->
                        FilterChip(
                            selected = prompt == sampleGoal,
                            onClick = { prompt = sampleGoal },
                            label = { Text(sampleGoal, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tujuan / Goal Pengguna") },
                    placeholder = { Text("Contoh: Riset kompetitor Prime Apps dan buat strategi marketing 30 hari...") },
                    minLines = 2
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !vm.agentBusy,
                        onClick = {
                            if (prompt.isNotBlank()) {
                                vm.startAutonomousGoal(prompt)
                                prompt = ""
                            }
                        }
                    ) {
                        Text(if (vm.agentBusy) "Memproses..." else "Jalankan Goal Otonom")
                    }
                    OutlinedButton(onClick = { vm.recoverInterruptedAutonomousRuns() }) {
                        Text("Recover Checkpoint")
                    }
                }
            }
        }

        // 2. KANBAN BOARD FILTER CHIPS
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                kanbanFilters.forEach { (key, label) ->
                    FilterChip(
                        selected = vm.kanbanFilter == key,
                        onClick = { vm.kanbanFilter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        // 3. LIVE TASK MONITOR & EXECUTION TIMELINE (AUTONOMOUS RUNS)
        items(filteredRuns, key = { it.id }) { run ->
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(run.goal, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Run ID: ${run.id} • Agent Aktif: ${run.activeAgent} • Tier: ${run.autonomyLevel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    RiskBadge(run.status)
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = (run.progressPercent / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Progress: ${run.progressPercent}% (${run.currentStep}/${run.totalSteps} tahap) • QC Score: ${run.selfEvalScore}/100",
                    style = MaterialTheme.typography.labelSmall
                )
                if (run.selfEvalNotes.isNotBlank()) {
                    Text("Self-Evaluation QC: ${run.selfEvalNotes}", style = MaterialTheme.typography.labelSmall, color = EmeraldOk)
                }
                if (run.errorMessage.isNotBlank()) {
                    Text("Error Terakhir: ${run.errorMessage}", style = MaterialTheme.typography.labelSmall, color = CrimsonDanger)
                }

                // Task Planning Preview & Execution Timeline
                if (run.steps.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Execution Timeline & Kolaborasi Multi-Agent:", style = MaterialTheme.typography.labelSmall)
                    run.steps.forEach { step ->
                        Text(
                            "• [${step.status.uppercase()}] (${step.agentName}) ${step.title}" +
                                (if (step.outputSummary.isNotBlank()) " — ${step.outputSummary}" else ""),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Generated Assets Gallery
                if (run.generatedFiles.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Generated Files & Artifacts (${run.generatedFiles.size}):", style = MaterialTheme.typography.labelSmall, color = EmeraldOk)
                    run.generatedFiles.forEach { artPath ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• $artPath", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            OutlinedButton(
                                onClick = {
                                    DeviceBridge.copyToClipboard(context, "Artifact Path", artPath)
                                    Toast.makeText(context, "Path file disalin!", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("Salin Path", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                if (run.finalReport.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    ChatMessageContent(rawText = run.finalReport, onCopyFeedback = { _, _ -> })
                }

                // Pause / Resume / Cancel / Retry Controls
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (run.status == "running") {
                        OutlinedButton(onClick = { vm.pauseAutonomousRun(run.id) }) {
                            Text("Pause Task")
                        }
                    }
                    if (run.status == "paused" || run.status == "queued") {
                        Button(onClick = { vm.resumeAutonomousRun(run.id) }) {
                            Text("Resume Task")
                        }
                    }
                    if (run.status == "failed" || run.status == "cancelled" || run.status == "paused") {
                        Button(onClick = { vm.retryAutonomousRun(run.id) }) {
                            Text("Retry Failed Task")
                        }
                    }
                    if (run.status == "running" || run.status == "paused" || run.status == "queued") {
                        OutlinedButton(onClick = { vm.cancelAutonomousRun(run.id) }) {
                            Text("Cancel Task")
                        }
                    }
                }
            }
        }

        // 4.GENERATED FILES GALLERY SUMMARY
        if (vm.generatedAssets.isNotEmpty()) {
            item {
                NexusCard {
                    Text("Galeri File & Aset Hasil Kerjaan Agent (${vm.generatedAssets.size})", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    vm.generatedAssets.take(10).forEach { path ->
                        Text("• $path", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 5. LEGACY 8-STEP WORKFLOW TASKS
        if (vm.tasks.isNotEmpty()) {
            item {
                Text("Riwayat 8-Step Task Engine (${vm.tasks.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(vm.tasks, key = { it.id }) { task ->
                NexusCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        RiskBadge(task.riskLevel)
                    }
                    Text("Status: ${task.status.uppercase()} • Model: ${task.modelId}")
                    if (!task.resultSummary.isNullOrBlank() && task.resultSummary != "null") {
                        Spacer(Modifier.height(6.dp))
                        ChatMessageContent(rawText = task.resultSummary.orEmpty(), onCopyFeedback = { _, _ -> })
                    }
                    Spacer(Modifier.height(6.dp))
                    task.steps.forEach { s -> Text("• ${s.title} [${s.status.uppercase()}]", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
fun SkillsScreen(vm: NexusViewModel) {
    val context = LocalContext.current
    var folderName by remember { mutableStateOf("") }
    var skillMarkdown by remember {
        mutableStateOf("---\nname: custom-helper\ndescription: Skill kustom pengguna\nversion: 1.0.0\nrequired_tools:\n  - file_read\nrequired_permissions:\n  - storage_workspace\n---\n# Custom Skill\nInstruksi kerja skill untuk membantu pengguna.")
    }
    var showInstaller by remember { mutableStateOf(false) }
    var viewingSkill by remember { mutableStateOf<SkillItemModel?>(null) }

    if (viewingSkill != null) {
        val s = viewingSkill!!
        val rawToDisplay = s.rawContent.ifBlank { s.instructions.ifBlank { "# ${s.name}\n${s.description}" } }
        AlertDialog(
            onDismissRequest = { viewingSkill = null },
            title = { Text("SKILL.md — ${s.name} (v${s.version})") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Folder: ${s.folderPath.ifBlank { "skills/${s.name}" }}", style = MaterialTheme.typography.labelSmall)
                    Text("Status: ${s.status.uppercase()} • Security: ${s.securityStatus.uppercase()}", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = rawToDisplay,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val ok = DeviceBridge.copyToClipboard(context, "SKILL.md (${s.name})", rawToDisplay)
                    Toast.makeText(
                        context,
                        if (ok) "Isi SKILL.md berhasil disalin!" else "Gagal menyalin SKILL.md",
                        Toast.LENGTH_SHORT
                    ).show()
                }) {
                    Text("Salin SKILL.md")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewingSkill = null }) {
                    Text("Tutup")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Skill Manager (SKILL.md)", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(modifier = Modifier.weight(1f), onClick = { vm.scanSkills() }) {
                    Text("Scan Skills")
                }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { vm.refreshSkillsAndTools() }) {
                    Text("Refresh")
                }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { showInstaller = !showInstaller }) {
                    Text(if (showInstaller) "Tutup Form" else "+ Pasang")
                }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = vm.skillStatusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        if (showInstaller) {
            item {
                NexusCard {
                    Text("Pasang Skill Baru / Dari Folder skills/", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ketik nama folder di dalam 'skills/' (kosongkan isi SKILL.md jika file sudah ada di folder), atau tempel isi SKILL.md baru di bawah:",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text("Nama Folder Skill (contoh: my-skill)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = skillMarkdown,
                        onValueChange = { skillMarkdown = it },
                        label = { Text("Isi File SKILL.md (YAML Frontmatter + Instruksi)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (folderName.isNotBlank()) {
                                    vm.installSkill(folderName, skillMarkdown)
                                    folderName = ""
                                    showInstaller = false
                                }
                            }
                        ) {
                            Text("Verifikasi & Install")
                        }
                        OutlinedButton(
                            onClick = { skillMarkdown = "" }
                        ) {
                            Text("Kosongkan Teks (Baca dari Folder)")
                        }
                    }
                }
            }
        }

        items(vm.skills, key = { it.id }) { skill ->
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${skill.name} (v${skill.version})", style = MaterialTheme.typography.titleMedium)
                        Text("Status: ${skill.status.uppercase()} • Keamanan: ${skill.securityStatus.uppercase()}", style = MaterialTheme.typography.labelSmall)
                    }
                    RiskBadge(skill.status)
                }

                Spacer(Modifier.height(4.dp))
                Text(skill.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Dependencies (Tools): ${skill.requiredTools.joinToString(", ").ifEmpty { "Tidak ada" }}",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "Permissions: ${skill.requiredPermissions.joinToString(", ").ifEmpty { "Standar (Sandbox)" }}",
                    style = MaterialTheme.typography.labelSmall
                )
                if (skill.folderPath.isNotBlank()) {
                    Text("Path: ${skill.folderPath}", style = MaterialTheme.typography.labelSmall)
                }

                if (skill.validationErrors.isNotEmpty() || skill.securityStatus == "blocked") {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = CrimsonDanger.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Detail Error / Validasi:", style = MaterialTheme.typography.labelSmall, color = CrimsonDanger)
                            skill.validationErrors.forEach { err ->
                                Text("• $err", style = MaterialTheme.typography.bodyMedium, color = CrimsonDanger)
                            }
                            if (skill.securityStatus == "blocked" && skill.securityNotes.isNotBlank()) {
                                Text("• Keamanan: ${skill.securityNotes}", style = MaterialTheme.typography.bodyMedium, color = CrimsonDanger)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (skill.status == "discovered" || skill.status == "valid") {
                        Button(onClick = { vm.installDiscoveredSkill(skill.id) }) {
                            Text("Install")
                        }
                    }
                    if (skill.enabled && skill.status == "enabled") {
                        OutlinedButton(onClick = { vm.disableSkill(skill.id) }) {
                            Text("Disable")
                        }
                    } else if (skill.status != "error") {
                        Button(onClick = { vm.enableSkill(skill.id) }) {
                            Text("Enable")
                        }
                    }
                    OutlinedButton(onClick = { viewingSkill = skill }) {
                        Text("View SKILL.md")
                    }
                    if (!skill.isBuiltin || skill.status != "discovered") {
                        OutlinedButton(onClick = { vm.uninstallSkill(skill.id, deleteFiles = false) }) {
                            Text("Uninstall")
                        }
                    }
                    Switch(
                        checked = skill.enabled && skill.status == "enabled",
                        onCheckedChange = { vm.toggleSkill(skill.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: NexusViewModel, onNavigate: (String) -> Unit) {
    var localEndpoint by remember { mutableStateOf(vm.prefs.localEndpoint) }
    var apiToken by remember { mutableStateOf(vm.prefs.apiToken) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Pengaturan & Custom AI Router", style = MaterialTheme.typography.headlineMedium) }
        item { AiRouterConfigSection(vm = vm) }
        item {
            NexusCard {
                Text("Pintasan Cepat Studio & Sistem", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("diagnostics") }) { Text("Buka Diagnostics") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("video") }) { Text("Video Studio 15-Tahap") }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("marketing") }) { Text("Marketing Studio") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("cloud_runtime") }) { Text("Hybrid Cloud Engine") }
                }
            }
        }
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
}
