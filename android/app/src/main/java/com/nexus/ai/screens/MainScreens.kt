@file:OptIn(ExperimentalMaterial3Api::class)
package com.nexus.ai.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.ai.components.EmergencyStopBanner
import com.nexus.ai.components.NexusCard
import com.nexus.ai.components.RiskBadge
import com.nexus.ai.data.NexusViewModel

@Composable
fun HomeScreen(
    vm: NexusViewModel,
    onNavigate: (String) -> Unit
) {
    val pendingApprovals = vm.approvals.count { it.status == "pending" }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("NEXUS AI Agent", style = MaterialTheme.typography.headlineMedium)
            Text(vm.statusBanner, style = MaterialTheme.typography.bodyMedium)
        }
        item {
            EmergencyStopBanner(
                agentStopped = vm.agentStopped,
                onStopClick = { vm.stopAgentEmergency() },
                onResumeClick = { vm.resumeAgent() }
            )
        }
        item {
            NexusCard {
                Text("Status Runtime & Koneksi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Local Engine: ${if (vm.engineOnline) "ONLINE" else "OFFLINE"} • WebSocket: ${if (vm.wsConnected) "LIVE" else "IDLE"}")
                Text("Active Runtime: ${vm.activeRuntime.uppercase()} • Auto Model Routing: ${if (vm.autoModelRouting) "ON" else "MANUAL"}")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.refreshAll() }) { Text("Refresh Status") }
                    OutlinedButton(onClick = { onNavigate("cloud_runtime") }) { Text("Cloud Runtime") }
                }
            }
        }
        item {
            NexusCard {
                Text("Ringkasan Tugas & Keamanan", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text("Total Tugas: ${vm.tasks.size} • Menunggu Persetujuan: $pendingApprovals • Memory Tersimpan: ${vm.memories.size}")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onNavigate("approvals") }) {
                        Text("Approval Center ($pendingApprovals)")
                    }
                    OutlinedButton(onClick = { onNavigate("workspace") }) {
                        Text("Workspace")
                    }
                }
            }
        }
        item {
            NexusCard {
                Text("Pusat Kendali Pendukung", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("tools") }) { Text("Tools (${vm.tools.size})") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("memory") }) { Text("Memory (${vm.memories.size})") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("automations") }) { Text("Automation") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("device") }) { Text("Device Bridge") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("providers") }) { Text("AI Providers") }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { onNavigate("logs") }) { Text("Activity Logs") }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: NexusViewModel) {
    var inputText by remember { mutableStateOf("") }
    var selectedRuntime by remember { mutableStateOf(vm.activeRuntime) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Perintah & Percakapan Agent", style = MaterialTheme.typography.headlineMedium)
            FilterChip(
                selected = selectedRuntime == "local",
                onClick = { selectedRuntime = if (selectedRuntime == "local") "cloud" else "local" },
                label = { Text("Runtime: ${selectedRuntime.uppercase()}") }
            )
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (vm.chatMessages.isEmpty()) {
                item {
                    NexusCard {
                        Text("Siap Menerima Perintah Bahasa Alami", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Contoh perintah yang langsung dieksekusi Agent:\n• Buat file catatan_target.md di workspace\n• Organisasi folder downloads\n• Riset arsitektur SQLite FTS5 dan simpan ke dokumen\n• Cek kode Python di folder projects")
                    }
                }
            }
            items(vm.chatMessages) { msg ->
                NexusCard {
                    Text(
                        text = if (msg.role == "user") "Anda" else "NEXUS AI Agent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(text = msg.content, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (vm.streamingBuffer.isNotBlank()) {
                item {
                    NexusCard {
                        Text("Streaming dari Agent...", style = MaterialTheme.typography.labelSmall)
                        Text(vm.streamingBuffer, style = MaterialTheme.typography.bodyMedium)
                    }
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
                placeholder = { Text("Ketik perintah tugas atau pertanyaan...") },
                maxLines = 3
            )
            Button(
                onClick = {
                    val toSend = inputText
                    inputText = ""
                    vm.sendChatCommand(toSend, selectedRuntime)
                }
            ) {
                Text("Kirim")
            }
        }
    }
}

@Composable
fun TasksScreen(vm: NexusViewModel) {
    var newTaskPrompt by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Manajemen Tugas & 8-Step Workflow", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newTaskPrompt,
                    onValueChange = { newTaskPrompt = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Buat tugas bertahap baru...") }
                )
                Button(onClick = {
                    if (newTaskPrompt.isNotBlank()) {
                        vm.sendChatCommand(newTaskPrompt)
                        newTaskPrompt = ""
                    }
                }) {
                    Text("Jalankan")
                }
            }
        }
        items(vm.tasks) { task ->
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    RiskBadge(task.riskLevel)
                }
                Spacer(Modifier.height(4.dp))
                Text("Status: ${task.status.uppercase()} • Runtime: ${task.runtime.uppercase()} • Model: ${task.modelId}")
                if (!task.resultSummary.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Ringkasan Hasil: ${task.resultSummary}", style = MaterialTheme.typography.bodyMedium)
                }
                if (!task.errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Penyebab Gagal: ${task.errorMessage}", color = MaterialTheme.colorScheme.error)
                }
                if (!task.recoverySteps.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Langkah Pemulihan:\n${task.recoverySteps}", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(8.dp))
                Text("Alur 8 Tahap (Understand → Report):", style = MaterialTheme.typography.labelSmall)
                task.steps.forEach { step ->
                    Text("• ${step.title} [${step.status.uppercase()}]", style = MaterialTheme.typography.bodyMedium)
                }
                if (task.status in listOf("pending", "running", "planning", "awaiting_approval")) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { vm.cancelTask(task.id) }) {
                        Text("Batalkan Tugas (Cancel Task)")
                    }
                }
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
fun SettingsScreen(
    vm: NexusViewModel,
    onNavigate: (String) -> Unit
) {
    var localEndpoint by remember { mutableStateOf(vm.prefs.localEndpoint) }
    var apiToken by remember { mutableStateOf(vm.prefs.apiToken) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Pengaturan & Konfigurasi", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            NexusCard {
                Text("Tampilan & Tema", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gunakan Mode Gelap (Dark Mode)")
                    Switch(
                        checked = vm.darkThemeEnabled,
                        onCheckedChange = { vm.darkThemeEnabled = it }
                    )
                }
            }
        }
        item {
            NexusCard {
                Text("Koneksi Local Engine & Token Autentikasi", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = localEndpoint,
                    onValueChange = { localEndpoint = it },
                    label = { Text("Alamat Local Runtime") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiToken,
                    onValueChange = { apiToken = it },
                    label = { Text("Local Auth Token (Wajib untuk REST & WS)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    vm.prefs.localEndpoint = localEndpoint
                    vm.prefs.apiToken = apiToken
                    vm.refreshAll()
                    vm.connectRealtimeSocket()
                }) {
                    Text("Simpan & Hubungkan Ulang")
                }
            }
        }
        item {
            NexusCard {
                Text("Manajemen Lanjutan", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { onNavigate("providers") }) {
                        Text("Kelola Multi-Provider AI & Model Routing")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { onNavigate("approvals") }) {
                        Text("Buka Approval Center (Izin Risiko Tinggi)")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { onNavigate("cloud_runtime") }) {
                        Text("Konfigurasi Cloud Runtime & Sinkronisasi")
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { onNavigate("logs") }) {
                        Text("Audit Activity Logs")
                    }
                }
            }
        }
    }
}
