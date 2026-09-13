package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*

/**
 * Feature 12: Post-Session Self-Assessment & Metacognition (Dörnyei 2005, Oxford 1990).
 * Prompts the learner to reflect on their own speaking performance before AI results are revealed.
 */
@Composable
fun SelfAssessmentDialog(
    onDismiss: () -> Unit,
    onSubmit: (fluency: Int, pronunciation: Int, confidence: Int) -> Unit
) {
    var fluencyRating by remember { mutableIntStateOf(3) }
    var pronunciationRating by remember { mutableIntStateOf(3) }
    var confidenceRating by remember { mutableIntStateOf(3) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("self_assessment_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BluePrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = BluePrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Self-Assessment",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "How did that attempt feel to you?",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Rating Dimension 1: Fluency & Flow
                RatingRow(
                    label = "Fluency & Pacing",
                    sublabel = "Smooth flow vs hesitation",
                    currentRating = fluencyRating,
                    onRatingChanged = { fluencyRating = it },
                    tag = "fluency"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Rating Dimension 2: Pronunciation
                RatingRow(
                    label = "Pronunciation Clarity",
                    sublabel = "Clean sounds vs stumbling",
                    currentRating = pronunciationRating,
                    onRatingChanged = { pronunciationRating = it },
                    tag = "pronunciation"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Rating Dimension 3: Confidence
                RatingRow(
                    label = "Speaking Confidence",
                    sublabel = "Relaxed command vs tension",
                    currentRating = confidenceRating,
                    onRatingChanged = { confidenceRating = it },
                    tag = "confidence"
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Submit Button
                Button(
                    onClick = {
                        onSubmit(fluencyRating, pronunciationRating, confidenceRating)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_self_assessment_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text(
                        text = "Submit & Reveal AI Score",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("skip_self_assessment_button")
                ) {
                    Text(
                        text = "Skip to Results",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RatingRow(
    label: String,
    sublabel: String,
    currentRating: Int,
    onRatingChanged: (Int) -> Unit,
    tag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rating_row_$tag")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Text(
                text = "$currentRating/5",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = BluePrimary
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 1..5) {
                IconButton(
                    onClick = { onRatingChanged(i) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("${tag}_star_$i")
                ) {
                    Icon(
                        imageVector = if (i <= currentRating) Icons.Default.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Rate $i",
                        tint = if (i <= currentRating) WarningAmber else TextMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Result screen card showing self-assessment calibration against AI scores.
 */
@Composable
fun SelfAssessmentCalibrationCard(
    selfFluency: Int,
    selfPronunciation: Int,
    selfConfidence: Int,
    aiScore: Int,
    modifier: Modifier = Modifier
) {
    val selfAveragePct = ((selfFluency + selfPronunciation + selfConfidence) / 15f * 100f).toInt()
    val gap = aiScore - selfAveragePct

    val (calibrationTitle, calibrationBody, badgeColor) = when {
        gap >= 15 -> Triple(
            "🌟 You're Better Than You Think!",
            "You rated yourself at $selfAveragePct%, but AI measured $aiScore%. Speaking anxiety frequently masks real competence (Dörnyei 2005). Trust your natural voice!",
            SuccessGreen
        )
        gap <= -15 -> Triple(
            "🎯 Calibration Opportunity",
            "You felt confident at $selfAveragePct%, while objective analysis measured $aiScore%. Check the phonetic drill-downs below to pinpoint subtle sound deviations.",
            WarningAmber
        )
        else -> Triple(
            "🎯 Excellent Self-Awareness",
            "Your self-evaluation ($selfAveragePct%) is closely aligned with the objective evaluation ($aiScore%). Accurate metacognitive monitoring accelerates fluency growth.",
            BluePrimary
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("self_assessment_calibration_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Metacognitive Calibration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "Self: $selfAveragePct% | AI: $aiScore%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = calibrationTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = calibrationBody,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Rating pills summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RatingPill(label = "Flow", stars = selfFluency, modifier = Modifier.weight(1f))
                RatingPill(label = "Clarity", stars = selfPronunciation, modifier = Modifier.weight(1f))
                RatingPill(label = "Confidence", stars = selfConfidence, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RatingPill(
    label: String,
    stars: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = BackgroundLight,
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "★".repeat(stars) + "☆".repeat((5 - stars).coerceAtLeast(0)),
                fontSize = 11.sp,
                color = WarningAmber
            )
        }
    }
}
