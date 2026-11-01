package com.nexus.ai.components
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.nexus.ai.theme.AccentBlueLight
import com.nexus.ai.theme.AmberWarn
import com.nexus.ai.theme.CrimsonDanger
import com.nexus.ai.theme.EmeraldOk

@Composable
fun NexusCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun RiskBadge(riskLevel: String) {
    val c = when (riskLevel.lowercase()) { "critical", "high" -> CrimsonDanger; "medium" -> AmberWarn; else -> EmeraldOk }
    Box(modifier = Modifier.background(c.copy(alpha = 0.18f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(text = riskLevel.uppercase(), style = MaterialTheme.typography.labelSmall, color = c)
    }
}

@Composable
fun NexusAgentCharacterCard(isWorking: Boolean, currentPhaseTitle: String, activeModelName: String) {
    val inf = rememberInfiniteTransition(label = "droid")
    val dy by inf.animateFloat(-6f, 6f, infiniteRepeatable(tween(if (isWorking) 500 else 1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dy")
    val ang by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if (isWorking) 1600 else 5500, easing = LinearEasing), RepeatMode.Restart), label = "ang")
    val ex by inf.animateFloat(-7f, 7f, infiniteRepeatable(tween(if (isWorking) 420 else 2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ex")
    val pCol = if (isWorking) AccentBlueLight else EmeraldOk
    val sCol = if (isWorking) AmberWarn else AccentBlueLight
    NexusCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f; val cy = (size.height / 2f) + dy
                    drawCircle(pCol.copy(alpha = if (isWorking) 0.22f else 0.1f), size.minDimension / 2.1f, Offset(cx, cy))
                    rotate(ang, Offset(cx, cy)) {
                        val r = size.minDimension / 2.3f
                        drawCircle(pCol.copy(alpha = 0.7f), r, Offset(cx, cy), style = Stroke(if (isWorking) 4f else 2f))
                        drawCircle(sCol, if (isWorking) 7f else 4f, Offset(cx + r, cy))
                        drawCircle(pCol, if (isWorking) 6f else 3f, Offset(cx - r, cy))
                    }
                    drawLine(pCol, Offset(cx, cy - 24f), Offset(cx, cy - 36f), strokeWidth = 4f)
                    drawCircle(sCol, if (isWorking) 6.5f else 4.5f, Offset(cx, cy - 38f))
                    drawRoundRect(Color(0xFF1E293B), Offset(cx - 25f, cy - 22f), Size(50f, 44f), CornerRadius(14f, 14f))
                    drawRoundRect(pCol, Offset(cx - 25f, cy - 22f), Size(50f, 44f), CornerRadius(14f, 14f), style = Stroke(3f))
                    drawRoundRect(Color(0xFF090D16), Offset(cx - 19f, cy - 14f), Size(38f, 17f), CornerRadius(7f, 7f))
                    val eo = if (isWorking) ex else ex * 0.25f
                    drawCircle(pCol, 4.2f, Offset(cx - 8f + eo, cy - 5.5f))
                    drawCircle(pCol, 4.2f, Offset(cx + 8f + eo, cy - 5.5f))
                    drawRoundRect(sCol, Offset(cx - 11f, cy + 9f), Size(22f, 5f), CornerRadius(3f, 3f))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isWorking) "NEXUS Cyber-Droid Sedang Bekerja..." else "NEXUS Cyber-Droid Standby", style = MaterialTheme.typography.titleMedium, color = pCol)
                Spacer(Modifier.height(3.dp))
                Text(currentPhaseTitle, style = MaterialTheme.typography.bodyMedium)
                Text("Model: $activeModelName", style = MaterialTheme.typography.labelSmall)
                if (isWorking) { Spacer(Modifier.height(5.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = pCol) }
            }
        }
    }
}

@Composable
fun EmergencyStopBanner(agentStopped: Boolean, onStopClick: () -> Unit, onResumeClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(if (agentStopped) CrimsonDanger.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(if (agentStopped) "AGENT DIHENTIKAN" else "Kendali Keamanan Agent Aktif", style = MaterialTheme.typography.titleMedium)
            Text(if (agentStopped) "Seluruh tugas ditangguhkan." else "Mode Semi-Autonomous berjalan normal.", style = MaterialTheme.typography.bodyMedium)
        }
        if (agentStopped) Button(onClick = onResumeClick) { Text("Aktifkan") }
        else Button(onClick = onStopClick, colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)) { Text("Stop Agent", color = Color.White) }
    }
}
