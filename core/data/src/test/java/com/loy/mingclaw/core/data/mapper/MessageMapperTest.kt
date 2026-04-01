package com.loy.mingclaw.core.data.mapper

import com.loy.mingclaw.core.database.entity.MessageEntity
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.context.ToolCall
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageMapperTest {

    private val testInstant = Instant.fromEpochMilliseconds(1700000000000)

    private val testMessage = Message(
        id = "msg-1",
        sessionId = "session-1",
        role = MessageRole.User,
        content = "Hello world",
        timestamp = testInstant,
        toolCalls = listOf(
            ToolCall(id = "tc-1", name = "search", arguments = """{"q":"test"}"""),
        ),
        editedAt = null,
    )

    @Test
    fun asEntity_mapsAllFields() {
        val entity = testMessage.asEntity()
        assertEquals(testMessage.id, entity.id)
        assertEquals(testMessage.sessionId, entity.sessionId)
        assertEquals(testMessage.role.name, entity.role)
        assertEquals(testMessage.content, entity.content)
        assertEquals(testMessage.timestamp?.toEpochMilliseconds(), entity.timestamp)
        assertNull(entity.editedAt)
    }

    @Test
    fun asDomain_mapsAllFields() {
        val entity = MessageEntity(
            id = "msg-1",
            sessionId = "session-1",
            role = "User",
            content = "Hello world",
            timestamp = 1700000000000,
            toolCalls = """[{"id":"tc-1","name":"search","arguments":"{\"q\":\"test\"}"}]""",
            editedAt = null,
        )
        val domain = entity.asDomain()
        assertEquals(entity.id, domain.id)
        assertEquals(entity.sessionId, domain.sessionId)
        assertEquals(MessageRole.User, domain.role)
        assertEquals(entity.content, domain.content)
        assertEquals(1, domain.toolCalls.size)
        assertEquals("tc-1", domain.toolCalls[0].id)
        assertEquals("search", domain.toolCalls[0].name)
    }

    @Test
    fun roundTrip_preservesBasicFields() {
        val entity = MessageEntity(
            id = "msg-rt",
            sessionId = "sess-rt",
            role = "Assistant",
            content = "Response text",
            timestamp = 1700000000000,
            toolCalls = null,
            editedAt = null,
        )
        val roundTripped = entity.asDomain().asEntity()
        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.sessionId, roundTripped.sessionId)
        assertEquals(entity.role, roundTripped.role)
        assertEquals(entity.content, roundTripped.content)
        assertEquals(entity.timestamp, roundTripped.timestamp)
    }

    @Test
    fun roundTrip_preservesToolCalls() {
        val domain = testMessage
        val roundTripped = domain.asEntity().asDomain()
        assertEquals(domain.id, roundTripped.id)
        assertEquals(domain.toolCalls.size, roundTripped.toolCalls.size)
        assertEquals(domain.toolCalls[0].id, roundTripped.toolCalls[0].id)
        assertEquals(domain.toolCalls[0].name, roundTripped.toolCalls[0].name)
        assertEquals(domain.toolCalls[0].arguments, roundTripped.toolCalls[0].arguments)
    }

    @Test
    fun asEntity_emptyToolCalls_producesNull() {
        val message = testMessage.copy(toolCalls = emptyList())
        val entity = message.asEntity()
        assertNull(entity.toolCalls)
    }

    @Test
    fun asDomain_nullToolCalls_producesEmptyList() {
        val entity = MessageEntity(
            id = "msg-nt",
            sessionId = "sess",
            role = "User",
            content = "Hi",
            timestamp = null,
            toolCalls = null,
            editedAt = null,
        )
        val domain = entity.asDomain()
        assertEquals(emptyList<ToolCall>(), domain.toolCalls)
    }

    @Test
    fun asDomain_handlesInvalidRole() {
        val entity = MessageEntity(
            id = "msg-ir",
            sessionId = "sess",
            role = "InvalidRole",
            content = "test",
            timestamp = null,
            toolCalls = null,
            editedAt = null,
        )
        val domain = entity.asDomain()
        assertEquals(MessageRole.User, domain.role)
    }
}
