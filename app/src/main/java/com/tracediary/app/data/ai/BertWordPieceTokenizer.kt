package com.tracediary.app.data.ai

import java.io.File

data class BertInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray,
)

class BertWordPieceTokenizer private constructor(
    private val vocabulary: Map<String, Int>,
) {
    private val unknownId = vocabulary.getValue("[UNK]")
    private val clsId = vocabulary.getValue("[CLS]")
    private val sepId = vocabulary.getValue("[SEP]")

    fun encode(text: String, maxTokens: Int = MAX_TOKENS): BertInput {
        require(maxTokens >= 2)
        val contentIds = mutableListOf<Int>()
        basicTokens(text).forEach { token ->
            if (contentIds.size >= maxTokens - 2) return@forEach
            val remaining = maxTokens - 2 - contentIds.size
            contentIds += wordPieces(token).take(remaining)
        }
        val ids = LongArray(contentIds.size + 2)
        ids[0] = clsId.toLong()
        contentIds.forEachIndexed { index, id -> ids[index + 1] = id.toLong() }
        ids[ids.lastIndex] = sepId.toLong()
        return BertInput(
            inputIds = ids,
            attentionMask = LongArray(ids.size) { 1L },
            tokenTypeIds = LongArray(ids.size),
        )
    }

    private fun basicTokens(text: String): List<String> {
        val output = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                output += current.toString()
                current.clear()
            }
        }
        var offset = 0
        while (offset < text.length) {
            val codePoint = text.codePointAt(offset)
            when {
                Character.isWhitespace(codePoint) || Character.getType(codePoint) == Character.FORMAT.toInt() -> flush()
                isChinese(codePoint) || isPunctuation(codePoint) -> {
                    flush()
                    output += String(Character.toChars(codePoint))
                }
                Character.getType(codePoint) == Character.CONTROL.toInt() -> Unit
                else -> current.appendCodePoint(codePoint)
            }
            offset += Character.charCount(codePoint)
        }
        flush()
        return output
    }

    private fun wordPieces(token: String): List<Int> {
        vocabulary[token]?.let { return listOf(it) }
        if (token.codePointCount(0, token.length) > MAX_INPUT_CHARS_PER_WORD) return listOf(unknownId)
        val pieces = mutableListOf<Int>()
        var start = 0
        while (start < token.length) {
            var end = token.length
            var matchedId: Int? = null
            var matchedEnd = start
            while (end > start) {
                val candidate = (if (start == 0) "" else "##") + token.substring(start, end)
                val id = vocabulary[candidate]
                if (id != null) {
                    matchedId = id
                    matchedEnd = end
                    break
                }
                end = token.offsetByCodePoints(end, -1)
            }
            if (matchedId == null) return listOf(unknownId)
            pieces += matchedId
            start = matchedEnd
        }
        return pieces
    }

    private fun isChinese(codePoint: Int): Boolean =
        codePoint in 0x4E00..0x9FFF ||
            codePoint in 0x3400..0x4DBF ||
            codePoint in 0x20000..0x2A6DF ||
            codePoint in 0x2A700..0x2B73F ||
            codePoint in 0x2B740..0x2B81F ||
            codePoint in 0x2B820..0x2CEAF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x2F800..0x2FA1F

    private fun isPunctuation(codePoint: Int): Boolean {
        if (codePoint in 33..47 || codePoint in 58..64 || codePoint in 91..96 || codePoint in 123..126) return true
        return when (Character.getType(codePoint)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt() -> true
            else -> false
        }
    }

    companion object {
        const val MAX_TOKENS = 512
        private const val MAX_INPUT_CHARS_PER_WORD = 100

        fun fromFile(file: File): BertWordPieceTokenizer {
            val vocabulary = file.useLines { lines ->
                lines.mapIndexed { index, token -> token to index }.toMap()
            }
            require(listOf("[UNK]", "[CLS]", "[SEP]").all(vocabulary::containsKey)) {
                "词表缺少 BERT 特殊 token"
            }
            return BertWordPieceTokenizer(vocabulary)
        }

        internal fun fromVocabulary(vocabulary: Map<String, Int>): BertWordPieceTokenizer =
            BertWordPieceTokenizer(vocabulary)
    }
}
