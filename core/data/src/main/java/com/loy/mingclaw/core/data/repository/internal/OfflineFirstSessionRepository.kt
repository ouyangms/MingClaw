package com.loy.mingclaw.core.data.repository.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.data.mapper.asDomain
import com.loy.mingclaw.core.data.mapper.asEntity
import com.loy.mingclaw.core.data.repository.SessionRepository
import com.loy.mingclaw.core.database.dao.MessageDao
import com.loy.mingclaw.core.database.dao.SessionDao
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.context.Session
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class OfflineFirstSessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : SessionRepository {

    override suspend fun createSession(title: String): Session = withContext(ioDispatcher) {
        val now = Clock.System.now()
        val session = Session(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
        )
        sessionDao.insert(session.asEntity())
        session
    }

    override suspend fun getSession(sessionId: String): Session? = withContext(ioDispatcher) {
        sessionDao.getById(sessionId)?.asDomain()
    }

    override suspend fun updateSession(session: Session): Session = withContext(ioDispatcher) {
        val updated = session.copy(updatedAt = Clock.System.now())
        sessionDao.update(updated.asEntity())
        updated
    }

    override suspend fun deleteSession(sessionId: String) = withContext(ioDispatcher) {
        sessionDao.delete(sessionId)
    }

    override fun observeAllSessions(): Flow<List<Session>> =
        sessionDao.observeAll().map { sessions -> sessions.map { it.asDomain() } }
            .flowOn(ioDispatcher)

    override fun observeSession(sessionId: String): Flow<Session?> =
        sessionDao.observeById(sessionId).map { it?.asDomain() }
            .flowOn(ioDispatcher)

    override suspend fun addMessage(sessionId: String, message: Message): Message =
        withContext(ioDispatcher) {
            val now = Clock.System.now()
            val persisted = message.copy(
                id = if (message.id.isBlank()) UUID.randomUUID().toString() else message.id,
                sessionId = sessionId,
                timestamp = message.timestamp ?: now,
            )
            messageDao.insert(persisted.asEntity())

            // Update session's updatedAt timestamp
            val session = sessionDao.getById(sessionId)
            if (session != null) {
                sessionDao.update(session.copy(updatedAt = now.toEpochMilliseconds()))
            }
            persisted
        }

    override suspend fun getMessages(sessionId: String, limit: Int): List<Message> =
        withContext(ioDispatcher) {
            if (limit == Int.MAX_VALUE) {
                messageDao.getAllBySessionId(sessionId).map { it.asDomain() }
            } else {
                messageDao.getBySessionId(sessionId, limit).map { it.asDomain() }
            }
        }

    override fun observeMessages(sessionId: String): Flow<List<Message>> =
        messageDao.observeBySessionId(sessionId).map { messages -> messages.map { it.asDomain() } }

    override suspend fun deleteMessages(sessionId: String) = withContext(ioDispatcher) {
        messageDao.deleteBySessionId(sessionId)
    }
}
