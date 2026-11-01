package com.nexus.ai.services

import android.Manifest
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.net.URLEncoder

data class BatteryTelemetry(
    val percent: Int,
    val isCharging: Boolean
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isInstalled: Boolean = true
)

data class AppLaunchResult(
    val success: Boolean,
    val appName: String,
    val packageName: String,
    val message: String,
    val errorDetail: String = ""
)

data class ForegroundActivationResult(
    val success: Boolean,
    val isRunning: Boolean,
    val message: String,
    val errorDetail: String = ""
)

object DeviceBridge {

    // Mapping alias aplikasi populer di Indonesia & Global ke daftar package name resmi
    val KNOWN_APP_PACKAGES: Map<String, Pair<String, List<String>>> = mapOf(
        "tiktok" to ("TikTok" to listOf(
            "com.ss.android.ugc.trill",      // TikTok Indonesia / SEA (Google Play resmi)
            "com.zhiliaoapp.musically",      // TikTok Global
            "com.zhiliaoapp.musically.go",   // TikTok Lite
            "com.ss.android.ugc.aweme"       // Douyin / TikTok CN
        )),
        "whatsapp" to ("WhatsApp" to listOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )),
        "wa" to ("WhatsApp" to listOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )),
        "chrome" to ("Google Chrome" to listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev"
        )),
        "youtube" to ("YouTube" to listOf(
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music"
        )),
        "yt" to ("YouTube" to listOf(
            "com.google.android.youtube"
        )),
        "instagram" to ("Instagram" to listOf(
            "com.instagram.android",
            "com.instagram.lite"
        )),
        "ig" to ("Instagram" to listOf(
            "com.instagram.android",
            "com.instagram.lite"
        )),
        "telegram" to ("Telegram" to listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.thunderdog.challegram"
        )),
        "termux" to ("Termux" to listOf(
            "com.termux"
        )),
        "gmail" to ("Gmail" to listOf(
            "com.google.android.gm"
        )),
        "maps" to ("Google Maps" to listOf(
            "com.google.android.apps.maps"
        )),
        "pengaturan" to ("Pengaturan Android" to listOf(
            "com.android.settings"
        )),
        "settings" to ("Pengaturan Android" to listOf(
            "com.android.settings"
        ))
    )

    fun readBatteryStatus(context: Context): BatteryTelemetry {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryTelemetry(percent = pct, isCharging = charging)
    }

    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }.getOrDefault(false)

    fun openBatteryOptimizationSettings(context: Context): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun openAccessibilitySettings(context: Context): Boolean = runCatching {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun isForegroundServiceRunning(context: Context): Boolean {
        if (NexusForegroundService.isRunning) return true
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            @Suppress("DEPRECATION")
            val running = am?.getRunningServices(50).orEmpty()
            running.any { it.service.className == NexusForegroundService::class.java.name }
        }.getOrDefault(false)
    }

    fun activateForegroundService(context: Context): ForegroundActivationResult {
        return try {
            NexusForegroundService.lastError = ""
            val svcIntent = Intent(context, NexusForegroundService::class.java).apply {
                action = NexusForegroundService.ACTION_START
                putExtra("status_text", "Device Bridge & Automation Engine Aktif")
            }
            ContextCompat.startForegroundService(context, svcIntent)
            val notifGranted = isNotificationPermissionGranted(context)
            val noteWarn = if (!notifGranted) {
                " (Catatan: Izin Notifikasi Android 13+ belum diaktifkan, sehingga ikon di status bar mungkin tersembunyi)."
            } else {
                ""
            }
            ForegroundActivationResult(
                success = true,
                isRunning = true,
                message = "Foreground Service berhasil dijalankan$noteWarn",
                errorDetail = NexusForegroundService.lastError
            )
        } catch (e: Exception) {
            val errMsg = "${e.javaClass.simpleName}: ${e.message ?: "Gagal memulai Foreground Service"}"
            NexusForegroundService.lastError = errMsg
            ForegroundActivationResult(
                success = false,
                isRunning = false,
                message = "Gagal mengaktifkan Foreground Service.",
                errorDetail = errMsg
            )
        }
    }

    fun stopForegroundService(context: Context): ForegroundActivationResult {
        return try {
            val stopIntent = Intent(context, NexusForegroundService::class.java).apply {
                action = NexusForegroundService.ACTION_STOP
            }
            context.stopService(stopIntent)
            NexusForegroundService.isRunning = false
            ForegroundActivationResult(
                success = true,
                isRunning = false,
                message = "Foreground Service dihentikan."
            )
        } catch (e: Exception) {
            ForegroundActivationResult(
                success = false,
                isRunning = NexusForegroundService.isRunning,
                message = "Gagal menghentikan Foreground Service.",
                errorDetail = e.message ?: "Unknown error"
            )
        }
    }

    fun listInstalledLaunchableApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val discovered = linkedMapOf<String, InstalledAppInfo>()

        // 1. Query all launcher activities
        runCatching {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
            for (ri in resolveInfos) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == context.packageName) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: pkg
                discovered[pkg] = InstalledAppInfo(appName = label, packageName = pkg, isInstalled = true)
            }
        }

        // 2. Also probe known popular packages directly via getLaunchIntentForPackage
        for ((_, pair) in KNOWN_APP_PACKAGES) {
            val (displayName, pkgCandidates) = pair
            for (pkg in pkgCandidates) {
                if (!discovered.containsKey(pkg)) {
                    val launchIntent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
                    if (launchIntent != null) {
                        discovered[pkg] = InstalledAppInfo(appName = displayName, packageName = pkg, isInstalled = true)
                    }
                }
            }
        }

        return discovered.values.sortedBy { it.appName.lowercase() }
    }

    fun openAppByQuery(context: Context, rawQuery: String): AppLaunchResult {
        val pm = context.packageManager
        val clean = rawQuery.trim()
        if (clean.isEmpty()) {
            return AppLaunchResult(false, "", "", "Nama aplikasi atau package name kosong.")
        }
        val lower = clean.lowercase()

        // 1. Check direct alias in KNOWN_APP_PACKAGES (TikTok, WhatsApp, Chrome, YouTube, Instagram, Termux, etc.)
        val matchedAlias = KNOWN_APP_PACKAGES[lower] ?: KNOWN_APP_PACKAGES.entries.firstOrNull { (k, pair) ->
            lower == k || lower == pair.first.lowercase() || lower.contains(k)
        }?.value

        if (matchedAlias != null) {
            val (friendlyName, candidates) = matchedAlias
            for (pkg in candidates) {
                val launchIntent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
                if (launchIntent != null) {
                    return try {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        context.startActivity(launchIntent)
                        AppLaunchResult(
                            success = true,
                            appName = friendlyName,
                            packageName = pkg,
                            message = "Berhasil membuka $friendlyName ($pkg)."
                        )
                    } catch (e: Exception) {
                        AppLaunchResult(
                            success = false,
                            appName = friendlyName,
                            packageName = pkg,
                            message = "Gagal menjalankan intent untuk $friendlyName.",
                            errorDetail = e.message ?: "ActivityNotFound / SecurityException"
                        )
                    }
                }
            }
            // Special web deep-link fallback check if user asked for TikTok / YouTube / Instagram and app isn't installed
            return AppLaunchResult(
                success = false,
                appName = friendlyName,
                packageName = candidates.first(),
                message = "Aplikasi $friendlyName belum terinstal di perangkat ini (Package yang diperiksa: ${candidates.joinToString(", ")}).",
                errorDetail = "PACKAGE_NOT_INSTALLED"
            )
        }

        // 2. If user passed an exact package name like com.something.app
        if (clean.contains(".") && !clean.contains(" ")) {
            val directIntent = runCatching { pm.getLaunchIntentForPackage(clean) }.getOrNull()
            if (directIntent != null) {
                return try {
                    directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(directIntent)
                    AppLaunchResult(true, clean, clean, "Berhasil membuka package $clean.")
                } catch (e: Exception) {
                    AppLaunchResult(false, clean, clean, "Gagal membuka $clean.", e.message ?: "")
                }
            }
        }

        // 3. Search across all installed launchable apps on the device
        val installed = listInstalledLaunchableApps(context)
        val matchedInstalled = installed.firstOrNull {
            it.appName.equals(clean, ignoreCase = true) || it.packageName.equals(clean, ignoreCase = true)
        } ?: installed.firstOrNull {
            it.appName.contains(clean, ignoreCase = true) || it.packageName.contains(clean, ignoreCase = true)
        }

        if (matchedInstalled != null) {
            val launchIntent = runCatching { pm.getLaunchIntentForPackage(matchedInstalled.packageName) }.getOrNull()
            if (launchIntent != null) {
                return try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    context.startActivity(launchIntent)
                    AppLaunchResult(
                        success = true,
                        appName = matchedInstalled.appName,
                        packageName = matchedInstalled.packageName,
                        message = "Berhasil membuka ${matchedInstalled.appName} (${matchedInstalled.packageName})."
                    )
                } catch (e: Exception) {
                    AppLaunchResult(
                        success = false,
                        appName = matchedInstalled.appName,
                        packageName = matchedInstalled.packageName,
                        message = "Gagal membuka ${matchedInstalled.appName}.",
                        errorDetail = e.message ?: ""
                    )
                }
            }
        }

        return AppLaunchResult(
            success = false,
            appName = clean,
            packageName = "",
            message = "Aplikasi '$clean' tidak ditemukan atau belum terinstal di perangkat.",
            errorDetail = "APP_NOT_FOUND (Terdeteksi ${installed.size} aplikasi launcher di perangkat)"
        )
    }

    fun openChromeWithSearchOrUrl(context: Context, searchOrUrl: String): AppLaunchResult {
        val trimmed = searchOrUrl.trim()
        val targetUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            "https://$trimmed"
        } else {
            val encoded = runCatching { URLEncoder.encode(trimmed, "UTF-8") }.getOrDefault(trimmed.replace(" ", "+"))
            "https://www.google.com/search?q=$encoded"
        }

        val pm = context.packageManager
        val chromePackages = listOf("com.android.chrome", "com.chrome.beta")
        for (pkg in chromePackages) {
            if (runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull() != null) {
                val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val started = runCatching {
                    context.startActivity(chromeIntent)
                    true
                }.getOrDefault(false)
                if (started) {
                    return AppLaunchResult(
                        success = true,
                        appName = "Google Chrome",
                        packageName = pkg,
                        message = "Berhasil membuka Google Chrome dengan alamat/pencarian: $targetUrl"
                    )
                }
            }
        }

        // Fallback to any default browser
        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
            AppLaunchResult(
                success = true,
                appName = "Browser",
                packageName = "android.intent.action.VIEW",
                message = "Berhasil membuka browser untuk: $targetUrl"
            )
        } catch (e: Exception) {
            AppLaunchResult(
                success = false,
                appName = "Browser",
                packageName = "",
                message = "Gagal membuka browser untuk '$searchOrUrl'.",
                errorDetail = e.message ?: "No browser available"
            )
        }
    }

    fun openExternalUrl(context: Context, url: String): Boolean {
        return openChromeWithSearchOrUrl(context, url).success
    }

    fun launchTermux(context: Context): Boolean {
        return openAppByQuery(context, "termux").success
    }

    fun copyToClipboard(context: Context, label: String, text: String): Boolean = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        val clip = ClipData.newPlainText(label, text)
        cm.setPrimaryClip(clip)
        true
    }.getOrDefault(false)

    fun readClipboardText(context: Context): String = runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = cm?.primaryClip?.getItemAt(0)
        item?.coerceToText(context)?.toString() ?: ""
    }.getOrDefault("")
}
