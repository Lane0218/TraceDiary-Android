package com.tracediary.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.buildJsonArray

class OpenAiCompatibleAgentClientTest {
    private val client = OpenAiCompatibleAgentClient()

    @Test
    fun parseTurn_readsToolCallsWithoutExecutingUnknownContent() {
        val turn = client.parseTurn(
            """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"semantic_search","arguments":"{\"query\":\"洗牙\"}"}}]}}]}""",
        )

        assertEquals("semantic_search", turn.message.toolCalls.single().name)
        assertEquals("{\"query\":\"洗牙\"}", turn.message.toolCalls.single().arguments)
    }

    @Test
    fun resolveEndpoint_requiresHttpsAndKeepsExplicitEndpoint() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            client.resolveEndpoint("https://api.deepseek.com/"),
        )
        assertEquals(
            "https://example.com/v1/chat/completions",
            client.resolveEndpoint("https://example.com/v1/chat/completions"),
        )
        assertTrue(runCatching { client.resolveEndpoint("http://example.com/v1") }.isFailure)
    }

    @Test
    fun parseStreamEvent_readsTextAndToolCallFragments() {
        val text = client.parseStreamEvent(
            """{"choices":[{"delta":{"content":"根据你的记录"},"finish_reason":null}]}""",
        )
        assertEquals("根据你的记录", text.contentDelta)

        val tool = client.parseStreamEvent(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"semantic_search","arguments":"{\\\"query\\\":"}}]},"finish_reason":null}]}""",
        )
        assertEquals(0, tool.toolCallDeltas.single().index)
        assertEquals("semantic_search", tool.toolCallDeltas.single().name)
        assertEquals("call_1", tool.toolCallDeltas.single().id)
    }

    @Test
    fun parseStreamEvent_ignoresEmptyChoicePayload() {
        val done = client.parseStreamEvent("{}")
        assertTrue(done.contentDelta == null)
    }

    @Test
    fun parseStreamEvent_keepsReasoningSeparateFromVisibleContent() {
        val event = client.parseStreamEvent(
            """{"choices":[{"delta":{"reasoning_content":"内部推理"},"finish_reason":null}]}""",
        )

        assertEquals(4, event.reasoningDeltaChars)
        assertEquals("内部推理", event.reasoningDelta)
        assertTrue(event.contentDelta == null)
    }

    @Test
    fun buildRequestPayload_doesNotSendLocalDiagnostics() {
        val payload = client.buildRequestPayload(
            request = AiAgentRequest(
                baseUrl = "https://api.deepseek.com",
                apiKey = "unused",
                model = "deepseek-chat",
                messages = listOf(AiAgentMessage(role = "user", content = "测试")),
                tools = buildJsonArray { },
                diagnostics = AiAgentRequestDiagnostics(
                    traceId = "trace-secret-marker",
                    stage = "stage-secret-marker",
                ),
            ),
            stream = true,
        )

        assertFalse(payload.contains("trace-secret-marker"))
        assertFalse(payload.contains("stage-secret-marker"))
    }

    @Test
    fun buildRequestPayload_addsExplicitLowThinkingWhenRequested() {
        val payload = client.buildRequestPayload(
            request = AiAgentRequest(
                baseUrl = "https://api.deepseek.com",
                apiKey = "unused",
                model = "deepseek-v4-flash",
                messages = listOf(AiAgentMessage(role = "user", content = "测试")),
                tools = buildJsonArray { },
                thinking = AiAgentThinking.Low,
            ),
            stream = true,
        )

        assertTrue(payload.contains("\"thinking\":{\"type\":\"enabled\"}"))
        assertTrue(payload.contains("\"reasoning_effort\":\"low\""))
    }

    @Test
    fun buildRequestPayload_canDisableThinkingForToolLoop() {
        val payload = client.buildRequestPayload(
            request = AiAgentRequest(
                baseUrl = "https://api.deepseek.com",
                apiKey = "unused",
                model = "deepseek-v4-flash",
                messages = listOf(AiAgentMessage(role = "user", content = "测试")),
                tools = buildJsonArray { },
                thinking = AiAgentThinking.Disabled,
            ),
            stream = true,
        )

        assertTrue(payload.contains("\"thinking\":{\"type\":\"disabled\"}"))
        assertFalse(payload.contains("reasoning_effort"))
    }

    @Test
    fun buildRequestPayload_returnsReasoningForDeepSeekToolContinuation() {
        val payload = client.buildRequestPayload(
            request = AiAgentRequest(
                baseUrl = "https://api.deepseek.com",
                apiKey = "unused",
                model = "deepseek-v4-flash",
                messages = listOf(
                    AiAgentMessage(
                        role = "assistant",
                        reasoningContent = "reasoning-marker",
                        toolCalls = listOf(
                            AiAgentToolCall("call_1", "semantic_search", "{\"query\":\"测试\"}"),
                        ),
                    ),
                    AiAgentMessage(role = "tool", content = "[]", toolCallId = "call_1"),
                ),
                tools = buildJsonArray { },
                thinking = AiAgentThinking.Low,
            ),
            stream = true,
        )

        assertTrue(payload.contains("\"reasoning_content\":\"reasoning-marker\""))
    }
}
