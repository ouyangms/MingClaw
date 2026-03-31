package com.loy.mingclaw.core.model.context

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Message serializes and deserializes`() {
        val now = Clock.System.now()
        val original = Message(
            id = "msg-1",
            sessionId = "session-1",
            role = MessageRole.User,
            content = "Hello, how are you?",
            timestamp = now
        )
        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<Message>(jsonString)
        assertEquals(original, restored)
    }

    @Test
    fun `MessageRole has expected values`() {
        val roles = MessageRole.values()
        assertEquals(4, roles.size)
        assertEquals(MessageRole.User, roles[0])
        assertEquals(MessageRole.Assistant, roles[1])
        assertEquals(MessageRole.System, roles[2])
        assertEquals(MessageRole.Tool, roles[3])
    }

    @Test
    fun `ToolCall serializes and deserializes`() {
        val original = ToolCall(
            id = "call-1",
            name = "search",
            arguments = """{"query": "test"}"""
        )
        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<ToolCall>(jsonString)
        assertEquals(original, restored)
    }
}
