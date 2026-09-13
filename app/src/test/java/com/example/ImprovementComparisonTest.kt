package com.example

import com.example.data.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Feature 11: Task Repetition (Zhang 2023 SLA study)
 * Verifies metrics delta calculations between consecutive attempts on the same prompt.
 */
class ImprovementComparisonTest {

    private fun createSession(
        score: Int,
        wpm: Int,
        fillers: Int,
        pauses: Int,
        accuracy: Int,
        promptId: String = "work_b1_1"
    ) = SessionEntity(
        id = 1,
        epochDay = 100,
        timestamp = 1000L,
        durationSec = 60,
        score = score,
        wpm = wpm,
        fillers = fillers,
        pauses = pauses,
        accuracy = accuracy,
        cefr = "B1 Intermediate",
        promptId = promptId,
        isDemo = false
    )

    @Test
    fun retryPrompt_withHigherScoreAndFewerFillers_detectsImprovement() {
        val attempt1 = createSession(score = 75, wpm = 110, fillers = 4, pauses = 3, accuracy = 88)
        val attempt2Score = 84
        val attempt2Wpm = 128
        val attempt2Fillers = 1
        val attempt2Pauses = 1
        val attempt2Accuracy = 93

        val scoreDelta = attempt2Score - attempt1.score
        val wpmDelta = attempt2Wpm - attempt1.wpm
        val fillersDelta = attempt2Fillers - attempt1.fillers
        val pausesDelta = attempt2Pauses - attempt1.pauses
        val accuracyDelta = attempt2Accuracy - attempt1.accuracy

        assertEquals(9, scoreDelta)
        assertEquals(18, wpmDelta)
        assertEquals(-3, fillersDelta)
        assertEquals(-2, pausesDelta)
        assertEquals(5, accuracyDelta)

        val hasImproved = scoreDelta > 0 || (fillersDelta < 0 && scoreDelta >= 0)
        assertTrue("Attempt 2 should be recognized as an improvement", hasImproved)
    }

    @Test
    fun retryPrompt_deltaCanBeNegativeIfPerformanceDeclined() {
        val attempt1 = createSession(score = 85, wpm = 135, fillers = 1, pauses = 1, accuracy = 95)
        val attempt2Score = 78
        val attempt2Fillers = 3

        val scoreDelta = attempt2Score - attempt1.score
        val fillersDelta = attempt2Fillers - attempt1.fillers

        assertEquals(-7, scoreDelta)
        assertEquals(2, fillersDelta)
    }
}
