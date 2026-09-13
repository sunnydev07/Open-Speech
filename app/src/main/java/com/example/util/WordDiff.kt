package com.example.util

/** Word-level diff token type for Feature 19 ("Say it again" re-drill loop). */
enum class DiffType { SAME, REMOVED, ADDED }

/** One word of a diffed sentence; [text] keeps the original casing/punctuation. */
data class DiffToken(val text: String, val type: DiffType)

/**
 * Word-level diff of the learner's sentence vs the corrected sentence.
 *
 * Matching is case- and punctuation-insensitive ("Years." matches "years") while
 * displayed tokens preserve the original text. Implemented as LCS over normalized
 * words — O(n*m), trivial cost for single sentences.
 *
 * @return Pair(first = tokens for the "you said" line as SAME/REMOVED,
 *              second = tokens for the "say it" line as SAME/ADDED)
 */
fun diffWords(own: String, corrected: String): Pair<List<DiffToken>, List<DiffToken>> {
    val ownWords = own.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val fixedWords = corrected.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (ownWords.isEmpty() || fixedWords.isEmpty()) {
        return Pair(
            ownWords.map { DiffToken(it, DiffType.REMOVED) },
            fixedWords.map { DiffToken(it, DiffType.ADDED) }
        )
    }

    val normOwn = ownWords.map { normalizeWord(it) }
    val normFixed = fixedWords.map { normalizeWord(it) }
    val n = normOwn.size
    val m = normFixed.size

    // LCS length table over normalized words.
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            dp[i][j] = if (normOwn[i] == normFixed[j]) {
                dp[i + 1][j + 1] + 1
            } else {
                maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
    }

    val ownTokens = mutableListOf<DiffToken>()
    val fixedTokens = mutableListOf<DiffToken>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
        if (normOwn[i] == normFixed[j]) {
            ownTokens.add(DiffToken(ownWords[i], DiffType.SAME))
            fixedTokens.add(DiffToken(fixedWords[j], DiffType.SAME))
            i++
            j++
        } else if (dp[i + 1][j] >= dp[i][j + 1]) {
            ownTokens.add(DiffToken(ownWords[i], DiffType.REMOVED))
            i++
        } else {
            fixedTokens.add(DiffToken(fixedWords[j], DiffType.ADDED))
            j++
        }
    }
    while (i < n) {
        ownTokens.add(DiffToken(ownWords[i], DiffType.REMOVED))
        i++
    }
    while (j < m) {
        fixedTokens.add(DiffToken(fixedWords[j], DiffType.ADDED))
        j++
    }
    return Pair(ownTokens, fixedTokens)
}

private fun normalizeWord(word: String): String =
    word.lowercase().trim { it in ".,!?;:\"'()[]{}" }
