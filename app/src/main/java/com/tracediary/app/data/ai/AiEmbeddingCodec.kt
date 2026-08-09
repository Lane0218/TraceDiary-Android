package com.tracediary.app.data.ai

import java.nio.ByteBuffer
import java.nio.ByteOrder

object AiEmbeddingCodec {
    fun encode(vector: FloatArray): ByteArray =
        ByteBuffer.allocate(vector.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .also { buffer -> vector.forEach(buffer::putFloat) }
            .array()

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % Float.SIZE_BYTES == 0) { "非法向量字节长度" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float }
    }
}
