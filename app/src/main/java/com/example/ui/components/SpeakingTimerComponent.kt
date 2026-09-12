package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.effects.subtleClick
import com.example.ui.theme.*
import com.example.util.rememberHapticHelper

/**
 * Speaking duration presets for practice sessions.
 */
enum class TimerPreset(val durationSeconds: Int, val label: String, val subtitle: String) {
    THIRTY_SEC(30, "30s", "Quick Drill"),
    SIXTY_SEC(60, "60s", "Standard"),
    NINETY_SEC(90, "90s", "Detailed"),
    TWO_MIN(120, "2m", "IELTS Part 2"),
    FREE_FLOW(0, "Open", "Stopwatch");

    fun formatDuration(): String {
        if (durationSeconds == 0) return "Free-form"
        val mins = durationSeconds / 60
        val secs = durationSeconds % 60
        return if (mins > 0 && secs == 0) "$mins min" else "$durationSeconds sec"
    }
}

/**
 * Modern, clean speaking timer component.
 * Displays the countdown or elapsed time, visual circular track, pause/resume,
 * and quick time extensions (+15s) to help users manage their speaking pacing effectively.
 */
@Composable
fun SpeakingTimerComponent(
    totalDuration: Int, // 0 for count up (stopwatch), >0 for countdown
    elapsedSeconds: Int,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onAddSeconds: (Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCountDown = totalDuration > 0
    val remainingSeconds = if (isCountDown) (totalDuration - elapsedSeconds).coerceAtLeast(0) else elapsedSeconds
    val isOvertime = isCountDown && elapsedSeconds > totalDuration

    val progress = if (isCountDown) {
        (remainingSeconds.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        ((elapsedSeconds % 60).toFloat() / 60f)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
        label = "TimerProgress"
    )

    val ringColor by animateColorAsState(
        targetValue = when {
            isPaused -> WarningAmber
            isOvertime || (isCountDown && remainingSeconds <= 10) -> DangerRed
            isCountDown && remainingSeconds <= 20 -> WarningAmber
            else -> BluePrimary
        },
        label = "RingColor"
    )

    val hapticHelper = rememberHapticHelper()

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Circular Progress Display
        Box(
            modifier = Modifier
                .size(190.dp)
                .testTag("speaking_timer_display"),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = ringColor,
                strokeWidth = 8.dp,
                trackColor = BorderLight,
                strokeCap = StrokeCap.Round
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Smooth transition digital clock readout
                val displayMinutes = if (isCountDown) remainingSeconds / 60 else elapsedSeconds / 60
                val displaySeconds = if (isCountDown) remainingSeconds % 60 else elapsedSeconds % 60
                val isCountingDown = isCountDown && !isOvertime
                val textColor = if (isPaused) WarningAmber else TextPrimary

                AnimatedTimerDisplay(
                    minutes = displayMinutes,
                    seconds = displaySeconds,
                    isCountDown = isCountingDown,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                val statusText = when {
                    isPaused -> "PAUSED"
                    isOvertime -> "OVERTIME (+${elapsedSeconds - totalDuration}s)"
                    isCountDown -> "${remainingSeconds}s remaining"
                    else -> "Elapsed time"
                }

                AnimatedContent(
                    targetState = statusText,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(220))
                    },
                    label = "TimerStatusAnimation"
                ) { targetStatus ->
                    Text(
                        text = targetStatus.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = when {
                            isPaused -> WarningAmber
                            isOvertime || (isCountDown && remainingSeconds <= 10) -> DangerRed
                            else -> TextSecondary
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Timer Controls Row: Pause/Resume, +15s extension, and Finish
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pause / Resume Button
            OutlinedButton(
                onClick = {
                    hapticHelper.onToggleOrAdjust()
                    onTogglePause()
                },
                modifier = Modifier
                    .height(46.dp)
                    .testTag("timer_pause_toggle_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (isPaused) WarningAmber else BorderLight),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (isPaused) WarningAmber.copy(alpha = 0.1f) else SurfaceLight
                )
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    tint = if (isPaused) WarningAmber else TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPaused) "Resume" else "Pause",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isPaused) WarningAmber else TextPrimary
                )
            }

            if (isCountDown) {
                Spacer(modifier = Modifier.width(12.dp))

                // +15s Quick Extension
                OutlinedButton(
                    onClick = {
                        hapticHelper.onToggleOrAdjust()
                        onAddSeconds(15)
                    },
                    modifier = Modifier
                        .height(46.dp)
                        .testTag("timer_add_15s_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceLight)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add 15 seconds",
                        tint = BluePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "15s",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = BluePrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Finish Speaking Button
            Button(
                onClick = {
                    hapticHelper.onStopSession()
                    onStop()
                },
                modifier = Modifier
                    .height(46.dp)
                    .testTag("stop_recording_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Finish Speaking",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Finish",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

/**
 * Selector row for choosing target practice duration presets on the Dashboard.
 */
@Composable
fun TimerPresetSelector(
    selectedPreset: TimerPreset,
    onSelectPreset: (TimerPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticHelper = rememberHapticHelper()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = BluePrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Target Practice Duration",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                }
                Text(
                    text = selectedPreset.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimerPreset.values().forEach { preset ->
                    val isSelected = preset == selectedPreset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) BluePrimary else BackgroundLight)
                            .border(
                                BorderStroke(1.dp, if (isSelected) BluePrimary else BorderLight),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                hapticHelper.onToggleOrAdjust()
                                onSelectPreset(preset)
                            }
                            .testTag("preset_${preset.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) Color.White else TextPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders individual timer digits with smooth rolling vertical slide and fade transitions.
 * Prevents abrupt number jumping and horizontal layout shifts.
 */
@Composable
fun AnimatedTimerDisplay(
    minutes: Int,
    seconds: Int,
    isCountDown: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val minStr = String.format("%02d", minutes)
    val secStr = String.format("%02d", seconds)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Minutes digits
        AnimatedDigit(digit = minStr[0], isCountDown = isCountDown, color = color)
        AnimatedDigit(digit = minStr[1], isCountDown = isCountDown, color = color)

        // Steady Colon divider
        Text(
            text = ":",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                letterSpacing = (-1).sp
            ),
            color = color,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        // Seconds digits
        AnimatedDigit(digit = secStr[0], isCountDown = isCountDown, color = color)
        AnimatedDigit(digit = secStr[1], isCountDown = isCountDown, color = color)
    }
}

@Composable
private fun AnimatedDigit(
    digit: Char,
    isCountDown: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.widthIn(min = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = digit,
            transitionSpec = {
                if (isCountDown) {
                    // Counting down: New digit slides in from top, old exits down
                    (slideInVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(180)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180))
                        )
                } else {
                    // Counting up: New digit slides in from bottom, old exits up
                    (slideInVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(180))
                        )
                }.using(SizeTransform(clip = true))
            },
            label = "DigitRollAnimation"
        ) { targetChar ->
            Text(
                text = targetChar.toString(),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 44.sp,
                    letterSpacing = (-1).sp
                ),
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}
