package com.example

import com.example.ai.CefrLevel
import com.example.ai.PromptLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptLibraryTest {
    @Test
    fun library_hasAtLeast20Prompts_acrossLevels() {
        assertTrue(PromptLibrary.prompts.size >= 20)
        val levels = PromptLibrary.prompts.map { it.level }.toSet()
        assertTrue(levels.containsAll(listOf(CefrLevel.A2, CefrLevel.B1, CefrLevel.B2, CefrLevel.C1)))
    }

    @Test
    fun nextSkipsRecentlyUsed() {
        val recent = PromptLibrary.prompts.take(5).map { it.id }.toSet()
        val next = PromptLibrary.next(excludeIds = recent, level = null)
        assertTrue(next.id !in recent)
    }

    @Test
    fun rotation_yieldsMostlyUniquePrompts() {
        val seen = mutableSetOf<String>()
        val recent = ArrayDeque<String>()
        val random = kotlin.random.Random(42)
        repeat(10) {
            val p = PromptLibrary.next(excludeIds = recent.toSet(), level = null, random = random)
            seen.add(p.id)
            recent.addLast(p.id)
            while (recent.size > 5) recent.removeFirst()
        }
        assertTrue("expected >=8 unique prompts, got ${seen.size}", seen.size >= 8)
    }

    @Test
    fun rotation_neverRepeatsWithinWindowOfSix() {
        val history = mutableListOf<String>()
        val random = kotlin.random.Random(7)
        repeat(24) {
            val p = PromptLibrary.next(
                excludeIds = history.takeLast(5).toSet(),
                level = null,
                random = random
            )
            history.add(p.id)
        }
        history.windowed(6).forEach { window ->
            assertTrue("prompt repeated within 6 draws: $window", window.toSet().size == 6)
        }
    }

    @Test
    fun nextRespectsLevel() {
        val next = PromptLibrary.next(excludeIds = emptySet(), level = CefrLevel.C1)
        assertEquals(CefrLevel.C1, next.level)
    }
}
