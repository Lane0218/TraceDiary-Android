package com.tracediary.app.data.ai

interface AiEmbeddingEngine : AutoCloseable {
    suspend fun embed(text: String, isQuery: Boolean): FloatArray
    override fun close()
}
