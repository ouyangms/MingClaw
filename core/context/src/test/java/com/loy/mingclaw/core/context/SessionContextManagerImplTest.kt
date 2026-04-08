package com.loy.mingclaw.core.context

import app.cash.turbine.test
import com.loy.mingclaw.core.context.internal.SessionContextManagerImpl
import com.loy.mingclaw.core.data.repository.SessionRepository
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionContextManagerImplTest {
    private val sessionRepository = mockk<SessionRepository>()
    private lateinit var manager: SessionContextManagerImpl

    @Before
    fun setup() {
        manager = SessionContextManagerImpl(
            sessionRepository = sessionRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `createSession delegates to repository`() = runTest {
        val now = Clock.System.now()
        val session = Session(id = "s1", title = "My Chat", createdAt = now, updatedAt = now)
        coEvery { sessionRepository.createSession("My Chat") } returns session

        val result = manager.createSession(title = "My Chat")
        assertTrue(result.isSuccess)
        assertEquals("s1", result.getOrThrow().id)
        coVerify { sessionRepository.createSession("My Chat") }
    }

    @Test
    fun `createSession with null title generates default`() = runTest {
        coEvery { sessionRepository.createSession(any()) } answers {
            Session(id = "s1", title = firstArg(), createdAt = Clock.System.now(), updatedAt = Clock.System.now())
        }

        val result = manager.createSession()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().title.startsWith("Session "))
    }

    @Test
    fun `getSession returns session from repository`() = runTest {
        val now = Clock.System.now()
        val session = Session(id = "s1", title = "Test", createdAt = now, updatedAt = now)
        coEvery { sessionRepository.getSession("s1") } returns session

        val result = manager.getSession("s1")
        assertTrue(result.isSuccess)
        assertEquals("Test", result.getOrThrow().title)
    }

    @Test
    fun `getSession returns failure for missing session`() = runTest {
        coEvery { sessionRepository.getSession("missing") } returns null

        val result = manager.getSession("missing")
        assertTrue(result.isFailure)
    }

    @Test
    fun `addMessage delegates to repository`() = runTest {
        val now = Clock.System.now()
        val message = Message(id = "m1", sessionId = "s1", role = MessageRole.User, content = "Hello", timestamp = now)
        coEvery { sessionRepository.addMessage("s1", any()) } returns message

        val result = manager.addMessage("s1", message)
        assertTrue(result.isSuccess)
        assertEquals("m1", result.getOrThrow().id)
        coVerify { sessionRepository.addMessage("s1", message) }
    }

    @Test
    fun `getConversationHistory returns messages from repository`() = runTest {
        val now = Clock.System.now()
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "Hi", timestamp = now),
            Message(id = "2", sessionId = "s1", role = MessageRole.Assistant, content = "Hello", timestamp = now),
        )
        coEvery { sessionRepository.getMessages("s1") } returns messages

        val result = manager.getConversationHistory("s1")
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `getConversationHistory with limit delegates with limit`() = runTest {
        coEvery { sessionRepository.getMessages("s1", 3) } returns emptyList()

        manager.getConversationHistory("s1", limit = 3)
        coVerify { sessionRepository.getMessages("s1", 3) }
    }

    @Test
    fun `deleteSession delegates to repository`() = runTest {
        coEvery { sessionRepository.deleteSession("s1") } returns Unit

        val result = manager.deleteSession("s1")
        assertTrue(result.isSuccess)
        coVerify { sessionRepository.deleteSession("s1") }
    }

    @Test
    fun `observeSessionEvents maps from observeMessages`() = runTest {
        val now = Clock.System.now()
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "Hi", timestamp = now),
        )
        every { sessionRepository.observeMessages("s1") } returns flowOf(messages)

        manager.observeSessionEvents("s1").test {
            val event = awaitItem()
            assertTrue(event is SessionEvent.MessageAdded)
            assertEquals("1", (event as SessionEvent.MessageAdded).message.id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
