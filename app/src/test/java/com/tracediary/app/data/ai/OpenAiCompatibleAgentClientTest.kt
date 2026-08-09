package com.tracediary.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
