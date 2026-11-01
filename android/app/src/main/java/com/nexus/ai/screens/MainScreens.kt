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
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("NEXUS AI v2.0 • Cyber-Droid", style = MaterialTheme.typography.headlineMedium)
            Text(vm.statusBanner, style = MaterialTheme.typography.bodyMedium)
        }
        item { NexusAgentCharacterCard(vm.agentBusy, vm.agentPhaseText, "${vm.providerNameState} (${vm.modelNameState})") }
        item { EmergencyStopBanner(vm.agentStopped, { vm.stopAgentEmergency() }, { vm.resumeAgent() }) }
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
        item {
            NexusCard {
                Text("Pusat Kendali Pendukung", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("tools") }) { Text("Tools") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("memory") }) { Text("Memory") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("automations") }) { Text("Automation") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("device") }) { Text("Device Bridge") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("approvals") }) { Text("Approvals ($pending)") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("workspace") }) { Text("Workspace") }
                    }
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
        Text("Chat & Perintah Agent (${vm.modelNameState})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
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
                placeholder = { Text("Ketik perintah atau pertanyaan...") },
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
    var prompt by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Manajemen Tugas & 8-Step Workflow", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { vm.refreshTasks() }) { Text("Refresh") }
            }
        }
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
