package com.example

import com.example.data.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {
    @Test
    fun emptyHistory_isZero() {
        assertEquals(0, StreakCalculator.computeStreak(emptyList(), todayEpochDay = 100))
    }

    @Test
    fun threeConsecutiveDays_endingToday_isThree() {
        assertEquals(3, StreakCalculator.computeStreak(listOf(98, 99, 100), todayEpochDay = 100))
    }

    @Test
    fun streakEndingYesterday_survives() {
        assertEquals(2, StreakCalculator.computeStreak(listOf(98, 99), todayEpochDay = 100))
    }

    @Test
    fun gapBreaksStreak_countsTailOnly() {
        assertEquals(1, StreakCalculator.computeStreak(listOf(90, 91, 100), todayEpochDay = 100))
    }

    @Test
    fun lastPracticeTwoDaysAgo_isZero() {
        assertEquals(0, StreakCalculator.computeStreak(listOf(97, 98), todayEpochDay = 100))
    }
}
