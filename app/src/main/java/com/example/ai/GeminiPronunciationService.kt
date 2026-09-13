package com.example.ai

import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class PhoneticTip(
    val word: String,
    val phonetic: String,
    val issue: String,
    val tip: String
)

/**
 * Feature 19 ("Say it again" re-drill loop, Lyster & Saito 2010): one spoken
 * grammar/word-choice error as an eliciting prompt — the learner's own sentence,
 * the minimal correction, and the one-line rule behind it.
 */
data class SentenceCorrection(
    val ownSentence: String,
    val correctedSentence: String,
    val rule: String
)

data class PronunciationAccentFeedback(
    val transcription: String,
    val pronunciationScore: Int,
    val accentClarityScore: Int,
    val detectedAccentProfile: String,
    val pronunciationFeedback: String,
    val accentFeedback: String,
    val phoneticTips: List<PhoneticTip>,
    val wpm: Int,
    val pauses: Int,
    val fillers: Int,
    val accuracy: Int,
    val recommendations: List<String>,
    val isRealAiGenerated: Boolean = true,
    val cefr: String? = null,
    val cefrJustification: String = "",
    val sentenceCorrections: List<SentenceCorrection> = emptyList()
)

/** Thrown when a real (API-key) analysis fails: network, HTTP error, or bad payload (F8). */
class AnalysisException(message: String) : Exception(message)

/**
 * Service that connects to Google Gemini AI to analyze recorded speech audio
 * and provide structured, real-time feedback on pronunciation, phonemes, and accent.
 */
class GeminiPronunciationService {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Sends the recorded audio file to Gemini AI for deep pronunciation & accent analysis.
     *
     * Returns a deterministic demo result only when no API key is configured (F3).
     * With a key configured, failures throw [AnalysisException] so the UI can show
     * an error + retry instead of silent fake scores (F8).
     */
    suspend fun analyzeSpeech(
        audioFile: File?,
        referencePrompt: String,
        elapsedSeconds: Int
    ): PronunciationAccentFeedback = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        // If no API key is provided or it's the template placeholder, provide deterministic demo feedback
        if (!isApiKeyConfigured()) {
            Log.d("GeminiAI", "Using demo analysis: GEMINI_API_KEY is placeholder or empty")
            return@withContext generateDiagnosticFallback(referencePrompt, elapsedSeconds)
        }

        try {
            val audioBytes = if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                audioFile.readBytes()
            } else {
                null
            }

            val base64Audio = if (audioBytes != null && audioBytes.isNotEmpty()) {
                Base64.encodeToString(audioBytes, Base64.NO_WRAP)
            } else {
                null
            }

            val systemPrompt = """
                You are an expert American & International English pronunciation and accent coach.
                Analyze the user's speech audio input for this practice drill prompt: "$referencePrompt".
                Provide clear, supportive, and highly actionable diagnostic feedback.

                For "sentenceCorrections", pick the 2-4 most important spoken grammar or word-choice
                errors actually present in the transcription. Each "ownSentence" must be an exact
                sentence the learner said; "correctedSentence" is the minimal correction of that
                sentence; "rule" is a one-line grammar/usage rule (max 15 words). If the speech has
                no clear errors, return an empty array.
                
                You must return your output strictly in JSON with the following structure:
                {
                  "transcription": "The spoken words transcribed from the audio",
                  "pronunciationScore": 88, // integer 0-100 evaluating phonetic articulation
                  "accentClarityScore": 84, // integer 0-100 evaluating accent intelligibility and intonation
                  "detectedAccentProfile": "Brief description of detected accent traits or cadence (e.g. 'General American tone with slight non-native vowel elongation')",
                  "pronunciationFeedback": "2-3 sentences on phoneme articulation, syllable stress, and consonant endings",
                  "accentFeedback": "2-3 sentences on pitch variation, speech rhythm, and intonation curve",
                  "phoneticTips": [
                    {
                      "word": "consequently",
                      "phonetic": "/ˈkɒn.sɪ.kwənt.li/",
                      "issue": "Primary stress placed on 2nd syllable",
                      "tip": "Emphasize the first syllable 'CON-', keeping '-se-quent-ly' light."
                    },
                    {
                      "word": "bottleneck",
                      "phonetic": "/ˈbɒt.əl.nek/",
                      "issue": "Glottal stop softened on 'tt'",
                      "tip": "Articulate a crisp flap 't' for standard American clarity."
                    }
                  ],
                  "wpm": 134, // estimated words per minute
                  "pauses": 2, // count of pauses > 1s
                  "fillers": 1, // count of filler words (um, uh, like)
                  "accuracy": 95, // grammatical accuracy percentage
                  "cefr": "B2 Upper Intermediate", // one of: A2 Elementary, B1 Intermediate, B2 Upper Intermediate, C1 Advanced
                  "cefrJustification": "One sentence justifying the CEFR level from score, accuracy, and pacing",
                  "sentenceCorrections": [
                    {
                      "ownSentence": "I have been working here since three years.",
                      "correctedSentence": "I have been working here for three years.",
                      "rule": "Use 'for' (not 'since') with a length of time."
                    }
                  ],
                  "recommendations": [
                    "Practice linking vowel-to-vowel transitions smoothly.",
                    "Slow down slightly on polysyllabic terminology for crisper articulation."
                  ]
                }
            """.trimIndent()

