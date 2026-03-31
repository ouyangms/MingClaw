package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.context.SessionStatus
import com.loy.mingclaw.core.context.internal.SessionContextManagerImpl
import com.loy.mingclaw.core.context.internal.TokenEstimatorImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionContextManagerImplTest {
    private lateinit var manager: SessionContextManagerImpl
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        manager = SessionContextManagerImpl(TokenEstimatorImpl(), testDispatcher)
    }

    @Test
    fun `createSession creates session with default title`() = runTest(testDispatcher) {
        val result = manager.createSession()
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertTrue(session.title.isNotEmpty())
        assertEquals(SessionStatus.Active, session.status)
    }

    @Test
    fun `createSession creates session with custom title`() = runTest(testDispatcher) {
        val result = manager.createSession(title = "My Chat")
        assertTrue(result.isSuccess)
        assertEquals("My Chat", result.getOrThrow().title)
    }

    @Test
    fun `getSession returns created session`() = runTest(testDispatcher) {
        val created = manager.createSession(title = "Test").getOrThrow()
        val result = manager.getSession(created.id)
        assertTrue(result.isSuccess)
        assertEquals("Test", result.getOrThrow().title)
    }

    @Test
    fun `getSession returns failure for unknown id`() = runTest(testDispatcher) {
        val result = manager.getSession("nonexistent")
        assertTrue(result.isFailure)
    }

    @Test
    fun `addMessage stores message in session`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "Chat").getOrThrow()
        val message = Message(id = "msg-1", sessionId = session.id, role = MessageRole.User, content = "Hello!")
        val result = manager.addMessage(session.id, message)
        assertTrue(result.isSuccess)
        assertEquals("Hello!", result.getOrThrow().content)
    }

    @Test
    fun `getConversationHistory returns messages`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "Chat").getOrThrow()
        manager.addMessage(session.id, Message(id = "1", sessionId = session.id, role = MessageRole.User, content = "Hi"))
        manager.addMessage(session.id, Message(id = "2", sessionId = session.id, role = MessageRole.Assistant, content = "Hello"))
        val history = manager.getConversationHistory(session.id)
        assertTrue(history.isSuccess)
        assertEquals(2, history.getOrThrow().size)
    }

    @Test
    fun `getConversationHistory with limit`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "Chat").getOrThrow()
        repeat(5) { i ->
            manager.addMessage(session.id, Message(id = "msg-$i", sessionId = session.id, role = MessageRole.User, content = "Msg $i"))
        }
        val history = manager.getConversationHistory(session.id, limit = 3)
        assertTrue(history.isSuccess)
        assertEquals(3, history.getOrThrow().size)
    }

    @Test
    fun `deleteSession removes session`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "To Delete").getOrThrow()
        assertTrue(manager.deleteSession(session.id).isSuccess)
        assertTrue(manager.getSession(session.id).isFailure)
    }

    @Test
    fun `archiveSession changes status`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "Archive Me").getOrThrow()
        manager.archiveSession(session.id)
        assertEquals(SessionStatus.Archived, manager.getSession(session.id).getOrThrow().status)
    }

    @Test
    fun `getAllSessions returns active sessions only`() = runTest(testDispatcher) {
        manager.createSession(title = "Active")
        val s2 = manager.createSession(title = "Archive").getOrThrow()
        manager.archiveSession(s2.id)
        val sessions = manager.getAllSessions(includeArchived = false)
        assertTrue(sessions.isSuccess)
        assertEquals(1, sessions.getOrThrow().size)
    }

    @Test
    fun `getAllSessions includes archived when requested`() = runTest(testDispatcher) {
        manager.createSession(title = "Active")
        val s2 = manager.createSession(title = "Archive").getOrThrow()
        manager.archiveSession(s2.id)
        val sessions = manager.getAllSessions(includeArchived = true)
        assertTrue(sessions.isSuccess)
        assertEquals(2, sessions.getOrThrow().size)
    }

    @Test
    fun `getSessionContext returns context with messages`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "Context").getOrThrow()
        manager.addMessage(session.id, Message(id = "1", sessionId = session.id, role = MessageRole.User, content = "Hello"))
        val context = manager.getSessionContext(session.id)
        assertTrue(context.isSuccess)
        assertEquals(1, context.getOrThrow().messageCount)
    }
}
