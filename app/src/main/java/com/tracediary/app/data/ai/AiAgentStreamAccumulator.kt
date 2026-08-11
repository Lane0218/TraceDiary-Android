package com.tracediary.app.data.ai

internal class AiAgentStreamAccumulator {
    private val content = StringBuilder()
    private val reasoningContent = StringBuilder()
    private val toolCalls = sortedMapOf<Int, ToolCallBuilder>()
    var finishReason: String? = null
        private set
    var sawDone: Boolean = false
        private set

    val contentText: String get() = content.toString()
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()

    fun accept(event: AiAgentStreamEvent) {
        event.contentDelta?.let(content::append)
        event.reasoningDelta?.let(reasoningContent::append)
        event.toolCallDeltas.forEach { delta ->
            toolCalls.getOrPut(delta.index, ::ToolCallBuilder).apply {
                delta.id?.let(id::append)
                delta.name?.let(name::append)
                delta.argumentsDelta?.let(arguments::append)
            }
        }
        event.finishReason?.let { finishReason = it }
        sawDone = sawDone || event.done
    }

    fun toTurn(): AiAgentTurn = AiAgentTurn(
        message = AiAgentMessage(
            role = "assistant",
            content = contentText.takeIf { it.isNotBlank() },
            reasoningContent = reasoningContent.toString().takeIf { it.isNotBlank() },
            toolCalls = toolCalls.values.map { builder ->
                AiAgentToolCall(
                    id = builder.id.toString(),
                    name = builder.name.toString(),
                    arguments = builder.arguments.toString().ifBlank { "{}" },
                )
            },
        ),
        finishReason = finishReason,
    )

    private class ToolCallBuilder {
        val id = StringBuilder()
        val name = StringBuilder()
        val arguments = StringBuilder()
    }
}
