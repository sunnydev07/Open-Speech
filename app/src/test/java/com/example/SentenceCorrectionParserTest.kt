package com.example

import com.example.ai.GeminiPronunciationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Feature 19: the Gemini payload carries sentence corrections for the
 * "Say it again" re-drill loop. Parser must tolerate missing/invalid entries
 * (older payloads simply hide the section).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SentenceCorrectionParserTest {

    private val service = GeminiPronunciationService()

    private fun payload(correctionsJson: String): String = """
        {
          "transcription": "I have been working here since three years.",
          "pronunciationScore": 82,
          "accentClarityScore": 80,
          "detectedAccentProfile": "Test profile",
          "pronunciationFeedback": "Test pronunciation feedback.",
          "accentFeedback": "Test accent feedback.",
          "phoneticTips": [],
          "wpm": 120,
          "pauses": 2,
          "fillers": 1,
          "accuracy": 88,
          "cefr": "B1 Intermediate",
          "cefrJustification": "Test justification.",
          "sentenceCorrections": $correctionsJson,
          "recommendations": ["Keep practicing."]
        }
    """.trimIndent()

    @Test
    fun parsesCorrections() {
        val json = payload(
            """[
              {"ownSentence": "I have been working here since three years.",
               "correctedSentence": "I have been working here for three years.",
               "rule": "Use 'for' with a length of time."},
              {"ownSentence": "He go to school.",
               "correctedSentence": "He goes to school.",
               "rule": "Third-person singular takes -s."}
            ]"""
        )
        val result = service.parseFeedbackJson(json, 30, true)
        assertEquals(2, result.sentenceCorrections.size)
        assertEquals("I have been working here since three years.", result.sentenceCorrections[0].ownSentence)
        assertEquals("I have been working here for three years.", result.sentenceCorrections[0].correctedSentence)
        assertEquals("Use 'for' with a length of time.", result.sentenceCorrections[0].rule)
    }

    @Test
    fun skipsBlankEntries_missingKeyGivesEmptyList() {
        val json = payload(
            """[
              {"ownSentence": "", "correctedSentence": "He goes.", "rule": "x"},
              {"ownSentence": "He go.", "correctedSentence": "", "rule": "y"},
              {"ownSentence": "He go.", "correctedSentence": "He goes.", "rule": ""}
            ]"""
        )
        val result = service.parseFeedbackJson(json, 30, true)
        assertEquals(1, result.sentenceCorrections.size)
        assertEquals("", result.sentenceCorrections[0].rule)

        val noKey = payload("[]").replace("\"sentenceCorrections\": [],", "")
        assertTrue(service.parseFeedbackJson(noKey, 30, true).sentenceCorrections.isEmpty())
    }

    @Test
    fun capsCorrectionsAtFour() {
        val many = (1..6).joinToString(",") {
            """{"ownSentence": "Bad $it.", "correctedSentence": "Good $it.", "rule": "Rule $it."}"""
        }
        val result = service.parseFeedbackJson(payload("[$many]"), 30, true)
        assertEquals(4, result.sentenceCorrections.size)
    }
}
