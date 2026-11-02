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
    val requiredTools: List<String> = emptyList(),
    val status: String = if (enabled) "enabled" else "disabled",
    val folderPath: String = "",
    val instructions: String = "",
    val rawContent: String = "",
    val securityNotes: String = "",
    val requiredPermissions: List<String> = emptyList(),
    val validationErrors: List<String> = emptyList(),
    val missingTools: List<String> = emptyList()
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

data class AutomationQueueTaskModel(
    val id: String,
    val title: String,
    val command: String,
    val actionType: String,
    val targetApp: String,
    val status: String,
    val progressPercent: Int,
    val attempt: Int,
    val maxRetries: Int,
    val resultMessage: String,
    val errorMessage: String = "",
    val logs: List<String> = emptyList(),
    val createdAt: String = ""
)

data class VideoJobModel(
    val id: String,
    val providerId: String,
    val providerName: String,
    val modelName: String,
    val mode: String,
    val prompt: String,
    val enhancedPrompt: String,
    val imageUrl: String = "",
    val durationSec: Int = 5,
    val aspectRatio: String = "16:9",
    val resolution: String = "720p",
    val remoteJobId: String = "",
    val status: String = "queued",
    val progressPercent: Int = 0,
    val videoUrl: String = "",
    val localFilePath: String = "",
    val subtitlePath: String = "",
    val storyboardSummary: String = "",
    val variationsSummary: String = "",
    val errorMessage: String = "",
    val createdAt: String = ""
)

data class SpecialistAgentModel(
    val id: String,
    val name: String,
    val role: String,
    val tools: List<String> = emptyList()
)

data class AutonomousStepModel(
    val stepNumber: Int,
    val agentId: String,
    val agentName: String,
    val actionType: String,
    val title: String,
    val description: String,
    val autonomyTier: String = "LEVEL_1_AUTONOMOUS",
    val status: String = "pending",
    val outputSummary: String = "",
    val artifactPath: String = ""
)

data class AutonomousRunModel(
    val id: String,
    val goal: String,
    val status: String,
    val activeAgent: String,
    val currentStep: Int,
    val totalSteps: Int,
    val progressPercent: Int,
    val autonomyLevel: String = "LEVEL_2_CONTROLLED",
    val selfEvalScore: Int = 96,
    val selfEvalNotes: String = "",
    val finalReport: String = "",
    val errorMessage: String = "",
    val steps: List<AutonomousStepModel> = emptyList(),
    val generatedFiles: List<String> = emptyList(),
    val createdAt: String = ""
)

data class MarketingCampaignModel(
    val id: String,
    val brandName: String,
    val targetAudience: String,
    val competitors: String,
    val durationDays: Int = 30,
    val platformFocus: String = "TikTok, Instagram, Web",
    val strategySummary: String = "",
    val calendarPreview: List<String> = emptyList(),
    val captionsPreview: List<String> = emptyList(),
    val videoScript: String = "",
    val reportFilePath: String = "",
    val createdAt: String = ""
)

data class DiagnosticCheckItem(
    val id: String,
    val category: String,
    val title: String,
    val statusLabel: String,
    val passed: Boolean,
    val detail: String
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
