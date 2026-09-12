package com.example.ai

/**
 * Prompt library replacing the single hardcoded `currentPrompt` (F9).
 * 24 prompts across A2–C1 x Work / School / Daily life / IELTS preparation.
 */
enum class PromptCategory(val label: String) {
    WORK("Work"),
    SCHOOL("School"),
    DAILY_LIFE("Daily life"),
    IELTS("IELTS prep")
}

data class PracticePrompt(
    val id: String,
    val text: String,
    val level: CefrLevel,
    val category: PromptCategory,
    val ieltsPart: Int? = null
)

object PromptLibrary {
    val prompts: List<PracticePrompt> = listOf(
        PracticePrompt("daily_a2_1", "Describe what you usually eat for breakfast and lunch.", CefrLevel.A2, PromptCategory.DAILY_LIFE),
        PracticePrompt("daily_a2_2", "Talk about your family members and what they do every day.", CefrLevel.A2, PromptCategory.DAILY_LIFE),
        PracticePrompt("daily_a2_3", "Describe your home: the rooms, your favourite room, and why you like it.", CefrLevel.A2, PromptCategory.DAILY_LIFE),
        PracticePrompt("school_a2_1", "Talk about your favourite subject at school and why you enjoy it.", CefrLevel.A2, PromptCategory.SCHOOL),
        PracticePrompt("work_a2_1", "Describe your job or your dream job and what you do every day.", CefrLevel.A2, PromptCategory.WORK),
        PracticePrompt("ielts_a2_1", "Talk for one minute about your hometown: where it is and what it is famous for.", CefrLevel.A2, PromptCategory.IELTS, ieltsPart = 1),
        PracticePrompt("daily_b1_1", "Describe a recent trip or outing: where you went, who you went with, and the best moment.", CefrLevel.B1, PromptCategory.DAILY_LIFE),
        PracticePrompt("daily_b1_2", "Talk about a hobby you enjoy and how you started it.", CefrLevel.B1, PromptCategory.DAILY_LIFE),
        PracticePrompt("school_b1_1", "Describe a teacher who influenced you and what you learned from them.", CefrLevel.B1, PromptCategory.SCHOOL),
        PracticePrompt("work_b1_1", "Describe a challenging situation you overcame at work or school, and what you learned from it.", CefrLevel.B1, PromptCategory.WORK),
        PracticePrompt("work_b1_2", "Explain how you plan your week to balance work, study, and rest.", CefrLevel.B1, PromptCategory.WORK),
        PracticePrompt("ielts_b1_1", "Talk for one minute about a gift you recently gave or received.", CefrLevel.B1, PromptCategory.IELTS, ieltsPart = 2),
        PracticePrompt("ielts_b1_2", "Do you prefer studying alone or with friends? Give reasons and examples.", CefrLevel.B1, PromptCategory.IELTS, ieltsPart = 3),
        PracticePrompt("daily_b2_1", "Discuss the advantages and disadvantages of living in a big city versus a small town.", CefrLevel.B2, PromptCategory.DAILY_LIFE),
        PracticePrompt("school_b2_1", "Should university education be free for everyone? Argue your position with examples.", CefrLevel.B2, PromptCategory.SCHOOL),
        PracticePrompt("work_b2_1", "Describe a time you had to convince your team to follow your idea. How did you do it?", CefrLevel.B2, PromptCategory.WORK),
        PracticePrompt("work_b2_2", "Talk about a failure at work or school and the systematic steps you took to recover.", CefrLevel.B2, PromptCategory.WORK),
        PracticePrompt("ielts_b2_1", "Describe a skill you would like to learn and explain how you would learn it.", CefrLevel.B2, PromptCategory.IELTS, ieltsPart = 2),
        PracticePrompt("ielts_b2_2", "Some people think technology has made communication worse. To what extent do you agree?", CefrLevel.B2, PromptCategory.IELTS, ieltsPart = 3),
        PracticePrompt("work_c1_1", "Evaluate how remote work has changed leadership: what must managers do differently now?", CefrLevel.C1, PromptCategory.WORK),
        PracticePrompt("school_c1_1", "Analyse whether standardised testing fairly measures a student's ability. Propose an alternative.", CefrLevel.C1, PromptCategory.SCHOOL),
        PracticePrompt("daily_c1_1", "Discuss how social media has reshaped friendships, with a personal example.", CefrLevel.C1, PromptCategory.DAILY_LIFE),
        PracticePrompt("ielts_c1_1", "Describe an important decision you made that affected your career or studies.", CefrLevel.C1, PromptCategory.IELTS, ieltsPart = 2),
        PracticePrompt("ielts_c1_2", "Economic growth is often prioritised over environmental protection. Is this justifiable?", CefrLevel.C1, PromptCategory.IELTS, ieltsPart = 3)
    )

    /**
     * Picks a random next prompt for [level], skipping recently used ids so sessions
     * don't repeat. Falls back to the full list when the level pool is exhausted.
     */
    fun next(
        excludeIds: Set<String>,
        level: CefrLevel? = null,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): PracticePrompt {
        val pool = if (level != null) prompts.filter { it.level == level } else prompts
        val available = pool.filter { it.id !in excludeIds }
            .ifEmpty { prompts.filter { it.id !in excludeIds } }
            .ifEmpty { pool }
        return available.random(random)
    }
}
