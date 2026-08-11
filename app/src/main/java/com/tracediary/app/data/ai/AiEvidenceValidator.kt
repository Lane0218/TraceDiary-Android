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
import java.text.Normalizer

object AiEvidenceValidator {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun validate(
        answerText: String,
        insufficientEvidence: Boolean,
        evidence: List<AiEvidence>,
        sourceReader: suspend (AiSourceRef) -> String?,
    ): AiAnswer {
        val answer = answerText.trim()
        require(answer.isNotBlank()) { "AI 返回了空答案" }
        val validatedEvidence = evidence.mapNotNull { item ->
            if (item.source.key.isBlank() || item.quote.isBlank()) return@mapNotNull null
            if (item.quote.length > MAX_EVIDENCE_QUOTE_CHARS) return@mapNotNull null
            val currentContent = sourceReader(item.source) ?: return@mapNotNull null
            val normalizedQuote = item.quote.normalizeForEvidence()
            if (normalizedQuote.isBlank()) return@mapNotNull null
            item.takeIf { currentContent.normalizeForEvidence().contains(normalizedQuote) }
        }
        if (validatedEvidence.isEmpty()) {
            require(insufficientEvidence) { "没有可验证证据，无法给出确定答案" }
            require(answer.contains("不确定") || answer.contains("无法确认") || answer.contains("记录不足")) {
                "证据不足时必须明确表达不确定"
            }
        }
        return AiAnswer(answer, validatedEvidence.distinct(), insufficientEvidence)
    }

    suspend fun parseAndValidate(
        raw: String,
        sourceReader: suspend (AiSourceRef) -> String?,
    ): AiAnswer {
        val root = runCatching { json.parseToJsonElement(extractJson(raw)).jsonObject }
            .getOrElse { throw AiAgentException("AI 未按约定返回可验证答案") }
        val answer = root["answer"]?.jsonPrimitive?.content?.trim().orEmpty()
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
            AiEvidence(source, quote)
        }
        return validate(answer, insufficientEvidence, evidence, sourceReader)
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private const val MAX_EVIDENCE_QUOTE_CHARS = 2_000
}

private fun String.normalizeForEvidence(): String = Normalizer
    .normalize(this, Normalizer.Form.NFKC)
    .filterNot(Char::isWhitespace)

private fun kotlinx.serialization.json.JsonArray?.orEmpty(): kotlinx.serialization.json.JsonArray =
    this ?: kotlinx.serialization.json.JsonArray(emptyList())
