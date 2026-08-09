package com.tracediary.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiVectorMathTest {
    @Test
    fun normalizeAndCosine_areDeterministic() {
        val normalized = AiVectorMath.normalize(floatArrayOf(3f, 4f))

        assertEquals(0.6f, normalized[0], 0.0001f)
        assertEquals(0.8f, normalized[1], 0.0001f)
        assertEquals(1f, AiVectorMath.cosine(normalized, normalized), 0.0001f)
        assertEquals(0f, AiVectorMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.0001f)
    }
}
