package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Provides rich, tactile haptic feedback for timer actions (start, stop, pause, resume, extend).
 * Combines Compose's LocalHapticFeedback with system Vibrator for consistent tactile confirmation across devices.
 */
class HapticHelper(
    private val context: Context,
    private val composeHaptics: HapticFeedback
) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Tactile confirmation when beginning a speaking practice session.
     */
    fun onStartSession() {
        composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Heavier, double-pulse tactile confirmation when stopping/finishing a practice session.
     */
    fun onStopSession() {
        composeHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(80)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Crisp, subtle tick for pausing, resuming, or extending time.
     */
    fun onToggleOrAdjust() {
        composeHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25)
            }
        } catch (_: Throwable) {}
    }
}

@Composable
fun rememberHapticHelper(): HapticHelper {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    return remember(context, hapticFeedback) {
        HapticHelper(context, hapticFeedback)
    }
}
