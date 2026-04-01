package com.loy.mingclaw.core.data.repository

import app.cash.turbine.test
import com.loy.mingclaw.core.data.mapper.asDomain
import com.loy.mingclaw.core.data.mapper.asEntity
import com.loy.mingclaw.core.data.repository.internal.OfflineFirstSessionRepository
import com.loy.mingclaw.core.database.dao.MessageDao
import com.loy.mingclaw.core.database.dao.SessionDao
import com.loy.mingclaw.core.database.entity.MessageEntity
import com.loy.mingclaw.core.database.entity.SessionEntity
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OfflineFirstSessionRepositoryTest {

    private val sessionDao = mockk<SessionDao>()
    private val messageDao = mockk<MessageDao>()
    private lateinit var repository: OfflineFirstSessionRepository

    @Before
    fun setup() {
        repository = OfflineFirstSessionRepository(
            sessionDao = sessionDao,
            messageDao = messageDao,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun createSession_insertsAndReturnsSession() = runTest {
        coEvery { sessionDao.insert(any()) } returns Unit

        val session = repository.createSession("Test Session")

        assertEquals("Test Session", session.title)
        assertNotNull(session.id)
        coVerify { sessionDao.insert(match { it.title == "Test Session" }) }
    }

    @Test
    fun getSession_returnsSessionIfExists() = runTest {
        val entity = SessionEntity(
            id = "s1", title = "Test", createdAt = 0, updatedAt = 0,
            metadata = "{}", status = "Active",
        )
        coEvery { sessionDao.getById("s1") } returns entity

        val result = repository.getSession("s1")
        assertNotNull(result)
        assertEquals("s1", result!!.id)
    }

    @Test
    fun getSession_returnsNullIfNotExists() = runTest {
        coEvery { sessionDao.getById("missing") } returns null

        val result = repository.getSession("missing")
        assertNull(result)
    }

    @Test
    fun updateSession_updatesTimestamp() = runTest {
        coEvery { sessionDao.update(any()) } returns Unit

        val session = com.loy.mingclaw.core.model.context.Session(
            id = "s1", title = "Updated",
            createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
            updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
        )
        val result = repository.updateSession(session)
        assertEquals("Updated", result.title)
        coVerify { sessionDao.update(any()) }
    }

    @Test
    fun deleteSession_delegatesToDao() = runTest {
        coEvery { sessionDao.delete("s1") } returns Unit

        repository.deleteSession("s1")

        coVerify { sessionDao.delete("s1") }
    }

    @Test
    fun addMessage_insertsAndUpdatesSessionTimestamp() = runTest {
        val sessionEntity = SessionEntity(
            id = "s1", title = "Test", createdAt = 0, updatedAt = 0,
            metadata = "{}", status = "Active",
        )
        coEvery { messageDao.insert(any()) } returns Unit
        coEvery { sessionDao.getById("s1") } returns sessionEntity
        coEvery { sessionDao.update(any()) } returns Unit

        val message = Message(
            id = "", sessionId = "s1", role = MessageRole.User,
            content = "Hello", timestamp = null,
        )
        val result = repository.addMessage("s1", message)

        assertEquals("s1", result.sessionId)
        assertEquals("Hello", result.content)
        assertNotNull(result.id)
        assertNotNull(result.timestamp)
        coVerify { messageDao.insert(any()) }
        coVerify { sessionDao.update(any()) }
    }

    @Test
    fun getMessages_returnsMappedMessages() = runTest {
        val entities = listOf(
            MessageEntity("m1", "s1", "User", "Hello", 0, null, null),
            MessageEntity("m2", "s1", "Assistant", "Hi there", 1, null, null),
        )
        coEvery { messageDao.getAllBySessionId("s1") } returns entities

        val result = repository.getMessages("s1")
        assertEquals(2, result.size)
        assertEquals("Hello", result[0].content)
        assertEquals("Hi there", result[1].content)
    }

    @Test
    fun observeAllSessions_returnsMappedFlow() = runTest {
        val entities = listOf(
            SessionEntity("s1", "Session 1", 0, 0, "{}", "Active"),
        )
        every { sessionDao.observeAll() } returns flowOf(entities)

        repository.observeAllSessions().test {
            val sessions = awaitItem()
            assertEquals(1, sessions.size)
            assertEquals("s1", sessions[0].id)
            awaitComplete()
        }
    }

    @Test
    fun observeMessages_returnsMappedFlow() = runTest {
        val entities = listOf(
            MessageEntity("m1", "s1", "User", "Hello", 0, null, null),
        )
        every { messageDao.observeBySessionId("s1") } returns flowOf(entities)

        repository.observeMessages("s1").test {
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals("Hello", messages[0].content)
            awaitComplete()
        }
    }

    @Test
    fun deleteMessages_delegatesToDao() = runTest {
        coEvery { messageDao.deleteBySessionId("s1") } returns Unit

        repository.deleteMessages("s1")

        coVerify { messageDao.deleteBySessionId("s1") }
    }
}
