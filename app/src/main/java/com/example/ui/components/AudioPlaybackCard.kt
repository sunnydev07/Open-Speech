package com.example.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File

/**
 * Feature 1: Playback + Self-Listening (Schmidt 1990 Noticing Hypothesis).
 * Enables learners to play back their own recorded audio on the Result screen.
 */
@Composable
fun AudioPlaybackCard(
    audioFile: File?,
    modifier: Modifier = Modifier
) {
    if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
        return
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var prepared by remember { mutableStateOf(false) }

    // Initialize or release MediaPlayer
    DisposableEffect(audioFile) {
        val player = MediaPlayer()
        var ready = false
        try {
            player.setDataSource(audioFile.absolutePath)
            player.prepare()
            durationMs = player.duration
            ready = true
            player.setOnCompletionListener {
                isPlaying = false
                currentPositionMs = 0
            }
        } catch (e: Exception) {
            // If audio cannot be prepared, fail gracefully (hide controls via durationMs = 0)
        }
        mediaPlayer = player
        prepared = ready

        onDispose {
            // stop() throws IllegalStateException on a never-prepared player.
            runCatching {
                if (player.isPlaying) player.stop()
            }
            player.release()
            mediaPlayer = null
        }
    }

    // Ticker for playback progress slider
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                runCatching {
                    if (player.isPlaying) {
                        currentPositionMs = player.currentPosition
                    }
                }
            }
            delay(100)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("audio_playback_card"),
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
                            .background(BluePrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = BluePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Listen to Your Recording",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Schmidt's Noticing Principle: self-hearing drives correction",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Text(
                    text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BluePrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        mediaPlayer?.let { player ->
                            runCatching {
                                if (player.isPlaying) {
                                    player.pause()
                                    isPlaying = false
                                } else if (prepared) {
                                    player.start()
                                    isPlaying = true
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(BluePrimary)
                        .testTag("playback_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = SurfaceLight,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (prepared && durationMs > 0) {
                    Slider(
                        value = currentPositionMs.toFloat() / durationMs.toFloat(),
                        onValueChange = { frac ->
                            val targetMs = (frac * durationMs).toInt()
                            currentPositionMs = targetMs
                            runCatching { mediaPlayer?.seekTo(targetMs) }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("playback_seek_bar"),
                        colors = SliderDefaults.colors(
                            thumbColor = BluePrimary,
                            activeTrackColor = BluePrimary,
                            inactiveTrackColor = BorderLight
                        )
                    )
                } else {
                    Text(
                        text = "Recording could not be prepared for playback",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
