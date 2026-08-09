package com.tracediary.app.data.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnnxBgeEmbeddingInstrumentedTest {
    @Test
    fun queryEmbedding_matchesPinnedQuantizedUpstreamGoldenVector() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = AiModelManager(context)
        val files = manager.filesOrNull() ?: manager.download().getOrThrow()
        val input = BertWordPieceTokenizer.fromFile(files.vocabulary).encode(
            OnnxBgeEmbeddingEngine.QUERY_INSTRUCTION + QUERY,
        )
        assertArrayEquals(EXPECTED_INPUT_IDS, input.inputIds)

        OnnxBgeEmbeddingEngine(files.model, files.vocabulary).use { engine ->
            val actual = engine.embed(QUERY, isQuery = true)

            assertEquals(512, actual.size)
            GOLDEN_PREFIX.forEachIndexed { index, expected ->
                assertEquals("dimension $index", expected, actual[index], CROSS_RUNTIME_TOLERANCE)
            }
            assertEquals(1f, AiVectorMath.cosine(actual, actual), 0.0001f)

            val relevant = engine.embed("去年我去了上海、杭州和南京旅行。", isQuery = false)
            val unrelated = engine.embed("今天整理了书架，并给绿植浇水。", isQuery = false)
            assertTrue(
                AiVectorMath.cosine(actual, relevant) > AiVectorMath.cosine(actual, unrelated) + 0.2f,
            )
        }
    }

    private companion object {
        const val QUERY = "去年我去了哪些城市？"
        // Dynamic INT8 matmul kernels differ slightly between x86_64 and ARM64.
        const val CROSS_RUNTIME_TOLERANCE = 0.02f
        val EXPECTED_INPUT_IDS = longArrayOf(
            101, 711, 6821, 702, 1368, 2094, 4495, 2768, 6134, 4850,
            809, 4500, 754, 3466, 5164, 4685, 1068, 3152, 4995, 8038,
            1343, 2399, 2769, 1343, 749, 1525, 763, 1814, 2356, 8043, 102,
        )
        val GOLDEN_PREFIX = floatArrayOf(
            0.006822605f,
            -0.000315761f,
            -0.052032076f,
            -0.032797031f,
            -0.032833669f,
            0.035874590f,
            -0.042036720f,
            0.003406166f,
            0.056134854f,
            -0.004369929f,
            0.053246945f,
            -0.174405724f,
            -0.008003953f,
            -0.048692506f,
            -0.008524750f,
            -0.003441752f,
        )
    }
}
