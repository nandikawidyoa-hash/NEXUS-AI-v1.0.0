package com.nexus.ai.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexus.ai.data.NexusViewModel
import com.nexus.ai.screens.*

sealed class MainTab(val route: String, val label: String, val icon: ImageVector) {
    object Home : MainTab("home", "Home", Icons.Default.Home)
    object Chat : MainTab("chat", "Chat", Icons.Default.Chat)
    object Tasks : MainTab("tasks", "Workspace", Icons.Default.List)
    object Skills : MainTab("skills", "Skills", Icons.Default.Build)
    object Settings : MainTab("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun NexusAppShell(vm: NexusViewModel) {
    var currentRoute by remember { mutableStateOf("home") }
    val tabs = listOf(MainTab.Home, MainTab.Chat, MainTab.Tasks, MainTab.Skills, MainTab.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { currentRoute = tab.route },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            when (currentRoute) {
                "home" -> HomeScreen(vm = vm, onNavigate = { currentRoute = it })
                "chat" -> ChatScreen(vm = vm)
                "tasks" -> TasksScreen(vm = vm)
                "skills" -> SkillsScreen(vm = vm)
                "settings" -> SettingsScreen(vm = vm, onNavigate = { currentRoute = it })
                "autonomous" -> TasksScreen(vm = vm)
                "marketing" -> MarketingStudioScreen(vm = vm, onBack = { currentRoute = "home" })
                "video" -> VideoGenScreen(vm = vm, onBack = { currentRoute = "home" })
                "diagnostics" -> DiagnosticsScreen(vm = vm, onBack = { currentRoute = "home" })
                "tools" -> ToolManagerScreen(vm = vm, onBack = { currentRoute = "home" })
                "memory" -> MemoryManagerScreen(vm = vm, onBack = { currentRoute = "home" })
                "automations" -> AutomationCenterScreen(vm = vm, onBack = { currentRoute = "home" })
                "device" -> DeviceManagerScreen(vm = vm, onBack = { currentRoute = "home" })
                "providers" -> ProviderManagerScreen(vm = vm, onBack = { currentRoute = "home" })
                "logs" -> ActivityLogsScreen(vm = vm, onBack = { currentRoute = "home" })
                "workspace" -> WorkspaceExplorerScreen(vm = vm, onBack = { currentRoute = "home" })
                "approvals" -> ApprovalCenterScreen(vm = vm, onBack = { currentRoute = "home" })
                "cloud_runtime" -> CloudRuntimeScreen(vm = vm, onBack = { currentRoute = "home" })
                else -> HomeScreen(vm = vm, onNavigate = { currentRoute = it })
            }
        }
    }
}
