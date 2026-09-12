package com.example.ai

/**
 * CEFR level derived from a small rubric instead of a single score threshold (F5).
 * Pure function — unit tested in [com.example.CefrMapperTest].
 */
enum class CefrLevel(val label: String) {
    A2("A2 Elementary"),
    B1("B1 Intermediate"),
    B2("B2 Upper Intermediate"),
    C1("C1 Advanced");

    fun downgrade(): CefrLevel = when (this) {
        C1 -> B2
        B2 -> B1
        B1 -> A2
        A2 -> A2
    }
}

object CefrMapper {
    /**
     * Base level from overall score, then at most one step down for weak
     * grammar accuracy / hesitation, and one step down for off-target pacing.
     * WPM of 0 means "unknown" (e.g. demo mode) and is ignored.
     */
    fun mapToCefr(score: Int, accuracy: Int, wpm: Int, pauses: Int): CefrLevel {
        var level = when {
            score >= 90 -> CefrLevel.C1
            score >= 75 -> CefrLevel.B2
            score >= 60 -> CefrLevel.B1
            else -> CefrLevel.A2
        }
        if (accuracy < 80 || pauses > 5) {
            level = level.downgrade()
        }
        if (wpm > 0 && (wpm < 90 || wpm > 170)) {
            level = level.downgrade()
        }
        return level
    }
}
