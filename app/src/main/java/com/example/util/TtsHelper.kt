package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Text-to-Speech utility for pronunciation sound modeling (Foote & McDonough 2017).
 * Allows learners to hear authentic acoustic targets for phonetic tips and vocabulary words.
 */
class TtsHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
            }
        }
    }

    fun speak(text: String, isSlow: Boolean = false) {
        if (!isInitialized) return
        tts?.setSpeechRate(if (isSlow) 0.7f else 1.0f)
        tts?.setPitch(1.0f)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speech_tts_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

@Composable
fun rememberTtsHelper(): TtsHelper {
    val context = LocalContext.current
    val helper = remember { TtsHelper(context) }
    DisposableEffect(helper) {
        onDispose {
            helper.shutdown()
        }
    }
    return helper
}
