package com.nexus.ai.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexus.ai.theme.AmberWarn
import com.nexus.ai.theme.CrimsonDanger
import com.nexus.ai.theme.EmeraldOk

@Composable
fun NexusCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun RiskBadge(riskLevel: String) {
    val bgColor = when (riskLevel.lowercase()) {
        "critical", "high" -> CrimsonDanger.copy(alpha = 0.18f)
        "medium" -> AmberWarn.copy(alpha = 0.18f)
        else -> EmeraldOk.copy(alpha = 0.18f)
    }
    val textColor = when (riskLevel.lowercase()) {
        "critical", "high" -> CrimsonDanger
        "medium" -> AmberWarn
        else -> EmeraldOk
    }
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = riskLevel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
}

@Composable
fun EmergencyStopBanner(
    agentStopped: Boolean,
    onStopClick: () -> Unit,
    onResumeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (agentStopped) CrimsonDanger.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (agentStopped) "AGENT DIHENTIKAN (DARURAT)" else "Kendali Keamanan Agent Aktif",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (agentStopped) "Seluruh tugas ditangguhkan." else "Mode Semi-Autonomous berjalan normal.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (agentStopped) {
            Button(onClick = onResumeClick) {
                Text("Aktifkan")
            }
        } else {
            Button(
                onClick = onStopClick,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
            ) {
                Text("Stop Agent", color = Color.White)
            }
        }
    }
}
