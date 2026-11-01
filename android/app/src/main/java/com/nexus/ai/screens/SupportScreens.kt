@file:OptIn(ExperimentalMaterial3Api::class)
package com.nexus.ai.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexus.ai.components.NexusAgentCharacterCard
import com.nexus.ai.components.NexusCard
import com.nexus.ai.components.RiskBadge
import com.nexus.ai.data.NexusViewModel
import com.nexus.ai.services.DeviceBridge
import com.nexus.ai.theme.CrimsonDanger
import com.nexus.ai.theme.EmeraldOk

@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onBack) { Text("Kembali") }
    }
}

@Composable
fun DeviceManagerScreen(vm: NexusViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val battery = remember { DeviceBridge.readBatteryStatus(context) }
    var appQueryInput by remember { mutableStateOf("") }
    var chromeSearchInput by remember { mutableStateOf("") }
    var accessClickTarget by remember { mutableStateOf("") }
    var accessInputText by remember { mutableStateOf("") }
    var showAllInstalled by remember { mutableStateOf(false) }
    var clipboardPreview by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.refreshDeviceBridgeState()
    }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.prefs.allowedSafUri = uri.toString()
        }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        vm.refreshDeviceBridgeState()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubScreenHeader("Android Device Bridge & App Launcher", onBack) }

        // 1. FOREGROUND SERVICE CARD (FIXED WITH LOADING, REAL VERIFICATION & ERROR REPORTING)
        item {
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1. Foreground Service (Android 14 Ready)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    RiskBadge(if (vm.foregroundServiceActive) "active" else "disabled")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = vm.foregroundStatusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (vm.foregroundServiceActive) EmeraldOk else MaterialTheme.colorScheme.onSurface
                )
                if (vm.foregroundActivating) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !vm.foregroundActivating,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !DeviceBridge.isNotificationPermissionGranted(context)
                            ) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            vm.activateForegroundBridge()
                        }
                    ) {
                        Text(if (vm.foregroundActivating) "Mengaktifkan..." else if (vm.foregroundServiceActive) "Refresh Foreground" else "Aktifkan Foreground")
                    }
                    if (vm.foregroundServiceActive) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !vm.foregroundActivating,
                            onClick = { vm.deactivateForegroundBridge() }
                        ) {
                            Text("Hentikan")
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { DeviceBridge.openBatteryOptimizationSettings(context) }
                        ) {
                            Text("Izin Baterai")
                        }
                    }
                }
            }
        }

        // 2. ANDROID APP LAUNCHER CARD (TIKTOK, WHATSAPP, CHROME, YOUTUBE, INSTAGRAM & INSTALLED APPS)
        item {
            NexusCard {
                Text("2. Android App Launcher (Package Visibility Aktif)", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = vm.appLauncherStatusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text("Klik Cepat Aplikasi Populer:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("TikTok", "WhatsApp", "Chrome", "YouTube", "Instagram", "Telegram", "Termux").forEach { app ->
                        Button(onClick = { vm.launchAppFromBridge(app) }) {
                            Text("Buka $app")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = appQueryInput,
                        onValueChange = { appQueryInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Nama Aplikasi / Package Name") },
                        placeholder = { Text("Contoh: TikTok / com.whatsapp") },
                        singleLine = true
                    )
                    Button(onClick = {
                        if (appQueryInput.isNotBlank()) {
                            vm.launchAppFromBridge(appQueryInput)
                        }
                    }) {
                        Text("Buka")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = chromeSearchInput,
                        onValueChange = { chromeSearchInput = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Buka Chrome & Cari / URL") },
                        placeholder = { Text("Contoh: tutorial Android Kotlin") },
                        singleLine = true
                    )
                    Button(onClick = {
                        if (chromeSearchInput.isNotBlank()) {
                            vm.launchChromeSearchFromBridge(chromeSearchInput)
                        }
                    }) {
                        Text("Cari")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            vm.refreshInstalledApps()
                            showAllInstalled = !showAllInstalled
                        }
                    ) {
                        Text(if (showAllInstalled) "Sembunyikan Daftar (${vm.installedApps.size})" else "Tampilkan Aplikasi Terinstal (${vm.installedApps.size})")
                    }
                    OutlinedButton(onClick = { vm.refreshInstalledApps() }) {
                        Text("Scan Ulang")
                    }
                }
            }
        }

        if (showAllInstalled) {
            items(vm.installedApps, key = { it.packageName }) { appInfo ->
                NexusCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(appInfo.appName, style = MaterialTheme.typography.titleMedium)
                            Text(appInfo.packageName, style = MaterialTheme.typography.labelSmall)
                        }
                        Button(onClick = { vm.launchAppFromBridge(appInfo.packageName) }) {
                            Text("Buka")
                        }
                    }
                }
            }
        }

        // 3. ACCESSIBILITY AUTOMATION CARD
        item {
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("3. Accessibility Automation Service", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    RiskBadge(if (vm.accessibilityConnected) "active" else "disabled")
                }
                Spacer(Modifier.height(4.dp))
                Text(vm.accessibilityStatusMessage, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Panduan Aktivasi:\n" +
                    "1. Klik tombol 'Buka Pengaturan Accessibility' di bawah.\n" +
                    "2. Cari menu 'Aplikasi yang didownload' / 'Installed services'.\n" +
                    "3. Pilih 'NEXUS AI Accessibility Automation' lalu ubah ke AKTIF (Allow).",
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { DeviceBridge.openAccessibilitySettings(context) }
                    ) {
                        Text("Buka Pengaturan Accessibility")
                    }
                    OutlinedButton(onClick = { vm.refreshDeviceBridgeState() }) {
                        Text("Cek Status")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { vm.inspectScreenAccessibilityNodes() }) {
                        Text("Baca Layar")
                    }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { vm.performAccessibilityScroll(true) }) {
                        Text("Scroll Bawah")
                    }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { vm.performAccessibilityScroll(false) }) {
                        Text("Scroll Atas")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = accessClickTarget,
                        onValueChange = { accessClickTarget = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Klik Elemen UI (Teks/ID)") },
                        singleLine = true
                    )
                    Button(onClick = { if (accessClickTarget.isNotBlank()) vm.performAccessibilityClick(accessClickTarget) }) {
                        Text("Klik")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = accessInputText,
                        onValueChange = { accessInputText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Isi Teks ke Kolom Input") },
                        singleLine = true
                    )
                    Button(onClick = { if (accessInputText.isNotBlank()) vm.performAccessibilityInput(accessInputText) }) {
                        Text("Isi")
                    }
                }
                if (vm.visibleScreenNodes.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Elemen UI Terbaca (${vm.visibleScreenNodes.size}):", style = MaterialTheme.typography.labelSmall)
                    vm.visibleScreenNodes.take(12).forEach { nodeStr ->
                        Text("• $nodeStr", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 4. TELEMETRY, SAF & CLIPBOARD
        item {
            NexusCard {
                Text("4. Telemetri Perangkat, SAF & Clipboard", style = MaterialTheme.typography.titleMedium)
                Text("Baterai: ${battery.percent}% • Status: ${if (battery.isCharging) "Mengisi Daya" else "Baterai"}")
                Text("Direktori SAF: ${vm.prefs.allowedSafUri.ifBlank { "Sandbox Workspace Default" }}")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = { safLauncher.launch(null) }) {
                        Text("Pilih Folder SAF")
                    }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { clipboardPreview = DeviceBridge.readClipboardText(context) }) {
                        Text("Baca Clipboard")
                    }
                }
                if (clipboardPreview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Isi Clipboard: $clipboardPreview", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun AutomationCenterScreen(vm: NexusViewModel, onBack: () -> Unit) {
    var queueCommand by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var intervalSec by remember { mutableStateOf("3600") }
    var runtime by remember { mutableStateOf("local") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubScreenHeader("Automation Task Engine & Queue", onBack) }

        item {
            NexusCard {
                Text("Jalankan Tugas Otomasi Instan (Task Queue + Retry + Timeout)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Mendukung perintah bahasa Indonesia untuk membuka aplikasi, mencari di Chrome, dan menjalankan aksi Accessibility.",
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Buka TikTok",
                        "Buka WhatsApp",
                        "Buka Chrome dan cari tutorial Android",
                        "Buka YouTube",
                        "Buka Instagram"
                    ).forEach { sample ->
                        FilterChip(
                            selected = queueCommand == sample,
                            onClick = { queueCommand = sample },
                            label = { Text(sample) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = queueCommand,
                        onValueChange = { queueCommand = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Perintah Otomasi Android") },
                        placeholder = { Text("Contoh: Buka TikTok") },
                        singleLine = true
                    )
                    Button(onClick = {
                        if (queueCommand.isNotBlank()) {
                            vm.enqueueAndRunAutomationTask(queueCommand, maxRetries = 2)
                            queueCommand = ""
                        }
                    }) {
                        Text("Jalankan")
                    }
                }
            }
        }

        if (vm.automationQueue.isNotEmpty()) {
            item {
                Text("Antrean & Riwayat Eksekusi Automation (${vm.automationQueue.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(vm.automationQueue, key = { it.id }) { qTask ->
                NexusCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(qTask.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Aksi: ${qTask.actionType} • Target: ${qTask.targetApp} • Percobaan: ${qTask.attempt}/${qTask.maxRetries}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        RiskBadge(qTask.status)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = (qTask.progressPercent / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(qTask.resultMessage, style = MaterialTheme.typography.bodyMedium)
                    if (qTask.errorMessage.isNotBlank()) {
                        Text("Error: ${qTask.errorMessage}", style = MaterialTheme.typography.labelSmall, color = CrimsonDanger)
                    }
                    Spacer(Modifier.height(4.dp))
                    qTask.logs.forEach { logLine ->
                        Text("• $logLine", style = MaterialTheme.typography.labelSmall)
                    }
                    if (qTask.status == "running" || qTask.status == "queued") {
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { vm.cancelAutomationQueueTask(qTask.id) }) {
                            Text("Batalkan Tugas")
                        }
                    }
                }
            }
        }

        item {
            NexusCard {
                Text("Jadwalkan Automation Berkala (Cron / Interval)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Tugas Terjadwal") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Perintah Agent (misal: Buka TikTok / Cek Workspace)") }, modifier = Modifier.fillMaxWidth())
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
                    Text("Simpan Jadwal Automation")
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
fun VideoGenScreen(vm: NexusViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedProvId by remember(vm.videoProviderIdState) { mutableStateOf(vm.videoProviderIdState) }
    var selectedProvName by remember(vm.videoProviderNameState) { mutableStateOf(vm.videoProviderNameState) }
    var baseUrlInput by remember(vm.videoBaseUrlState) { mutableStateOf(vm.videoBaseUrlState) }
    var modelInput by remember(vm.videoModelNameState) { mutableStateOf(vm.videoModelNameState) }
    var apiKeyInput by remember { mutableStateOf("") }

    var mode by remember { mutableStateOf("text_to_video") }
    var promptText by remember { mutableStateOf("") }
    var imageUrlInput by remember { mutableStateOf("") }
    var aspectRatio by remember { mutableStateOf("16:9") }
    var resolution by remember { mutableStateOf("720p") }
    var durationSec by remember { mutableStateOf(5) }
    var useEnhancement by remember { mutableStateOf(true) }

    val providerPresets = listOf(
        Triple("video-siliconflow", "SiliconFlow (Wan 2.1)", "https://api.siliconflow.cn/v1" to "Wan-AI/Wan2.1-T2V-14B"),
        Triple("video-dashscope", "Alibaba DashScope (Wan 2.1)", "https://dashscope-intl.aliyuncs.com/api/v1" to "wan2.1-t2v-turbo"),
        Triple("video-replicate", "Replicate (Wan 2.1 / Minimax)", "https://api.replicate.com/v1" to "wan-video/wan-2.1-1.3b")
    )

    val modelPresets = listOf(
        "Wan-AI/Wan2.1-T2V-14B",
        "Wan-AI/Wan2.1-I2V-14B-720P",
        "wan2.1-t2v-turbo",
        "wan2.1-t2v-plus",
        "wan2.1-i2v-turbo",
        "wan-video/wan-2.1-1.3b",
        "minimax/video-01"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SubScreenHeader("Generative Video AI Studio", onBack) }

        // 1. PROVIDER ADAPTER CONFIGURATION
        item {
            NexusCard {
                Text("1. Konfigurasi Provider Video AI (Multi-Provider Adapter)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Aktif: ${vm.videoProviderNameState} • Model: ${vm.videoModelNameState}\nEndpoint: ${vm.videoBaseUrlState} • API Key: ${vm.maskedVideoKeyState}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    providerPresets.forEach { (id, label, pair) ->
                        FilterChip(
                            selected = selectedProvId == id,
                            onClick = {
                                selectedProvId = id
                                selectedProvName = label
                                baseUrlInput = pair.first
                                modelInput = pair.second
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = baseUrlInput,
                    onValueChange = { baseUrlInput = it },
                    label = { Text("Endpoint URL Provider Video") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = modelInput,
                    onValueChange = { modelInput = it },
                    label = { Text("Model Video AI") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modelPresets.forEach { m ->
                        FilterChip(selected = modelInput == m, onClick = { modelInput = m }, label = { Text(m) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key Video Provider (Tersimpan: ${vm.maskedVideoKeyState})") },
                    placeholder = { Text("Tempel API Key SiliconFlow / DashScope / Replicate") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        vm.saveVideoProviderConfig(selectedProvId, selectedProvName, baseUrlInput, modelInput, apiKeyInput)
                        apiKeyInput = ""
                    }
                ) {
                    Text("Simpan Konfigurasi Video Provider")
                }
            }
        }

        // 2. VIDEO GENERATION FORM (TEXT-TO-VIDEO & IMAGE-TO-VIDEO)
        item {
            NexusCard {
                Text("2. Buat Video Baru (Text-to-Video & Image-to-Video)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "text_to_video",
                        onClick = { mode = "text_to_video" },
                        label = { Text("Text-to-Video") }
                    )
                    FilterChip(
                        selected = mode == "image_to_video",
                        onClick = { mode = "image_to_video" },
                        label = { Text("Image-to-Video") }
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    label = { Text("Prompt Deskripsi Video") },
                    placeholder = { Text("Contoh: Drone terbang di atas pegunungan berkabut saat matahari terbit, sinematik 4k...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                if (mode == "image_to_video") {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = imageUrlInput,
                        onValueChange = { imageUrlInput = it },
                        label = { Text("URL Gambar Sumber (HTTPS) untuk Image-to-Video") },
                        placeholder = { Text("https://example.com/source-image.jpg") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Aspect Ratio:", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("16:9", "9:16", "1:1").forEach { ar ->
                        FilterChip(selected = aspectRatio == ar, onClick = { aspectRatio = ar }, label = { Text(ar) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Resolusi & Durasi:", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("480p", "720p", "1080p").forEach { res ->
                        FilterChip(selected = resolution == res, onClick = { resolution = res }, label = { Text(res) })
                    }
                    listOf(5, 10).forEach { dur ->
                        FilterChip(selected = durationSec == dur, onClick = { durationSec = dur }, label = { Text("${dur} dtk") })
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Otomatis Perkaya Prompt (Prompt Enhancement)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = useEnhancement, onCheckedChange = { useEnhancement = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !vm.videoBusy,
                        onClick = {
                            vm.saveVideoProviderConfig(selectedProvId, selectedProvName, baseUrlInput, modelInput, apiKeyInput)
                            vm.startVideoGeneration(
                                prompt = promptText,
                                mode = mode,
                                imageUrl = imageUrlInput,
                                durationSec = durationSec,
                                aspectRatio = aspectRatio,
                                resolution = resolution,
                                useEnhancement = useEnhancement,
                                inlineApiKey = apiKeyInput
                            )
                            apiKeyInput = ""
                        }
                    ) {
                        Text(if (vm.videoBusy) "Mengirim Job..." else "Generate Video")
                    }
                    OutlinedButton(
                        onClick = {
                            if (promptText.isNotBlank()) {
                                promptText = vm.enhanceVideoPromptText(promptText, mode, aspectRatio)
                            }
                        }
                    ) {
                        Text("Enhance")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = vm.videoStatusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // 3. VIDEO GENERATION HISTORY, POLLING, PREVIEW & DOWNLOAD
        if (vm.videoJobs.isNotEmpty()) {
            item {
                Text("3. Riwayat & Status Rendering Video (${vm.videoJobs.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(vm.videoJobs, key = { it.id }) { job ->
                NexusCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${job.providerName} • ${job.mode.uppercase()}", style = MaterialTheme.typography.titleMedium)
                            Text("Model: ${job.modelName} • ${job.aspectRatio} • ${job.resolution} • ${job.durationSec}s", style = MaterialTheme.typography.labelSmall)
                        }
                        RiskBadge(job.status)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Prompt: ${job.prompt}", style = MaterialTheme.typography.bodyMedium)
                    if (job.remoteJobId.isNotBlank()) {
                        Text("Remote Task ID: ${job.remoteJobId}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (job.status == "processing" || job.status == "queued") {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = (job.progressPercent / 100f).coerceIn(0.05f, 0.95f),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Progress Rendering: ${job.progressPercent}% (Belum selesai, sedang diproses oleh provider)", style = MaterialTheme.typography.labelSmall)
                    }
                    if (job.errorMessage.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Info / Error: ${job.errorMessage}", style = MaterialTheme.typography.labelSmall, color = CrimsonDanger)
                    }
                    if (job.localFilePath.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Tersimpan di Perangkat: ${job.localFilePath}", style = MaterialTheme.typography.labelSmall, color = EmeraldOk)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (job.remoteJobId.isNotBlank() && job.status != "succeeded") {
                            Button(onClick = { vm.pollVideoJobNow(job.id) }) {
                                Text("Cek Status (Poll)")
                            }
                        }
                        if (job.videoUrl.isNotBlank()) {
                            Button(onClick = { DeviceBridge.openChromeWithSearchOrUrl(context, job.videoUrl) }) {
                                Text("Preview / Putar Video")
                            }
                            OutlinedButton(onClick = { vm.downloadVideoToDevice(job.id) }) {
                                Text("Download MP4")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(vm: NexusViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) {
        vm.runFullDiagnostics()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SubScreenHeader("System Diagnostics & Verification", onBack) }

        item {
            NexusCard {
                Text("Pengujian Komponen Langsung (Real Verification)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.weight(1f), onClick = { vm.runFullDiagnostics() }) {
                            Text("Run Diagnostics")
                        }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { vm.testAppLauncherDiagnostic() }) {
                            Text("Test App Launcher")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { vm.testDeviceBridgeDiagnostic() }) {
                            Text("Test Device Bridge")
                        }
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { vm.testAccessibilityDiagnostic() }) {
                            Text("Test Accessibility")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { vm.testCurrentAiRouter() }) {
                            Text("Test AI Connection")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = vm.diagnosticSummaryMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        items(vm.diagnosticItems, key = { it.id }) { item ->
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.category} • ${item.title}", style = MaterialTheme.typography.titleMedium)
                        Text("Status: ${item.statusLabel}", style = MaterialTheme.typography.labelSmall, color = if (item.passed) EmeraldOk else CrimsonDanger)
                    }
                    RiskBadge(if (item.passed) "ok" else "warning")
                }
                Spacer(Modifier.height(4.dp))
                Text(item.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
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
                    "1. Anda TIDAK diwajibkan memiliki VPS berbayar; NEXUS AI berjalan penuh secara lokal di Termux.\n" +
                    "2. Jika Anda mengaktifkan Cloud Runtime opsional, tugas riset/API dapat tetap berjalan saat HP offline.\n" +
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
