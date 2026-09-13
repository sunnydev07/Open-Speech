package com.example

import com.example.util.ShareProgressHelper
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Feature 18: Share Progress & Social Accountability (Dörnyei 2001)
 * Verifies that the generated share text contains essential learning metrics and links.
 */
class ShareProgressHelperTest {

    @Test
    fun generateShareText_containsScoreCefrAndStreak() {
        val metrics = SpeechMetrics(
            score = 88,
            cefr = "B2 Upper Intermediate",
            wpm = 135,
            fillers = 1,
            pauses = 2,
            accuracy = 94,
            pronunciationScore = 89,
            accentClarityScore = 86
        )

        val text = ShareProgressHelper.generateShareText(metrics, streakDays = 7)

        assertTrue(text.contains("88/100"))
        assertTrue(text.contains("B2 Upper Intermediate"))
        assertTrue(text.contains("135 WPM"))
        assertTrue(text.contains("7-Day Practice Streak"))
        assertTrue(text.contains("https://sunnydev07.github.io/Open-Speech/"))
    }

    @Test
    fun generateShareText_withZeroStreak_omitsStreakLineGracefully() {
        val metrics = SpeechMetrics(
            score = 80,
            cefr = "B1 Intermediate",
            wpm = 120,
            fillers = 2,
            pauses = 3,
            accuracy = 90
        )

        val text = ShareProgressHelper.generateShareText(metrics, streakDays = 0)

        assertTrue(text.contains("80/100"))
        assertTrue(!text.contains("0-Day Practice Streak"))
    }
}
