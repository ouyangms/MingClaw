package com.loy.mingclaw.core.model.context

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Session serializes and deserializes`() {
        val now = Clock.System.now()
        val original = Session(
            id = "session-1",
            title = "Test Session",
            createdAt = now,
            updatedAt = now,
            metadata = mapOf("key" to "value"),
            status = SessionStatus.Active
        )
        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<Session>(jsonString)
        assertEquals(original, restored)
    }

    @Test
    fun `SessionStatus has expected values`() {
        val statuses = SessionStatus.values()
        assertEquals(3, statuses.size)
        assertEquals(SessionStatus.Active, statuses[0])
        assertEquals(SessionStatus.Archived, statuses[1])
        assertEquals(SessionStatus.Deleted, statuses[2])
    }

    @Test
    fun `SessionContext computes messageCount`() {
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "hi"),
            Message(id = "2", sessionId = "s1", role = MessageRole.Assistant, content = "hello")
        )
        val context = SessionContext(
            sessionId = "s1",
            title = "Test",
            messages = messages,
            metadata = emptyMap(),
            status = SessionStatus.Active
        )
        assertEquals(2, context.messageCount)
    }
}
