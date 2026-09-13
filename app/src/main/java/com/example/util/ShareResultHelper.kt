package com.example.util

import android.content.Context
import android.content.Intent
import com.example.SpeechMetrics

/**
 * Feature 18: Share Progress & Social Accountability (Dörnyei 2001).
 * Generates formatted sharing content and launches Android's native share sheet.
 */
object ShareProgressHelper {

    fun generateShareText(metrics: SpeechMetrics, streakDays: Int): String {
        val streakStr = if (streakDays > 0) "🔥 $streakDays-Day Practice Streak\n" else ""
        return buildString {
            append("🎙️ Open Speech — English Fluency Report\n\n")
            append("⭐ Overall Score: ${metrics.score}/100 (${metrics.cefr})\n")
            append("⚡ Speech Rate: ${metrics.wpm} WPM | Accuracy: ${metrics.accuracy}%\n")
            append("🗣️ Fillers: ${metrics.fillers} | Pauses: ${metrics.pauses}\n")
            append("🎯 Pronunciation: ${metrics.pronunciationScore}% | Accent Clarity: ${metrics.accentClarityScore}%\n")
            append(streakStr)
            append("\nPractice speaking English with AI feedback:\nhttps://sunnydev07.github.io/Open-Speech/")
        }
    }

    fun launchShareSheet(context: Context, metrics: SpeechMetrics, streakDays: Int) {
        val shareText = generateShareText(metrics, streakDays)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Speaking Progress")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }
}
