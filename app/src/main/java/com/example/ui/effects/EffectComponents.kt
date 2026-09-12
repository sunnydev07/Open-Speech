package com.example.ui.effects

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BluePrimary
import kotlin.math.sin

/**
 * Clean, subtle audio wave visualizer for the recording screen.
 */
@Composable
fun SimpleAudioVisualizer(
    modifier: Modifier = Modifier,
    barCount: Int = 18,
    isRecording: Boolean = true,
    barColor: Color = BluePrimary,
    amplitude: Float = 0f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    Canvas(modifier = modifier.height(36.dp).fillMaxWidth()) {
        val spacing = 5.dp.toPx()
        val totalSpacing = spacing * (barCount - 1)
        val barWidth = (size.width - totalSpacing) / barCount
        val maxHeight = size.height

        for (i in 0 until barCount) {
            val factor = if (isRecording) {
                val wave = (sin(phase + i * 0.5f) + 1f) / 2f
                val ampBoost = amplitude.coerceIn(0f, 1f) * 0.5f
                (0.2f + 0.6f * wave + ampBoost).coerceIn(0.15f, 1f)
            } else {
                0.2f
            }

            val barHeight = maxHeight * factor
            val startX = i * (barWidth + spacing)
            val startY = (maxHeight - barHeight) / 2

            drawRoundRect(
                color = barColor.copy(alpha = if (isRecording) 0.85f else 0.3f),
                topLeft = Offset(startX, startY),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

/**
 * Clean, subtle micro-interaction modifier (slight 0.97 scale on tap).
 */
fun Modifier.subtleClick(onClick: () -> Unit): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "SubtleClickScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
}
