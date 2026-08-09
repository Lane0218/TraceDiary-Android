package com.tracediary.app.data.ai

import com.tracediary.app.domain.model.AiAnswer
import com.tracediary.app.domain.model.AiEvidence
import com.tracediary.app.domain.model.AiSourceRef
import com.tracediary.app.domain.model.AiSourceType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull

object AiEvidenceValidator {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun parseAndValidate(
        raw: String,
        sourceReader: suspend (AiSourceRef) -> String?,
    ): AiAnswer {
        val root = runCatching { json.parseToJsonElement(extractJson(raw)).jsonObject }
            .getOrElse { throw AiAgentException("AI 未按约定返回可验证答案") }
        val answer = root["answer"]?.jsonPrimitive?.content?.trim().orEmpty()
        require(answer.isNotBlank()) { "AI 返回了空答案" }
        val insufficientEvidence = root["insufficientEvidence"]?.jsonPrimitive?.booleanOrNull ?: false
        val evidence = root["evidence"]?.jsonArray.orEmpty().map { element ->
            val item = element.jsonObject
            val source = AiSourceRef(
                type = when (item["sourceType"]?.jsonPrimitive?.content) {
                    "diary" -> AiSourceType.Diary
                    "annual_summary" -> AiSourceType.AnnualSummary
                    "statistics" -> AiSourceType.Statistics
                    "preferences" -> AiSourceType.Preferences
                    else -> throw AiAgentException("AI 返回了未知证据类型")
                },
                key = item["sourceKey"]?.jsonPrimitive?.content?.trim().orEmpty(),
            )
            val quote = item["quote"]?.jsonPrimitive?.content?.trim().orEmpty()
            require(source.key.isNotBlank() && quote.isNotBlank()) { "AI 返回了不完整证据" }
            require(quote.length <= MAX_EVIDENCE_QUOTE_CHARS) { "AI 返回的证据片段过长" }
            val currentContent = sourceReader(source) ?: throw AiAgentException("答案引用的记录已不存在")
            require(currentContent.contains(quote)) { "答案证据与当前原文不一致" }
            AiEvidence(source, quote)
        }
        if (evidence.isEmpty()) {
            require(insufficientEvidence) { "没有可验证证据，无法给出确定答案" }
            require(answer.contains("不确定") || answer.contains("无法确认") || answer.contains("记录不足")) {
                "证据不足时必须明确表达不确定"
            }
        }
        return AiAnswer(answer, evidence.distinct(), insufficientEvidence)
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private const val MAX_EVIDENCE_QUOTE_CHARS = 2_000
}

private fun kotlinx.serialization.json.JsonArray?.orEmpty(): kotlinx.serialization.json.JsonArray =
    this ?: kotlinx.serialization.json.JsonArray(emptyList())
