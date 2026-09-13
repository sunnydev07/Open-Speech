package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordDiffTest {

    @Test
    fun identicalSentences_allSame() {
        val (own, fixed) = diffWords("I like apples", "I like apples")
        assertTrue(own.all { it.type == DiffType.SAME })
        assertTrue(fixed.all { it.type == DiffType.SAME })
        assertEquals(3, own.size)
    }

    @Test
    fun substitution_markedRemovedAndAdded() {
        val (own, fixed) = diffWords(
            "I have been working here since three years",
            "I have been working here for three years"
        )
        assertEquals(
            listOf("I", "have", "been", "working", "here", "since", "three", "years"),
            own.map { it.text }
        )
        assertEquals(DiffType.REMOVED, own[5].type)
        assertEquals(DiffType.ADDED, fixed[5].type)
        assertEquals("for", fixed[5].text)
        // Surrounding words stay aligned as SAME.
        assertTrue(own.take(5).all { it.type == DiffType.SAME })
        assertTrue(fixed.take(5).all { it.type == DiffType.SAME })
    }

    @Test
    fun matchingIgnoresCaseAndPunctuation() {
        val (own, fixed) = diffWords("Three Years.", "three years")
        assertTrue(own.all { it.type == DiffType.SAME })
        assertTrue(fixed.all { it.type == DiffType.SAME })
        // Original display text is preserved.
        assertEquals("Years.", own[1].text)
    }

    @Test
    fun insertion_markedAddedOnly() {
        val (own, fixed) = diffWords("It is good", "It is very good")
        assertTrue(own.none { it.type == DiffType.REMOVED })
        assertEquals(DiffType.ADDED, fixed[2].type)
        assertEquals("very", fixed[2].text)
    }

    @Test
    fun deletion_markedRemovedOnly() {
        val (own, fixed) = diffWords("I really really like it", "I like it")
        assertEquals(2, own.count { it.type == DiffType.REMOVED })
        assertTrue(fixed.all { it.type == DiffType.SAME })
    }

    @Test
    fun emptyInputs_produceNoCrash() {
        val (own, fixed) = diffWords("", "Say this")
        assertTrue(own.isEmpty())
        assertTrue(fixed.all { it.type == DiffType.ADDED })
        val (own2, fixed2) = diffWords("", "")
        assertTrue(own2.isEmpty())
        assertTrue(fixed2.isEmpty())
    }
}
