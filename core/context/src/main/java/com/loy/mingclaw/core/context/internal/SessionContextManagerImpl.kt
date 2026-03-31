package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.context.SessionContextManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionContext
import com.loy.mingclaw.core.model.context.SessionEvent
import com.loy.mingclaw.core.model.context.SessionStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SessionContextManagerImpl @Inject constructor(
    private val tokenEstimator: TokenEstimator,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : SessionContextManager {

    private val sessions = ConcurrentHashMap<String, Session>()
    private val messages = ConcurrentHashMap<String, MutableList<Message>>()
    private val sessionEvents = ConcurrentHashMap<String, MutableSharedFlow<SessionEvent>>()

    override suspend fun createSession(
        title: String?,
        metadata: Map<String, String>,
    ): Result<Session> = withContext(dispatcher) {
        val sessionId = UUID.randomUUID().toString()
        val now = kotlinx.datetime.Clock.System.now()
        val session = Session(
            id = sessionId,
            title = title ?: "Session ${now.toEpochMilliseconds()}",
            createdAt = now,
            updatedAt = now,
            metadata = metadata,
            status = SessionStatus.Active,
        )
        sessions[sessionId] = session
        messages[sessionId] = mutableListOf()
        sessionEvents[sessionId] = MutableSharedFlow(replay = 10)
        emitEvent(sessionId, SessionEvent.Created(session))
        Result.success(session)
    }

    override suspend fun getSession(sessionId: String): Result<Session> {
        val session = sessions[sessionId]
            ?: return Result.failure(IllegalArgumentException("Session not found: $sessionId"))
        return Result.success(session)
    }

    override suspend fun getSessionContext(sessionId: String): Result<SessionContext> {
        val session = sessions[sessionId]
            ?: return Result.failure(IllegalArgumentException("Session not found: $sessionId"))
        val sessionMessages = messages[sessionId] ?: emptyList()
        return Result.success(
            SessionContext(
                sessionId = session.id,
                title = session.title,
                messages = sessionMessages.toList(),
                metadata = session.metadata,
                status = session.status,
            )
        )
    }

    override suspend fun addMessage(sessionId: String, message: Message): Result<Message> =
        withContext(dispatcher) {
            val session = sessions[sessionId]
                ?: return@withContext Result.failure(IllegalArgumentException("Session not found: $sessionId"))
            val now = kotlinx.datetime.Clock.System.now()
            val savedMessage = message.copy(sessionId = sessionId, timestamp = message.timestamp ?: now)
            messages.getOrPut(sessionId) { mutableListOf() }.add(savedMessage)
            sessions[sessionId] = session.copy(updatedAt = now)
            emitEvent(sessionId, SessionEvent.MessageAdded(savedMessage))
            Result.success(savedMessage)
        }

    override suspend fun getConversationHistory(sessionId: String, limit: Int?): Result<List<Message>> {
        val sessionMessages = messages[sessionId]
            ?: return Result.failure(IllegalArgumentException("Session not found: $sessionId"))
        val result = if (limit != null) sessionMessages.takeLast(limit) else sessionMessages.toList()
        return Result.success(result)
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        sessions.remove(sessionId)
        messages.remove(sessionId)
        sessionEvents.remove(sessionId)
        return Result.success(Unit)
    }

    override suspend fun archiveSession(sessionId: String): Result<Unit> {
        val session = sessions[sessionId]
            ?: return Result.failure(IllegalArgumentException("Session not found: $sessionId"))
        val now = kotlinx.datetime.Clock.System.now()
        sessions[sessionId] = session.copy(status = SessionStatus.Archived, updatedAt = now)
        emitEvent(sessionId, SessionEvent.StatusChanged(SessionStatus.Archived))
        return Result.success(Unit)
    }

    override suspend fun getAllSessions(includeArchived: Boolean): Result<List<Session>> {
        val result = if (includeArchived) {
            sessions.values.toList()
        } else {
            sessions.values.filter { it.status == SessionStatus.Active }
        }
        return Result.success(result)
    }

    override fun watchSession(sessionId: String): Flow<SessionEvent> {
        return sessionEvents.getOrPut(sessionId) {
            MutableSharedFlow(replay = 10)
        }.asSharedFlow()
    }

    private suspend fun emitEvent(sessionId: String, event: SessionEvent) {
        sessionEvents[sessionId]?.emit(event)
    }
}
