package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.Edit
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
import com.example.util.rememberHapticHelper

/**
 * Preset configuration for daily practice goals.
 */
data class DailyGoalPreset(
    val minutes: Int,
    val title: String,
    val subtitle: String
)

val DEFAULT_GOAL_PRESETS = listOf(
    DailyGoalPreset(5, "Casual", "1-2 short drills"),
    DailyGoalPreset(10, "Steady", "Fluency maintenance"),
    DailyGoalPreset(15, "Focused", "Recommended"),
    DailyGoalPreset(20, "Intensive", "Fast pacing gains"),
    DailyGoalPreset(30, "Immersion", "Exam mastery")
)

/**
 * Visual card displaying today's progress toward the user's daily practice goal.
 */
@Composable
fun DailyGoalCard(
    practicedMinutes: Int,
    goalMinutes: Int,
    onEditGoal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticHelper = rememberHapticHelper()
    val targetProgress = if (goalMinutes > 0) {
        (practicedMinutes.toFloat() / goalMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "DailyGoalProgressAnimation"
    )

    val isGoalCompleted = practicedMinutes >= goalMinutes
    val remainingMinutes = (goalMinutes - practicedMinutes).coerceAtLeast(0)
    val percentage = (targetProgress * 100).toInt()

    val progressColor by animateColorAsState(
        targetValue = if (isGoalCompleted) SuccessGreen else BluePrimary,
        animationSpec = tween(durationMillis = 400),
        label = "ProgressColorAnimation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_goal_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, if (isGoalCompleted) SuccessGreen.copy(alpha = 0.4f) else BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            // Header Row with Icon, Title, and Edit button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isGoalCompleted) SuccessGreen.copy(alpha = 0.12f)
                                else BluePrimary.copy(alpha = 0.1f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isGoalCompleted) Icons.Default.CheckCircle else Icons.Default.TrackChanges,
                            contentDescription = "Daily Goal Icon",
                            tint = if (isGoalCompleted) SuccessGreen else BluePrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Daily Speaking Goal",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "$goalMinutes min / day target",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                // Edit Goal Button
                OutlinedButton(
                    onClick = {
                        hapticHelper.onToggleOrAdjust()
                        onEditGoal()
                    },
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("edit_daily_goal_button"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit Goal",
                        modifier = Modifier.size(14.dp),
                        tint = BluePrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Set Goal",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Metrics & Ratio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$practicedMinutes",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = if (isGoalCompleted) SuccessGreen else TextPrimary
                    )
                    Text(
                        text = " / $goalMinutes min today",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                    )
                }

                // Percentage Badge
                Surface(
                    color = (if (isGoalCompleted) SuccessGreen else BluePrimary).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isGoalCompleted) "Completed" else "$percentage%",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isGoalCompleted) SuccessGreen else BluePrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Custom Progress Bar Component
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .testTag("daily_goal_progress_bar"),
                color = progressColor,
                trackColor = BorderLight
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Motivational Feedback Footer
            Text(
                text = if (isGoalCompleted) {
                    "🎉 Daily goal achieved! You've maintained peak speaking momentum."
                } else {
                    "$remainingMinutes min remaining to complete today's speaking target."
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isGoalCompleted) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (isGoalCompleted) SuccessGreen else TextSecondary
            )
        }
    }
}

/**
 * Dialog allowing the user to select or customize their daily speaking practice goal.
 */
@Composable
fun DailyGoalDialog(
    currentGoalMinutes: Int,
    onSaveGoal: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val hapticHelper = rememberHapticHelper()
    var selectedMinutes by remember { mutableIntStateOf(currentGoalMinutes) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("daily_goal_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLight),
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dialog Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(BluePrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "Goal Setting",
                        tint = BluePrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Set Daily Goal",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )

                Text(
                    text = "Consistent daily speaking practice accelerates fluency and reduces speech hesitation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )

                // Interactive Stepper for Goal Minutes
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = BackgroundLight,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderLight)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minus Button
                        IconButton(
                            onClick = {
                                if (selectedMinutes > 1) {
                                    hapticHelper.onToggleOrAdjust()
                                    selectedMinutes = (selectedMinutes - 1).coerceAtLeast(1)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceLight)
                                .testTag("daily_goal_stepper_minus")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease Goal",
                                tint = if (selectedMinutes > 1) TextPrimary else TextMuted
                            )
                        }

                        // Display Minutes
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$selectedMinutes min",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BluePrimary
                                )
                            )
                            Text(
                                text = "per day",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        // Plus Button
                        IconButton(
                            onClick = {
                                if (selectedMinutes < 120) {
                                    hapticHelper.onToggleOrAdjust()
                                    selectedMinutes = (selectedMinutes + 1).coerceAtMost(120)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceLight)
                                .testTag("daily_goal_stepper_plus")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase Goal",
                                tint = TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Presets Label
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Preset Chips / Rows
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DEFAULT_GOAL_PRESETS.chunked(3).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val isSelected = selectedMinutes == preset.minutes
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) BluePrimary.copy(alpha = 0.12f)
                                            else BackgroundLight
                                        )
                                        .border(
                                            BorderStroke(
                                                1.5.dp,
                                                if (isSelected) BluePrimary else BorderLight
                                            ),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            hapticHelper.onToggleOrAdjust()
                                            selectedMinutes = preset.minutes
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp)
                                        .testTag("daily_goal_preset_${preset.minutes}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${preset.minutes}m",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) BluePrimary else TextPrimary
                                            )
                                        )
                                        Text(
                                            text = preset.title,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                color = if (isSelected) BluePrimary else TextMuted
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            hapticHelper.onToggleOrAdjust()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("daily_goal_cancel_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderLight)
                    ) {
                        Text("Cancel", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = {
                            hapticHelper.onStartSession()
                            onSaveGoal(selectedMinutes)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("daily_goal_save_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                    ) {
                        Text("Save Goal", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
