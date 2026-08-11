package com.tracediary.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAgentStreamAccumulatorTest {
    @Test
    fun assemblesFragmentedToolCall() {
        val accumulator = AiAgentStreamAccumulator()

        accumulator.accept(
            AiAgentStreamEvent(
                reasoningDelta = "需要检索。",
                toolCallDeltas = listOf(
                    AiAgentToolCallDelta(
                        index = 0,
                        id = "call_1",
                        name = "semantic_",
                        argumentsDelta = "{\"query\":\"洗",
                    ),
                ),
            ),
        )
        accumulator.accept(
            AiAgentStreamEvent(
                toolCallDeltas = listOf(
                    AiAgentToolCallDelta(index = 0, name = "search", argumentsDelta = "牙\"}"),
                ),
                finishReason = "tool_calls",
            ),
        )
        accumulator.accept(AiAgentStreamEvent(done = true))

        val turn = accumulator.toTurn()
        assertTrue(accumulator.hasToolCalls)
        assertTrue(accumulator.sawDone)
        assertEquals("需要检索。", turn.message.reasoningContent)
        assertEquals("semantic_search", turn.message.toolCalls.single().name)
        assertEquals("{\"query\":\"洗牙\"}", turn.message.toolCalls.single().arguments)
    }

    @Test
    fun keepsTerminalAnswerContent() {
        val accumulator = AiAgentStreamAccumulator()
        accumulator.accept(AiAgentStreamEvent(contentDelta = "根据你的"))
        accumulator.accept(AiAgentStreamEvent(contentDelta = "记录", finishReason = "stop"))

        val turn = accumulator.toTurn()
        assertFalse(accumulator.hasToolCalls)
        assertEquals("根据你的记录", turn.message.content)
        assertEquals("stop", turn.finishReason)
    }
}
