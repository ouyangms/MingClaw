package com.loy.mingclaw.core.model.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmProviderTypesTest {
    @Test
    fun chatMessage_creation() {
        val msg = ChatMessage(role = "user", content = "Hello")
        assertEquals("user", msg.role)
        assertEquals("Hello", msg.content)
    }

    @Test
    fun chatResponse_defaults() {
        val resp = ChatResponse(content = "Hi there")
        assertEquals("Hi there", resp.content)
        assertEquals(0, resp.totalTokens)
    }

    @Test
    fun chatChunk_creation() {
        val chunk = ChatChunk(id = "1", delta = "Hello")
        assertEquals("Hello", chunk.delta)
    }
}
