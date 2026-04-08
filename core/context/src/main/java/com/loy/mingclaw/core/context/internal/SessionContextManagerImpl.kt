package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.context.SessionContextManager
import com.loy.mingclaw.core.data.repository.SessionRepository
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SessionContextManagerImpl @Inject constructor(
    private val sessionRepository: SessionRepository,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : SessionContextManager {

    private val sessionEvents = MutableSharedFlow<SessionEvent>(replay = 10)

    override suspend fun createSession(title: String?): Result<Session> =
        withContext(ioDispatcher) {
            try {
                val session = sessionRepository.createSession(
                    title = title ?: "Session ${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
                )
                sessionEvents.emit(SessionEvent.Created(session))
                Result.success(session)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getSession(sessionId: String): Result<Session> =
        withContext(ioDispatcher) {
            try {
                val session = sessionRepository.getSession(sessionId)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Session not found: $sessionId")
                    )
                Result.success(session)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun addMessage(sessionId: String, message: Message): Result<Message> =
        withContext(ioDispatcher) {
            try {
                val saved = sessionRepository.addMessage(sessionId, message)
                sessionEvents.emit(SessionEvent.MessageAdded(saved))
                Result.success(saved)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getConversationHistory(sessionId: String, limit: Int?): Result<List<Message>> =
        withContext(ioDispatcher) {
            try {
                val messages = if (limit != null) {
                    sessionRepository.getMessages(sessionId, limit)
                } else {
                    sessionRepository.getMessages(sessionId)
                }
                Result.success(messages)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteSession(sessionId: String): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                sessionRepository.deleteSession(sessionId)
                sessionEvents.emit(SessionEvent.Deleted(sessionId))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // MVP: 后续增强 - simple mapping from observeMessages, no complex event aggregation
    override fun observeSessionEvents(sessionId: String): Flow<SessionEvent> =
        sessionRepository.observeMessages(sessionId).map { messages ->
            SessionEvent.MessageAdded(messages.last())
        }
}
