package com.nexus.ai.services

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.nexus.ai.MainActivity

class NexusForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "nexus_agent_channel"
        const val NOTIFICATION_ID = 8765
        const val ACTION_START = "com.nexus.ai.action.START_FOREGROUND"
        const val ACTION_STOP = "com.nexus.ai.action.STOP_FOREGROUND"

        @Volatile
        var isRunning: Boolean = false

        @Volatile
        var startedAtMs: Long = 0L

        @Volatile
        var lastError: String = ""
    }

    override fun onCreate() {
        super.onCreate()
        startInForegroundSafely("Memantau Local Runtime (127.0.0.1:8765) & Automation Queue")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val statusNote = intent?.getStringExtra("status_text")
            ?: "NEXUS AI Foreground Bridge & Automation Engine Aktif"
        startInForegroundSafely(statusNote)
        return START_STICKY
    }

    private fun startInForegroundSafely(contentText: String) {
        try {
            createChannel()
            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingFlags)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NEXUS AI Agent Active")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isRunning = true
            if (startedAtMs == 0L) startedAtMs = System.currentTimeMillis()
            lastError = ""
        } catch (e: Exception) {
            isRunning = false
            lastError = "${e.javaClass.simpleName}: ${e.message ?: "Gagal menjalankan startForeground"}"
        }
    }

    override fun onDestroy() {
        isRunning = false
        startedAtMs = 0L
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NEXUS AI Background Execution",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga koneksi Device Bridge & Automation Engine NEXUS AI tetap aktif"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}

class NexusAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: NexusAccessibilityService? = null

        @Volatile
        var lastPackageName: String = ""

        @Volatile
        var lastEventTimestamp: Long = 0L

        fun isAccessibilitySettingEnabled(context: Context): Boolean = runCatching {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expectedComponent1 = "${context.packageName}/${NexusAccessibilityService::class.java.canonicalName}"
            val expectedComponent2 = "${context.packageName}/.services.NexusAccessibilityService"
            enabledServices.contains(expectedComponent1, ignoreCase = true) ||
                enabledServices.contains(expectedComponent2, ignoreCase = true) ||
                enabledServices.contains(NexusAccessibilityService::class.java.simpleName, ignoreCase = true)
        }.getOrDefault(false)

        fun isReady(context: Context): Boolean {
            return isAccessibilitySettingEnabled(context) && instance != null
        }

        fun inspectVisibleNodes(maxNodes: Int = 40): List<String> {
            val svc = instance ?: return emptyList()
            val root = svc.rootInActiveWindow ?: return emptyList()
            val out = mutableListOf<String>()
            fun walk(node: AccessibilityNodeInfo?) {
                if (node == null || out.size >= maxNodes) return
                val text = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                val viewId = node.viewIdResourceName?.substringAfterLast("/") ?: ""
                if (text.isNotEmpty() || desc.isNotEmpty()) {
                    val label = buildString {
                        if (text.isNotEmpty()) append("\"$text\"")
                        if (desc.isNotEmpty() && desc != text) append(" ($desc)")
                        if (viewId.isNotEmpty()) append(" [id:$viewId]")
                        if (node.isClickable) append(" [clickable]")
                        if (node.isEditable) append(" [editable]")
                    }
                    out.add(label)
                }
                for (i in 0 until node.childCount) {
                    if (out.size >= maxNodes) break
                    walk(node.getChild(i))
                }
            }
            walk(root)
            return out
        }

        fun clickByTextOrDescription(query: String): Boolean {
            val svc = instance ?: return false
            val root = svc.rootInActiveWindow ?: return false
            val q = query.trim()
            if (q.isEmpty()) return false

            val matches = root.findAccessibilityNodeInfosByText(q) ?: emptyList()
            for (node in matches) {
                if (performClickOnNodeOrParent(node)) return true
            }

            fun searchAndClick(node: AccessibilityNodeInfo?): Boolean {
                if (node == null) return false
                val t = node.text?.toString() ?: ""
                val d = node.contentDescription?.toString() ?: ""
                val id = node.viewIdResourceName ?: ""
                if (t.contains(q, ignoreCase = true) || d.contains(q, ignoreCase = true) || id.contains(q, ignoreCase = true)) {
                    if (performClickOnNodeOrParent(node)) return true
                }
                for (i in 0 until node.childCount) {
                    if (searchAndClick(node.getChild(i))) return true
                }
                return false
            }
            return searchAndClick(root)
        }

        private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
            var cur = node
            var depth = 0
            while (cur != null && depth < 6) {
                if (cur.isClickable) {
                    return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                cur = cur.parent
                depth++
            }
            return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        }

        fun fillTextIntoField(textToEnter: String, fieldHint: String = ""): Boolean {
            val svc = instance ?: return false
            val root = svc.rootInActiveWindow ?: return false
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    textToEnter
                )
            }

            fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isEditable) {
                    if (fieldHint.isBlank()) return node
                    val t = node.text?.toString().orEmpty()
                    val d = node.contentDescription?.toString().orEmpty()
                    val h = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        node.hintText?.toString().orEmpty()
                    } else ""
                    if (t.contains(fieldHint, true) || d.contains(fieldHint, true) || h.contains(fieldHint, true)) {
                        return node
                    }
                }
                for (i in 0 until node.childCount) {
                    val found = findEditable(node.getChild(i))
                    if (found != null) return found
                }
                return null
            }

            val target = findEditable(root) ?: findEditable(root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
            return if (target != null) {
                target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } else {
                false
            }
        }

        fun scrollScreen(forward: Boolean = true): Boolean {
            val svc = instance ?: return false
            val root = svc.rootInActiveWindow ?: return false
            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }

            fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isScrollable) return node
                for (i in 0 until node.childCount) {
                    val s = findScrollable(node.getChild(i))
                    if (s != null) return s
                }
                return null
            }

            val scrollable = findScrollable(root) ?: return false
            return scrollable.performAction(action)
        }

        fun navigateBack(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        fun navigateHome(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        lastEventTimestamp = System.currentTimeMillis()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) {
            event.packageName?.toString()?.let {
                if (it.isNotBlank()) lastPackageName = it
            }
            lastEventTimestamp = System.currentTimeMillis()
        }
    }

    override fun onInterrupt() {
        // Triggered when system interrupts accessibility feedback
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }
}
