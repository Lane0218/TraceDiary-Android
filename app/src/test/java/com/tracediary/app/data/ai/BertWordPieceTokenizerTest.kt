package com.tracediary.app.data.ai

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BertWordPieceTokenizerTest {
    private val tokenizer = BertWordPieceTokenizer.fromVocabulary(
        linkedMapOf(
            "[PAD]" to 0,
            "[UNK]" to 1,
            "[CLS]" to 2,
            "[SEP]" to 3,
            "洗" to 4,
            "牙" to 5,
            "hello" to 6,
            "##s" to 7,
            "。" to 8,
        ),
    )

    @Test
    fun encode_splitsChinesePunctuationAndWordPieces() {
        val result = tokenizer.encode("洗牙 hellos。")

        assertArrayEquals(longArrayOf(2, 4, 5, 6, 7, 8, 3), result.inputIds)
        assertArrayEquals(LongArray(7) { 1L }, result.attentionMask)
        assertArrayEquals(LongArray(7), result.tokenTypeIds)
    }
}
