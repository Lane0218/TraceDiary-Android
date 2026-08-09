package com.tracediary.app.data.ai

data class AiTextChunk(
    val index: Int,
    val startOffset: Int,
    val endOffsetExclusive: Int,
    val text: String,
)

object AiTextChunker {
    const val DEFAULT_MAX_CODE_POINTS = 320
    const val DEFAULT_OVERLAP_CODE_POINTS = 64

    fun chunk(
        text: String,
        maxCodePoints: Int = DEFAULT_MAX_CODE_POINTS,
        overlapCodePoints: Int = DEFAULT_OVERLAP_CODE_POINTS,
    ): List<AiTextChunk> {
        require(maxCodePoints > 0)
        require(overlapCodePoints in 0 until maxCodePoints)
        if (text.isBlank()) return emptyList()

        val offsets = mutableListOf<Int>()
        var offset = 0
        while (offset < text.length) {
            offsets += offset
            offset += Character.charCount(text.codePointAt(offset))
        }
        offsets += text.length

        val chunks = mutableListOf<AiTextChunk>()
        var startCodePoint = 0
        while (startCodePoint < offsets.lastIndex) {
            val endCodePoint = minOf(startCodePoint + maxCodePoints, offsets.lastIndex)
            val start = offsets[startCodePoint]
            val end = offsets[endCodePoint]
            chunks += AiTextChunk(
                index = chunks.size,
                startOffset = start,
                endOffsetExclusive = end,
                text = text.substring(start, end),
            )
            if (endCodePoint == offsets.lastIndex) break
            startCodePoint = endCodePoint - overlapCodePoints
        }
        return chunks
    }
}
