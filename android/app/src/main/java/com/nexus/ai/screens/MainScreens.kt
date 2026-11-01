@file:OptIn(ExperimentalMaterial3Api::class)
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
                Text("Model: ${vm.modelNameState} (${vm.providerNameState})\nRouter: ${vm.routerUrlState}\nAPI Key: ${vm.maskedKeyState}")
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

@Composable
fun SkillsScreen(vm: NexusViewModel) {
    var folderName by remember { mutableStateOf("") }
    var skillMarkdown by remember {
        mutableStateOf("---\nname: custom-helper\ndescription: Skill kustom pengguna\nversion: 1.0.0\nrequired_tools:\n  - file_read\n---\n# Custom Skill\nInstruksi kerja skill.")
    }
    var showInstaller by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Modular Skills (SKILL.md)", style = MaterialTheme.typography.headlineMedium)
                Button(onClick = { showInstaller = !showInstaller }) {
                    Text(if (showInstaller) "Tutup Form" else "+ Pasang Skill")
                }
            }
        }
        if (showInstaller) {
            item {
                NexusCard {
                    Text("Pasang Skill Baru (Diperiksa oleh Security Scanner)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text("Nama Folder Skill") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = skillMarkdown,
                        onValueChange = { skillMarkdown = it },
                        label = { Text("Isi File SKILL.md") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        if (folderName.isNotBlank()) {
                            vm.installSkill(folderName, skillMarkdown)
                            folderName = ""
                            showInstaller = false
                        }
                    }) {
                        Text("Verifikasi & Pasang")
                    }
                }
            }
        }
        items(vm.skills) { skill ->
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${skill.name} (v${skill.version})", style = MaterialTheme.typography.titleMedium)
                        Text(skill.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Keamanan: ${skill.securityStatus.uppercase()} • Tools: ${skill.requiredTools.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Switch(
                        checked = skill.enabled,
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
