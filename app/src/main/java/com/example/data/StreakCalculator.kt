package com.example.data

import kotlin.math.max

/**
 * Pure date-based streak calculator (F2). A streak is the count of consecutive
 * epoch-days with at least one session, ending today or yesterday (so a streak
 * is not broken before the user has had a chance to practice today).
 * Unit tested in [com.example.StreakCalculatorTest].
 */
object StreakCalculator {
    fun computeStreak(activeEpochDays: Collection<Long>, todayEpochDay: Long): Int {
        if (activeEpochDays.isEmpty()) return 0
        val days = activeEpochDays.toSet()
        // Streak must include today or yesterday, otherwise it is broken.
        var cursor = if (todayEpochDay in days) todayEpochDay else todayEpochDay - 1
        if (cursor !in days) return 0
        var streak = 0
        while (cursor in days) {
            streak++
            cursor--
        }
        return max(streak, 0)
    }
}
