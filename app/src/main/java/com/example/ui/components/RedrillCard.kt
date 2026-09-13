package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.SentenceCorrection
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceLight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.DiffToken
import com.example.util.DiffType
import com.example.util.diffWords

/**
 * Feature 19 ("Say it again" re-drill loop, Lyster & Saito 2010): the target of
 * one focused re-drill — a single correction the learner re-records.
 */
data class RedrillTarget(
    val index: Int,
    val ownSentence: String,
    val correctedSentence: String,
    val rule: String
)

@Composable
private fun DiffSentenceLine(tokens: List<DiffToken>, isOwnLine: Boolean) {
    Text(
        text = buildAnnotatedString {
            tokens.forEachIndexed { wordIndex, token ->
                if (wordIndex > 0) append(" ")
                when {
                    token.type == DiffType.SAME -> append(token.text)
                    isOwnLine -> withStyle(
                        SpanStyle(
                            color = DangerRed,
                            textDecoration = TextDecoration.LineThrough
                        )
                    ) { append(token.text) }
                    else -> withStyle(
                        SpanStyle(
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold,
                            background = SuccessGreen.copy(alpha = 0.15f)
                        )
                    ) { append(token.text) }
                }
            }
        },
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
        color = TextPrimary
    )
}

/**
 * One "you said → say it" correction card with the rule and a drill button.
 * Supportive, actionable copy per repo convention (AGENTS.md §5).
 */
@Composable
fun SentenceCorrectionCard(
    correction: SentenceCorrection,
    index: Int,
    attemptCount: Int,
    onPractice: () -> Unit
) {
    val (ownTokens, fixedTokens) = remember(correction) {
        diffWords(correction.ownSentence, correction.correctedSentence)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("redrill_card_$index"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fix & re-say #${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (attemptCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Practiced ×$attemptCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = SuccessGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "You said:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            DiffSentenceLine(tokens = ownTokens, isOwnLine = true)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Say it:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            DiffSentenceLine(tokens = fixedTokens, isOwnLine = false)

            if (correction.rule.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Rule: ${correction.rule}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPractice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("redrill_practice_button_$index"),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Say it again", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DeltaChip(label: String, delta: Int, suffix: String = "") {
    val (text, color) = when {
        delta > 0 -> "+$delta$suffix" to SuccessGreen
        delta < 0 -> "$delta$suffix" to DangerRed
        else -> "±0$suffix" to TextSecondary
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

/**
 * Banner shown on a re-drill result: attempt number plus score/accuracy/WPM
 * deltas vs the parent attempt. Chainable — every re-drill can drill again.
 */
@Composable
fun RedrillResultBanner(
    attemptNumber: Int,
    baselineScore: Int,
    currentScore: Int,
    baselineAccuracy: Int,
    currentAccuracy: Int,
    baselineWpm: Int,
    currentWpm: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("redrill_result_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SuccessGreen.copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Re-drill #$attemptNumber complete — vs your last try:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DeltaChip(label = "Score", delta = currentScore - baselineScore)
                DeltaChip(label = "Accuracy", delta = currentAccuracy - baselineAccuracy, suffix = "%")
                DeltaChip(label = "Pace", delta = currentWpm - baselineWpm, suffix = "wpm")
            }
        }
    }
}
