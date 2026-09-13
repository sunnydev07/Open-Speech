package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.OpenSpeechTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun dashboard_screenshot() {
    composeTestRule.setContent { OpenSpeechTheme { OpenSpeechApp() } }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/dashboard.png")
  }

  @Test
  fun recording_screenshot() {
    composeTestRule.setContent {
      OpenSpeechTheme {
        RecordingScreen(
          prompt = "Describe a challenging situation you overcame at work or school, and what you learned from it.",
          totalDuration = 60,
          elapsedSeconds = 24,
          isPaused = false,
          amplitude = 0.65f,
          hasRecordPermission = true,
          onTogglePause = {},
          onAddSeconds = {},
          onTick = {},
          onStop = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/recording.png")
  }

  @Test
  fun analyzing_screenshot() {
    composeTestRule.setContent {
      OpenSpeechTheme {
        AnalyzingScreen(
          stepMessage = "Evaluating pronunciation phonemes, syllable stress & accent..."
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/analyzing.png")
  }

  @Test
  fun result_screenshot() {
    composeTestRule.setContent {
      OpenSpeechTheme {
        ResultScreen(
          metrics = SpeechMetrics(),
          dailyGoalMinutes = 15,
          todayPracticedMinutes = 7,
          recentBadge = null,
          onBadgeClick = {},
          onPracticeAgain = {},
          onBack = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/result.png")
  }

  @Test
  fun redrill_screenshot() {
    val corrections = listOf(
      com.example.ai.SentenceCorrection(
        ownSentence = "I have been working here since three years.",
        correctedSentence = "I have been working here for three years.",
        rule = "Use 'for' (not 'since') with a length of time."
      ),
      com.example.ai.SentenceCorrection(
        ownSentence = "He go to school every day.",
        correctedSentence = "He goes to school every day.",
        rule = "Third-person singular takes -s."
      )
    )
    composeTestRule.setContent {
      OpenSpeechTheme {
        androidx.compose.foundation.layout.Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
        ) {
          com.example.ui.components.RedrillResultBanner(
            attemptNumber = 1,
            baselineScore = 78,
            currentScore = 84,
            baselineAccuracy = 88,
            currentAccuracy = 94,
            baselineWpm = 118,
            currentWpm = 126
          )
          androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.height(12.dp)
          )
          corrections.forEachIndexed { index, correction ->
            com.example.ui.components.SentenceCorrectionCard(
              correction = correction,
              index = index,
              attemptCount = if (index == 0) 1 else 0,
              onPractice = {}
            )
            androidx.compose.foundation.layout.Spacer(
              modifier = Modifier.height(10.dp)
            )
          }
        }
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/redrill.png")
  }

  @Test
  fun daily_goal_dialog_screenshot() {
    composeTestRule.setContent {
      OpenSpeechTheme {
        com.example.ui.components.DailyGoalDialog(
          currentGoalMinutes = 15,
          onSaveGoal = {},
          onDismiss = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/goal_dialog.png")
  }

  @Test
  fun milestone_badge_dialog_screenshot() {
    composeTestRule.setContent {
      OpenSpeechTheme {
        com.example.ui.components.MilestoneDetailDialog(
          badge = com.example.ui.components.MilestoneBadge(
            id = "streak_7",
            title = "7-Day Streak",
            description = "Practiced speaking consistently for 7 consecutive days without skipping.",
            category = "Consistency",
            current = 7,
            target = 7,
            unit = "days",
            icon = Icons.Default.Whatshot,
            accentColor = androidx.compose.ui.graphics.Color(0xFFEA580C),
            isUnlocked = true,
            unlockedDate = "Yesterday"
          ),
          onDismiss = {}
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/badge_dialog.png")
  }
}
