package com.nexus.ai.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexus.ai.components.NexusCard
import com.nexus.ai.components.RiskBadge
import com.nexus.ai.data.NexusViewModel
import com.nexus.ai.services.DeviceBridge
import com.nexus.ai.services.NexusForegroundService

@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onBack) { Text("Kembali") }
    }
}

@Composable
fun ToolManagerScreen(vm: NexusViewModel, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubScreenHeader("Tool Manager", onBack) }
        items(vm.tools) { tool ->
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tool.name, style = MaterialTheme.typography.titleMedium)
                    RiskBadge(tool.riskLevel)
                }
                Text("Kategori: ${tool.category}", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Text(tool.description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun MemoryManagerScreen(vm: NexusViewModel, onBack: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("long_term") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubScreenHeader("Memory Manager (SQLite FTS5)", onBack) }
        item {
            NexusCard {
                Text("Cari Memory (FTS5 Full-Text Search)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Kata kunci memory...") }
                    )
                    Button(onClick = { vm.refreshMemories(searchQuery) }) { Text("Cari") }
                }
            }
        }
        item {
            NexusCard {
                Text("Tambah Memory Baru (Proteksi Anti-API Key Aktif)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("Judul / Kunci Konteks") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = newContent,
                    onValueChange = { newContent = it },
                    label = { Text("Isi Memory (Long-term / Project)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (newKey.isNotBlank() && newContent.isNotBlank()) {
                        vm.addMemory(selectedCategory, newKey, newContent)
                        newKey = ""
                        newContent = ""
                    }
                }) {
                    Text("Simpan ke SQLite")
                }
            }
        }
        items(vm.memories) { mem ->
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${mem.key} [${mem.category}]", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { vm.deleteMemory(mem.id) }) { Text("Hapus") }
                }
                Text(mem.content, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Sync Cloud: ${if (mem.syncedToCloud) "Ya" else "Belum"} • Diperbarui: ${mem.updatedAt}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun AutomationCenterScreen(vm: NexusViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var intervalSec by remember { mutableStateOf("3600") }
    var runtime by remember { mutableStateOf("local") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubScreenHeader("Automation Center", onBack) }
        item {
            NexusCard {
                Text("Peringatan Batasan Eksekusi Latar Belakang Android", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Android dapat menghentikan proses Termux di latar belakang (Doze Mode / Phantom Process Killer) jika layar mati lama. Gunakan Foreground Service untuk menjaga sesi lokal, atau pilih runtime 'cloud' untuk tugas yang wajib berjalan saat HP mati/offline.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            NexusCard {
                Text("Buat Tugas Otomatis Baru", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Tugas Otomatis") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Perintah Agent") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = intervalSec, onValueChange = { intervalSec = it }, label = { Text("Interval (detik)") }, modifier = Modifier.weight(1f))
                    FilterChip(
                        selected = runtime == "cloud",
                        onClick = { runtime = if (runtime == "local") "cloud" else "local" },
                        label = { Text("Runtime: ${runtime.uppercase()}") }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (name.isNotBlank() && prompt.isNotBlank()) {
                        vm.createAutomation(name, prompt, intervalSec.toIntOrNull() ?: 3600, runtime)
                        name = ""
                        prompt = ""
                    }
                }) {
                    Text("Jadwalkan Automation")
                }
            }
        }
        items(vm.automations) { auto ->
            NexusCard {
                Text("${auto.name} (${auto.runtime.uppercase()})", style = MaterialTheme.typography.titleMedium)
                Text("Perintah: ${auto.prompt}", style = MaterialTheme.typography.bodyMedium)
                Text("Interval: ${auto.intervalSeconds}s • Status Terakhir: ${auto.lastStatus}", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                Button(onClick = { vm.runAutomationNow(auto.id) }) { Text("Jalankan Sekarang") }
            }
        }
    }
}

@Composable
fun DeviceManagerScreen(vm: NexusViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val battery = remember { DeviceBridge.readBatteryStatus(context) }
    var clipboardPreview by remember { mutableStateOf("") }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.prefs.allowedSafUri = uri.toString()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubScreenHeader("Android Device Integration", onBack) }
        item {
            NexusCard {
                Text("Informasi Perangkat & Baterai", style = MaterialTheme.typography.titleMedium)
                Text("Level Baterai: ${battery.percent}% • Status Pengisian: ${if (battery.isCharging) "Mengisi Daya" else "Baterai"}")
                Text("Direktori SAF Diizinkan: ${vm.prefs.allowedSafUri.ifBlank { "Hanya Sandbox Workspace Default" }}")
            }
        }
        item {
            NexusCard {
                Text("Izin Penyimpanan (Storage Access Framework)", style = MaterialTheme.typography.titleMedium)
                Text("NEXUS AI membatasi akses file hanya pada folder workspace dan folder yang Anda pilih secara eksplisit melalui SAF.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { safLauncher.launch(null) }) {
                    Text("Pilih Folder Diizinkan (SAF)")
                }
            }
        }
        item {
            NexusCard {
                Text("Android Intents, Clipboard & Foreground Service", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { DeviceBridge.launchTermux(context) }) { Text("Buka Termux") }
                    OutlinedButton(onClick = {
                        context.startForegroundService(Intent(context, NexusForegroundService::class.java))
                    }) { Text("Aktifkan Foreground Service") }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipboardPreview = DeviceBridge.readClipboardText(context) }) {
                        Text("Baca Clipboard (Dengan Izin)")
                    }
                }
                if (clipboardPreview.isNotBlank()) {
                    Text("Isi Clipboard: $clipboardPreview", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun ProviderManagerScreen(vm: NexusViewModel, onBack: () -> Unit) {
    var selectedProviderId by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var baseUrlInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubScreenHeader("Multi-Provider AI & Model Routing", onBack) }
        item {
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Model Routing & Fallback", style = MaterialTheme.typography.titleMedium)
                        Text("Memilih model terbaik secara otomatis dan beralih ke provider cadangan jika gagal.")
                    }
                    Switch(checked = vm.autoModelRouting, onCheckedChange = { vm.toggleAutoRouting(it) })
                }
            }
        }
        items(vm.providers) { prov ->
            NexusCard {
                Text("${prov.name} (${prov.providerType.uppercase()})", style = MaterialTheme.typography.titleMedium)
                Text("Endpoint: ${prov.baseUrl}", style = MaterialTheme.typography.labelSmall)
                Text("API Key (Terenkripsi): ${prov.keyMasked} • Status Uji: ${prov.lastTestStatus}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.testProvider(prov.id) }) { Text("Uji Koneksi") }
                    OutlinedButton(onClick = {
                        selectedProviderId = prov.id
                        baseUrlInput = prov.baseUrl
                    }) { Text("Atur API Key") }
                }
                if (selectedProviderId == prov.id) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key Baru (Tidak akan ditampilkan penuh)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = baseUrlInput,
                        onValueChange = { baseUrlInput = it },
                        label = { Text("Base URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = {
                        vm.updateProviderApiKey(prov.id, apiKeyInput, baseUrlInput)
                        apiKeyInput = ""
                        selectedProviderId = ""
                    }) { Text("Simpan Terenkripsi") }
                }
            }
        }
        item {
            Text("Daftar Model AI Tersedia", style = MaterialTheme.typography.titleMedium)
        }
        items(vm.models) { model ->
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                        Text("${model.providerName} • ID: ${model.modelName}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (model.isDefault) {
                        Text("DEFAULT", color = MaterialTheme.colorScheme.primary)
                    } else {
                        OutlinedButton(onClick = { vm.selectModel(model.id) }) { Text("Pilih Manual") }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityLogsScreen(vm: NexusViewModel, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SubScreenHeader("Activity Logs", onBack) }
        items(vm.logs) { log ->
            NexusCard {
                Text("[${log.level}] ${log.category.uppercase()} • ${log.runtime.uppercase()}", style = MaterialTheme.typography.labelSmall)
                Text(log.action, style = MaterialTheme.typography.bodyMedium)
                Text(log.createdAt, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun WorkspaceExplorerScreen(vm: NexusViewModel, onBack: () -> Unit) {
    var currentSubpath by remember { mutableStateOf(".") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SubScreenHeader("Workspace Explorer", onBack) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(".", "projects", "documents", "downloads", "backups").forEach { folder ->
                    FilterChip(
                        selected = currentSubpath == folder,
                        onClick = {
                            currentSubpath = folder
                            vm.refreshWorkspaceFiles(folder)
                        },
                        label = { Text(folder) }
                    )
                }
            }
        }
        items(vm.workspaceFiles) { file ->
            NexusCard {
                Text(
                    text = "${if (file.isDir) "[FOLDER]" else "[FILE]"} ${file.name}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Path: ${file.path} • Ukuran: ${file.size} bytes", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ApprovalCenterScreen(vm: NexusViewModel, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubScreenHeader("Approval Center (Keamanan Semi-Autonomous)", onBack) }
        if (vm.approvals.isEmpty()) {
            item {
                NexusCard {
                    Text("Tidak ada permintaan persetujuan tertunda.")
                }
            }
        }
        items(vm.approvals) { appr ->
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(appr.actionType, style = MaterialTheme.typography.titleMedium)
                    RiskBadge(appr.riskLevel)
                }
                Spacer(Modifier.height(4.dp))
                Text(appr.description, style = MaterialTheme.typography.bodyMedium)
                Text("Status: ${appr.status.uppercase()} • Waktu: ${appr.requestedAt}", style = MaterialTheme.typography.labelSmall)
                if (appr.status == "pending") {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.resolveApproval(appr.id, true, "Disetujui pengguna via Android UI") }) {
                            Text("Setujui (Approve)")
                        }
                        OutlinedButton(onClick = { vm.resolveApproval(appr.id, false, "Ditolak pengguna via Android UI") }) {
                            Text("Tolak (Reject)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CloudRuntimeScreen(vm: NexusViewModel, onBack: () -> Unit) {
    var urlInput by remember { mutableStateOf(vm.cloudUrl) }
    var enabledInput by remember { mutableStateOf(vm.cloudEnabled) }
    var runtimeChoice by remember { mutableStateOf(vm.activeRuntime) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubScreenHeader("Hybrid Cloud Runtime Manager", onBack) }
        item {
            NexusCard {
                Text("Prinsip Kejujuran Arsitektur Hybrid", style = MaterialTheme.typography.titleMedium)
                Text(
                    "1. Anda TIDAK diwajibkan memiliki VPS berbayar; NEXUS AI berjalan penuh secara lokal di Termux.
" +
                    "2. Jika Anda mengaktifkan Cloud Runtime opsional, tugas riset/API dapat tetap berjalan saat HP offline.
" +
                    "3. Cloud Runtime TIDAK BISA mengakses file lokal HP, baterai, atau membuka aplikasi di HP ketika HP sedang offline.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            NexusCard {
                Text("Konfigurasi Cloud Endpoint", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("URL Cloud Runtime (HTTPS)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aktifkan Cloud Runtime Failover")
                    Switch(checked = enabledInput, onCheckedChange = { enabledInput = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("local", "hybrid", "cloud").forEach { rt ->
                        FilterChip(
                            selected = runtimeChoice == rt,
                            onClick = { runtimeChoice = rt },
                            label = { Text(rt.uppercase()) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveCloudConfig(urlInput, enabledInput, runtimeChoice) }) {
                        Text("Simpan Konfigurasi")
                    }
                    OutlinedButton(onClick = { vm.syncCloudNow() }) {
                        Text("Sinkronkan Memory & Task")
                    }
                }
            }
        }
    }
}
