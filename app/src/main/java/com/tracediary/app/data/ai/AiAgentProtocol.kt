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
)

sealed interface AiAgentConnectionResult {
    data object Success : AiAgentConnectionResult
    data class Failed(val message: String) : AiAgentConnectionResult
}

internal fun JsonElement.compactString(): String = toString()
