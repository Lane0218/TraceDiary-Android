package com.tracediary.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTextChunkerTest {
    @Test
    fun chunk_preservesOffsetsAndOverlapWithoutSplittingEmoji() {
        val text = "甲乙😀丙丁戊己"

        val chunks = AiTextChunker.chunk(text, maxCodePoints = 4, overlapCodePoints = 1)

        assertEquals(listOf("甲乙😀丙", "丙丁戊己"), chunks.map { it.text })
        chunks.forEach { chunk ->
            assertEquals(chunk.text, text.substring(chunk.startOffset, chunk.endOffsetExclusive))
        }
    }

    @Test
    fun chunk_blankTextProducesNoDerivedData() {
        assertTrue(AiTextChunker.chunk(" \n\t").isEmpty())
    }
}
