package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.model.context.Message
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TokenEstimatorImpl @Inject constructor() : TokenEstimator {

    companion object {
        private const val CHARS_PER_TOKEN_ASCII = 4.0
        private const val CHARS_PER_TOKEN_CJK = 1.5
        private val CJK_RANGES = listOf(
            0x4E00..0x9FFF,   // CJK Unified Ideographs
            0x3400..0x4DBF,   // CJK Unified Ideographs Extension A
            0x3000..0x303F,   // CJK Symbols and Punctuation
            0x3040..0x309F,   // Hiragana
            0x30A0..0x30FF,   // Katakana
            0xAC00..0xD7AF,   // Hangul Syllables
            0xFF00..0xFFEF,   // Halfwidth and Fullwidth Forms
        )
    }

    override fun estimate(text: String): Int {
        if (text.isBlank()) return 0

        var asciiChars = 0
        var cjkChars = 0

        for (char in text) {
            if (isCJK(char)) {
                cjkChars++
            } else if (!char.isWhitespace()) {
                asciiChars++
            }
        }

        if (asciiChars == 0 && cjkChars == 0) return 0

        val asciiTokens = if (asciiChars > 0) asciiChars / CHARS_PER_TOKEN_ASCII else 0.0
        val cjkTokens = if (cjkChars > 0) cjkChars / CHARS_PER_TOKEN_CJK else 0.0

        return (asciiTokens + cjkTokens).toInt().coerceAtLeast(1)
    }

    override fun estimateMessages(messages: List<Message>): Int {
        return messages.sumOf { estimate(it.content) }
    }

    private fun isCJK(char: Char): Boolean {
        val code = char.code
        return CJK_RANGES.any { code in it }
    }
}
