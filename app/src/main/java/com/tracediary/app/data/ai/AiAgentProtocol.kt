package com.tracediary.app.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

data class AiAgentToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class AiAgentMessage(
    val role: String,
    val content: String? = null,
    val reasoningContent: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<AiAgentToolCall> = emptyList(),
)

data class AiAgentTurn(
    val message: AiAgentMessage,
    val finishReason: String?,
)

data class AiAgentRequest(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val messages: List<AiAgentMessage>,
    val tools: JsonArray,
    val diagnostics: AiAgentRequestDiagnostics? = null,
    val thinking: AiAgentThinking = AiAgentThinking.Default,
)

enum class AiAgentThinking { Default, Low, Disabled }

data class AiAgentRequestDiagnostics(
    val traceId: String,
    val stage: String,
)

data class AiAgentToolCallDelta(
    val index: Int,
    val id: String? = null,
    val name: String? = null,
    val argumentsDelta: String? = null,
)

data class AiAgentStreamEvent(
    val contentDelta: String? = null,
    val reasoningDelta: String? = null,
    val toolCallDeltas: List<AiAgentToolCallDelta> = emptyList(),
    val finishReason: String? = null,
    val done: Boolean = false,
) {
    val reasoningDeltaChars: Int get() = reasoningDelta?.length ?: 0
}

sealed interface AiAgentConnectionResult {
    data object Success : AiAgentConnectionResult
    data class Failed(val message: String) : AiAgentConnectionResult
}

internal fun JsonElement.compactString(): String = toString()
