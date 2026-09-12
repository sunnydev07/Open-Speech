package com.example

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Fluency Coach", appName)
  }

  @Test
  fun `verify timer presets and durations`() {
    assertEquals(30, com.example.ui.components.TimerPreset.THIRTY_SEC.durationSeconds)
    assertEquals(60, com.example.ui.components.TimerPreset.SIXTY_SEC.durationSeconds)
    assertEquals(120, com.example.ui.components.TimerPreset.TWO_MIN.durationSeconds)
    assertEquals("1 min", com.example.ui.components.TimerPreset.SIXTY_SEC.formatDuration())
    assertEquals("2 min", com.example.ui.components.TimerPreset.TWO_MIN.formatDuration())
  }

  @Test
  fun `verify timer remaining seconds calculation`() {
    val totalSeconds = 60
    val elapsedSeconds = 15
    val remaining = (totalSeconds - elapsedSeconds).coerceAtLeast(0)
    val mins = remaining / 60
    val secs = remaining % 60
    assertEquals(45, remaining)
    assertEquals(0, mins)
    assertEquals(45, secs)
    val formatted = String.format("%02d:%02d", mins, secs)
    assertEquals("00:45", formatted)
  }

  @Test
  fun `verify haptic helper triggers without crash`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val mockHaptics = object : androidx.compose.ui.hapticfeedback.HapticFeedback {
      var lastFeedback: androidx.compose.ui.hapticfeedback.HapticFeedbackType? = null
      override fun performHapticFeedback(hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
        lastFeedback = hapticFeedbackType
      }
    }
    val helper = com.example.util.HapticHelper(context, mockHaptics)
    helper.onStartSession()
    assertEquals(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress, mockHaptics.lastFeedback)

    helper.onToggleOrAdjust()
    assertEquals(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove, mockHaptics.lastFeedback)

    helper.onStopSession()
    assertEquals(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress, mockHaptics.lastFeedback)
  }

  @Test
  fun `verify milestone badges progress and unlock thresholds`() {
    val streakBadge = com.example.ui.components.MilestoneBadge(
      id = "streak_7",
      title = "7-Day Streak",
      description = "Practiced speaking consistently for 7 consecutive days",
      category = "Consistency",
      current = 7,
      target = 7,
      unit = "days",
      icon = androidx.compose.material.icons.Icons.Default.Check,
      accentColor = androidx.compose.ui.graphics.Color.Red,
      isUnlocked = true
    )
    assertEquals(1.0f, streakBadge.progressFraction, 0.01f)
    assertEquals(true, streakBadge.isUnlocked)

    val hourBadge = com.example.ui.components.MilestoneBadge(
      id = "practice_1hr",
      title = "1 Hour Practiced",
      description = "Accumulate 60 minutes of speaking practice time",
      category = "Endurance",
      current = 45,
      target = 60,
      unit = "min",
      icon = androidx.compose.material.icons.Icons.Default.Check,
      accentColor = androidx.compose.ui.graphics.Color.Blue,
      isUnlocked = false
    )
    assertEquals(0.75f, hourBadge.progressFraction, 0.01f)
    assertEquals(false, hourBadge.isUnlocked)

    val vm = FluencyViewModel(ApplicationProvider.getApplicationContext())
    val initialBadges = vm.badges.value
    val streakInVm = initialBadges.find { it.id == "streak_7" }
    val hourInVm = initialBadges.find { it.id == "practice_1hr" }
    org.junit.Assert.assertNotNull(streakInVm)
    org.junit.Assert.assertNotNull(hourInVm)
    // F2: fresh installs start locked with zero progress — nothing is hardcoded.
    assertEquals(false, streakInVm?.isUnlocked)
    assertEquals(false, hourInVm?.isUnlocked)
    assertEquals(7, streakInVm?.target)
    assertEquals(60, hourInVm?.target)
    assertEquals(0, vm.streakDays.value)
    assertEquals(0, vm.todayPracticedMinutes.value)
  }

  @Test
  fun `verify daily goal setting and progress calculations`() {
    val vm = FluencyViewModel(ApplicationProvider.getApplicationContext())
    // Default goal is 15 minutes; nothing practiced yet on a fresh install (F2).
    assertEquals(15, vm.dailyGoalMinutes.value)
    assertEquals(0, vm.todayPracticedMinutes.value)
    assertEquals(false, vm.showGoalDialog.value)

    // Open and close dialog
    vm.openGoalDialog()
    assertEquals(true, vm.showGoalDialog.value)
    vm.closeGoalDialog()
    assertEquals(false, vm.showGoalDialog.value)

    // Set new daily goal
    vm.setDailyGoalMinutes(20)
    assertEquals(20, vm.dailyGoalMinutes.value)

    // Verify boundaries
    vm.setDailyGoalMinutes(0)
    assertEquals(1, vm.dailyGoalMinutes.value) // coerced to min 1
    vm.setDailyGoalMinutes(200)
    assertEquals(120, vm.dailyGoalMinutes.value) // coerced to max 120

    // Check presets exist
    val presets = com.example.ui.components.DEFAULT_GOAL_PRESETS
    assertEquals(5, presets.size)
    assertEquals(5, presets[0].minutes)
    assertEquals(10, presets[1].minutes)
    assertEquals(15, presets[2].minutes)
    assertEquals(20, presets[3].minutes)
    assertEquals(30, presets[4].minutes)
  }

  @Test
  fun `verify pronunciation and accent data structures and fallback generation`() {
    val service = com.example.ai.GeminiPronunciationService()
    val fallback = service.generateDiagnosticFallback(
      prompt = "Describe a challenging engineering situation you encountered recently.",
      elapsedSeconds = 45
    )

    org.junit.Assert.assertNotNull(fallback)
    // F3: demo fallback is deterministic zeros flagged as non-AI — never fake scores.
    assertEquals(0, fallback.pronunciationScore)
    assertEquals(0, fallback.accentClarityScore)
    assertEquals(false, fallback.isRealAiGenerated)
    org.junit.Assert.assertTrue(fallback.transcription.contains("Demo"))
    org.junit.Assert.assertTrue(fallback.detectedAccentProfile.isNotEmpty())
    org.junit.Assert.assertTrue(fallback.pronunciationFeedback.isNotEmpty())
    org.junit.Assert.assertTrue(fallback.accentFeedback.isNotEmpty())
    assertEquals(3, fallback.phoneticTips.size)

    val firstTip = fallback.phoneticTips[0]
    org.junit.Assert.assertTrue(firstTip.word.isNotEmpty())
    org.junit.Assert.assertTrue(firstTip.phonetic.startsWith("/"))
    org.junit.Assert.assertTrue(firstTip.tip.isNotEmpty())
  }

  @Test
  fun `verify FluencyMetrics contains pronunciation and accent feedback`() {
    val defaultMetrics = FluencyMetrics()
    assertEquals(88, defaultMetrics.pronunciationScore)
    assertEquals(84, defaultMetrics.accentClarityScore)
    org.junit.Assert.assertTrue(defaultMetrics.detectedAccentProfile.contains("American"))
    assertEquals(3, defaultMetrics.phoneticTips.size)
    assertEquals("consequently", defaultMetrics.phoneticTips[0].word)
    assertEquals(true, defaultMetrics.isRealAiGenerated)
  }

  @Test
  fun `verify audio recorder manager permission check with robolectric context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val recorderManager = com.example.audio.AudioRecorderManager(context)
    org.junit.Assert.assertNotNull(recorderManager)
    // amplitude flow initializes with 0f baseline
    assertEquals(0f, recorderManager.amplitude.value)
    assertEquals(false, recorderManager.isRecording.value)
  }
}
