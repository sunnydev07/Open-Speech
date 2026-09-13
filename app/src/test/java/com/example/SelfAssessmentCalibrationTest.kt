package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for Feature 12: Metacognitive Calibration (Dörnyei 2005, Oxford 1990)
 * Verifies self-assessment score percentage calculations and calibration categories.
 */
class SelfAssessmentCalibrationTest {

    private fun calculateSelfPercentage(fluency: Int, pronunciation: Int, confidence: Int): Int {
        return ((fluency + pronunciation + confidence) / 15f * 100f).toInt()
    }

    private fun getCalibrationStatus(selfPct: Int, aiScore: Int): String {
        val gap = aiScore - selfPct
        return when {
            gap >= 15 -> "UNDERESTIMATING"
            gap <= -15 -> "OVERESTIMATING"
            else -> "WELL_CALIBRATED"
        }
    }

    @Test
    fun maxRatings_gives100Percent() {
        val pct = calculateSelfPercentage(5, 5, 5)
        assertEquals(100, pct)
    }

    @Test
    fun minRatings_gives20Percent() {
        val pct = calculateSelfPercentage(1, 1, 1)
        assertEquals(20, pct)
    }

    @Test
    fun averageRatings_gives60Percent() {
        val pct = calculateSelfPercentage(3, 3, 3)
        assertEquals(60, pct)
    }

    @Test
    fun userRatesSelfLow_butAiScoreHigh_isUnderestimating() {
        val selfPct = calculateSelfPercentage(2, 3, 2) // (7/15)*100 = 46%
        val aiScore = 82
        val status = getCalibrationStatus(selfPct, aiScore)
        assertEquals("UNDERESTIMATING", status)
    }

    @Test
    fun userRatesSelfHigh_butAiScoreLow_isOverestimating() {
        val selfPct = calculateSelfPercentage(5, 4, 5) // (14/15)*100 = 93%
        val aiScore = 65
        val status = getCalibrationStatus(selfPct, aiScore)
        assertEquals("OVERESTIMATING", status)
    }

    @Test
    fun userSelfRatingMatchesAi_isWellCalibrated() {
        val selfPct = calculateSelfPercentage(4, 4, 4) // (12/15)*100 = 80%
        val aiScore = 84
        val status = getCalibrationStatus(selfPct, aiScore)
        assertEquals("WELL_CALIBRATED", status)
        assertTrue(abs(aiScore - selfPct) < 15)
    }
}
