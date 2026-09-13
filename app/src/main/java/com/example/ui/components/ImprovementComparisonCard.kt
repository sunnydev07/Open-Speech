package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SessionEntity
import com.example.ui.theme.*

/**
 * Feature 11: Retry This Prompt with Improvement Tracking (Zhang 2023 SLA task repetition).
 * Compares current attempt metrics with previous attempt on the exact same prompt.
 */
@Composable
fun ImprovementComparisonCard(
    currentScore: Int,
    currentWpm: Int,
    currentFillers: Int,
    currentPauses: Int,
    currentAccuracy: Int,
    previousSession: SessionEntity,
    modifier: Modifier = Modifier
) {
    val scoreDelta = currentScore - previousSession.score
    val wpmDelta = currentWpm - previousSession.wpm
    val fillersDelta = currentFillers - previousSession.fillers
    val pausesDelta = currentPauses - previousSession.pauses
    val accuracyDelta = currentAccuracy - previousSession.accuracy

    val hasImproved = scoreDelta > 0 || (fillersDelta < 0 && scoreDelta >= 0) || (wpmDelta > 0 && currentWpm in 120..150)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("improvement_comparison_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.5.dp, if (hasImproved) SuccessGreen.copy(alpha = 0.4f) else BluePrimary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(if (hasImproved) SuccessGreen.copy(alpha = 0.12f) else BluePrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (hasImproved) Icons.Default.AutoAwesome else Icons.Default.Repeat,
                            contentDescription = null,
                            tint = if (hasImproved) SuccessGreen else BluePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Re-Attempt Progress",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Task Repetition vs Previous Attempt",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasImproved) SuccessGreen.copy(alpha = 0.12f) else BluePrimary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = if (hasImproved) "Progress Detected" else "Consistent Practice",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasImproved) SuccessGreen else BluePrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Comparison Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ComparisonStatItem(
                    label = "Score",
                    previous = "${previousSession.score}",
                    current = "$currentScore",
                    delta = scoreDelta,
                    isPositiveBetter = true,
                    modifier = Modifier.weight(1f)
                )
                ComparisonStatItem(
                    label = "Pacing",
                    previous = "${previousSession.wpm}",
                    current = "$currentWpm",
                    unit = "WPM",
                    delta = wpmDelta,
                    isPositiveBetter = currentWpm in 100..160,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ComparisonStatItem(
                    label = "Fillers",
                    previous = "${previousSession.fillers}",
                    current = "$currentFillers",
                    delta = fillersDelta,
                    isPositiveBetter = false, // fewer fillers is better!
                    modifier = Modifier.weight(1f)
                )
                ComparisonStatItem(
                    label = "Pauses",
                    previous = "${previousSession.pauses}",
                    current = "$currentPauses",
                    delta = pausesDelta,
                    isPositiveBetter = false, // fewer long pauses is better!
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scientific Insight Callout
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BackgroundLight,
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💡",
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (hasImproved) {
                            "Repeating this prompt reduced cognitive hesitation and improved fluency flow (Zhang 2023 SLA study)."
                        } else {
                            "Repetition builds muscle memory for complex phrasings. Try again to lock in pronunciation clarity."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonStatItem(
    label: String,
    previous: String,
    current: String,
    unit: String = "",
    delta: Int,
    isPositiveBetter: Boolean,
    modifier: Modifier = Modifier
) {
    val isFavorable = if (isPositiveBetter) delta > 0 else delta < 0
    val isUnfavorable = if (isPositiveBetter) delta < 0 else delta > 0
    val deltaColor = when {
        isFavorable -> SuccessGreen
        isUnfavorable -> DangerRed
        else -> TextMuted
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = BackgroundLight,
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$previous → $current${if (unit.isNotBlank()) " $unit" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                if (delta != 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (delta > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = deltaColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${if (delta > 0) "+" else ""}$delta",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = deltaColor
                        )
                    }
                } else {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
