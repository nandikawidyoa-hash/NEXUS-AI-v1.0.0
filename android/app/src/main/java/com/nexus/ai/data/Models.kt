package com.nexus.ai.data

data class TaskStepModel(
    val stepNumber: Int,
    val phase: String,
    val title: String,
    val description: String,
    val status: String,
    val riskLevel: String,
    val output: String? = null
)

data class TaskItemModel(
    val id: String,
    val title: String,
    val prompt: String,
    val runtime: String,
    val status: String,
    val riskLevel: String,
    val modelId: String,
    val resultSummary: String?,
    val errorMessage: String?,
    val recoverySteps: String?,
    val createdAt: String,
    val steps: List<TaskStepModel> = emptyList()
)

data class ChatMessageModel(
    val id: String,
    val role: String,
    val content: String,
    val modelUsed: String? = null,
    val createdAt: String = ""
)

data class ProviderItemModel(
    val id: String,
    val name: String,
    val providerType: String,
    val baseUrl: String,
    val keyMasked: String,
    val enabled: Boolean,
    val priority: Int,
    val lastTestStatus: String
)

data class AiModelItem(
    val id: String,
    val providerId: String,
    val providerName: String,
    val modelName: String,
    val displayName: String,
    val isDefault: Boolean,
    val enabled: Boolean
)

data class SkillItemModel(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val enabled: Boolean,
    val isBuiltin: Boolean,
    val securityStatus: String,
    val requiredTools: List<String> = emptyList()
)

data class ToolItemModel(
    val name: String,
    val category: String,
    val riskLevel: String,
    val description: String
)

data class MemoryItemModel(
    val id: String,
    val category: String,
    val key: String,
    val content: String,
    val importance: Int,
    val syncedToCloud: Boolean,
    val updatedAt: String
)

data class AutomationItemModel(
    val id: String,
    val name: String,
    val prompt: String,
    val scheduleCron: String,
    val intervalSeconds: Int,
    val runtime: String,
    val enabled: Boolean,
    val lastStatus: String
)

data class ApprovalItemModel(
    val id: String,
    val taskId: String?,
    val actionType: String,
    val riskLevel: String,
    val description: String,
    val status: String,
    val requestedAt: String
)

data class ActivityLogModel(
    val id: String,
    val level: String,
    val category: String,
    val action: String,
    val runtime: String,
    val createdAt: String
)

data class WorkspaceFileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long
)
