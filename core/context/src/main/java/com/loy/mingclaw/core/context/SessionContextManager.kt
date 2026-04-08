package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionEvent
import kotlinx.coroutines.flow.Flow

interface SessionContextManager {
    suspend fun createSession(title: String? = null): Result<Session>
    suspend fun getSession(sessionId: String): Result<Session>
    suspend fun addMessage(sessionId: String, message: Message): Result<Message>
    suspend fun getConversationHistory(sessionId: String, limit: Int? = null): Result<List<Message>>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    fun observeSessionEvents(sessionId: String): Flow<SessionEvent>
}