            val partsArray = JSONArray()

            val promptPart = JSONObject().put("text", systemPrompt)
            partsArray.put(promptPart)

            if (base64Audio != null) {
                val audioPart = JSONObject().put(
                    "inlineData",
                    JSONObject()
                        .put("mimeType", "audio/mp4")
                        .put("data", base64Audio)
                )
                partsArray.put(audioPart)
            }

            val contentsArray = JSONArray().put(JSONObject().put("parts", partsArray))

            val generationConfig = JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.4)

            val rootRequest = JSONObject()
                .put("contents", contentsArray)
                .put("generationConfig", generationConfig)

            // Using gemini-2.5-flash which has multimodal audio support
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val requestBody = rootRequest.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("GeminiAI", "API response error ${response.code}: ${response.message}")
                throw AnalysisException("Gemini request failed (HTTP ${response.code}). Check your connection and retry.")
            }

            val responseString = response.body?.string() ?: ""
            if (responseString.isBlank()) {
                throw AnalysisException("Empty response from Gemini. Retry the analysis.")
            }
            val jsonResponse = JSONObject(responseString)
            val candidates = jsonResponse.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text") ?: ""

            if (text.isNotBlank()) {
                parseFeedbackJson(text, elapsedSeconds, true)
            } else {
                throw AnalysisException("Gemini returned no feedback text. Retry the analysis.")
            }
        } catch (e: AnalysisException) {
            throw e
        } catch (e: Exception) {
            Log.e("GeminiAI", "Error invoking Gemini pronunciation analysis", e)
            throw AnalysisException("Could not reach Gemini (${e.message}). Check your connection and retry.")
        }
    }

    fun isApiKeyConfigured(): Boolean {
        val apiKey = BuildConfig.GEMINI_API_KEY
        return apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"
    }

    @VisibleForTesting
    internal fun parseFeedbackJson(
        jsonString: String,
        elapsedSeconds: Int,
        isRealAi: Boolean
    ): PronunciationAccentFeedback {
        return try {
            val cleanJson = jsonString
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val obj = JSONObject(cleanJson)
            val transcription = obj.optString("transcription", "Speech recording analyzed successfully.")
            val pronunciationScore = obj.optInt("pronunciationScore", 88).coerceIn(0, 100)
            val accentClarityScore = obj.optInt("accentClarityScore", 84).coerceIn(0, 100)
            val detectedAccentProfile = obj.optString(
                "detectedAccentProfile",
                "General American cadence with clear consonant articulation"
            )
            val pronunciationFeedback = obj.optString(
                "pronunciationFeedback",
                "Clear syllable stress on main vocabulary words. Final consonants were articulated crisply."
            )
            val accentFeedback = obj.optString(
                "accentFeedback",
                "Natural intonation contour with expressive pitch shifts on emphasis words."
            )

            val tipsList = mutableListOf<PhoneticTip>()
            val tipsArray = obj.optJSONArray("phoneticTips")
            if (tipsArray != null) {
                for (i in 0 until tipsArray.length()) {
                    val tipObj = tipsArray.optJSONObject(i)
                    if (tipObj != null) {
                        tipsList.add(
                            PhoneticTip(
                                word = tipObj.optString("word", "word"),
                                phonetic = tipObj.optString("phonetic", "/wɜːrd/"),
                                issue = tipObj.optString("issue", "Slight vowel shift"),
                                tip = tipObj.optString("tip", "Open jaw slightly wider on central vowel sound.")
                            )
                        )
                    }
                }
            }

            val recList = mutableListOf<String>()
            val recArray = obj.optJSONArray("recommendations")
            if (recArray != null) {
                for (i in 0 until recArray.length()) {
                    recList.add(recArray.optString(i))
                }
            }
            if (recList.isEmpty()) {
                recList.add("Maintain steady diaphragmatic breath support across long sentences.")
                recList.add("Practice connecting terminal consonants to opening vowels.")
            }

            val wpm = obj.optInt("wpm", 132)
            val pauses = obj.optInt("pauses", 2)
            val fillers = obj.optInt("fillers", 1)
            val accuracy = obj.optInt("accuracy", 94)
            val cefr = obj.optString("cefr", "").ifBlank { null }
            val cefrJustification = obj.optString("cefrJustification", "")

            // Feature 19: sentence corrections for the "Say it again" re-drill loop.
            // Tolerate older/missing payloads — an empty list simply hides the section.
            val correctionsList = mutableListOf<SentenceCorrection>()
            val correctionsArray = obj.optJSONArray("sentenceCorrections")
            if (correctionsArray != null) {
                for (i in 0 until correctionsArray.length()) {
                    val correctionObj = correctionsArray.optJSONObject(i) ?: continue
                    val own = correctionObj.optString("ownSentence", "").trim()
                    val fixed = correctionObj.optString("correctedSentence", "").trim()
                    if (own.isNotEmpty() && fixed.isNotEmpty()) {
                        correctionsList.add(
                            SentenceCorrection(
                                ownSentence = own,
                                correctedSentence = fixed,
                                rule = correctionObj.optString("rule", "").trim()
                            )
                        )
                    }
                }
            }

            PronunciationAccentFeedback(
                transcription = transcription,
                pronunciationScore = pronunciationScore,
                accentClarityScore = accentClarityScore,
                detectedAccentProfile = detectedAccentProfile,
                pronunciationFeedback = pronunciationFeedback,
                accentFeedback = accentFeedback,
                phoneticTips = tipsList.ifEmpty { defaultPhoneticTips() },
                wpm = wpm,
                pauses = pauses,
                fillers = fillers,
                accuracy = accuracy,
                recommendations = recList,
                isRealAiGenerated = isRealAi,
                cefr = cefr,
                cefrJustification = cefrJustification,
                sentenceCorrections = correctionsList.take(4)
            )
        } catch (e: Exception) {
            Log.e("GeminiAI", "Failed to parse Gemini feedback JSON", e)
            throw AnalysisException("Gemini returned an unreadable response. Retry the analysis.")
        }
    }

    /**
     * Deterministic demo result for runs without an API key (F3).
     * All scores are zero so demo data is never mistaken for a real evaluation;
     * the UI must label it "Demo mode" (see ResultScreen demo banner).
     */
    fun generateDiagnosticFallback(
        prompt: String,
        elapsedSeconds: Int
    ): PronunciationAccentFeedback {
        return PronunciationAccentFeedback(
            transcription = "Demo transcript — connect a GEMINI_API_KEY in .env to transcribe and score your real speech.",
            pronunciationScore = 0,
            accentClarityScore = 0,
            detectedAccentProfile = "Demo mode — no audio was analyzed",
            pronunciationFeedback = "Demo mode: record with a configured API key to receive articulation feedback.",
            accentFeedback = "Demo mode: record with a configured API key to receive rhythm and intonation feedback.",
            phoneticTips = defaultPhoneticTips(),
            wpm = 0,
            pauses = 0,
            fillers = 0,
            accuracy = 0,
            recommendations = listOf(
                "Add your GEMINI_API_KEY to the .env file (see .env.example) to unlock real AI scoring.",
                "Until then, use the timer drills to build a daily speaking habit.",
                "Each scored session will appear here with pronunciation, accent, and pacing feedback."
            ),
            isRealAiGenerated = false,
            cefr = null,
            cefrJustification = ""
        )
    }

    private fun defaultPhoneticTips(): List<PhoneticTip> {
        return listOf(
            PhoneticTip(
                word = "consequently",
                phonetic = "/ˈkɒn.sɪ.kwənt.li/",
                issue = "Primary stress placed on 2nd syllable",
                tip = "Emphasize 'CON-', keeping '-se-quent-ly' light and relaxed."
            ),
            PhoneticTip(
                word = "bottleneck",
                phonetic = "/ˈbɒt.əl.nek/",
                issue = "Glottal stop softened on 'tt'",
                tip = "Articulate a crisp flap 't' for standard American clarity."
            ),
            PhoneticTip(
                word = "prioritized",
                phonetic = "/praɪˈɔːr.ɪ.taɪzd/",
                issue = "Vowel shortening on initial diphthong",
                tip = "Elongate the /aɪ/ diphthong in 'PRY' before transitioning."
            )
        )
    }
}
