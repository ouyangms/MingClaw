package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionContext
import com.loy.mingclaw.core.model.context.SessionEvent
import kotlinx.coroutines.flow.Flow

interface SessionContextManager {
    suspend fun createSession(
        title: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ): Result<Session>

    suspend fun getSession(sessionId: String): Result<Session>
    suspend fun getSessionContext(sessionId: String): Result<SessionContext>
    suspend fun addMessage(sessionId: String, message: Message): Result<Message>
    suspend fun getConversationHistory(
        sessionId: String,
        limit: Int? = null,
    ): Result<List<Message>>

    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun archiveSession(sessionId: String): Result<Unit>
    suspend fun getAllSessions(includeArchived: Boolean = false): Result<List<Session>>
    fun watchSession(sessionId: String): Flow<SessionEvent>
}
