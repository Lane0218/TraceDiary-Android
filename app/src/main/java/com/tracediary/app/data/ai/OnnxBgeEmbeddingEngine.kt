package com.tracediary.app.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer

class OnnxBgeEmbeddingEngine(
    modelFile: File,
    vocabularyFile: File,
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment(),
) : AiEmbeddingEngine {
    private val tokenizer = BertWordPieceTokenizer.fromFile(vocabularyFile)
    private val sessionOptions = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(2)
        setInterOpNumThreads(1)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val session = environment.createSession(modelFile.absolutePath, sessionOptions)

    override suspend fun embed(text: String, isQuery: Boolean): FloatArray = withContext(Dispatchers.Default) {
        val prepared = if (isQuery) QUERY_INSTRUCTION + text.trim() else text
        val input = tokenizer.encode(prepared)
        val shape = longArrayOf(1L, input.inputIds.size.toLong())
        val tensors = mutableListOf<OnnxTensor>()
        try {
            fun tensor(values: LongArray): OnnxTensor =
                OnnxTensor.createTensor(environment, LongBuffer.wrap(values), shape).also(tensors::add)

            val inputs = buildMap<String, OnnxTensor> {
                put("input_ids", tensor(input.inputIds))
                put("attention_mask", tensor(input.attentionMask))
                if ("token_type_ids" in session.inputNames) {
                    put("token_type_ids", tensor(input.tokenTypeIds))
                }
            }
            session.run(inputs).use { result ->
                @Suppress("UNCHECKED_CAST")
                val hidden = result[0].value as Array<Array<FloatArray>>
                require(hidden.isNotEmpty() && hidden[0].isNotEmpty()) { "Embedding 模型返回空向量" }
                AiVectorMath.normalize(hidden[0][0])
            }
        } finally {
            tensors.forEach(OnnxTensor::close)
        }
    }

    override fun close() {
        session.close()
        sessionOptions.close()
    }

    companion object {
        const val QUERY_INSTRUCTION = "为这个句子生成表示以用于检索相关文章："
    }
}
