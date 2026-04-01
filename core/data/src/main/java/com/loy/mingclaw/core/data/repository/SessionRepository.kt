package com.loy.mingclaw.core.data.repository

import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.Session
import kotlinx.coroutines.flow.Flow

interface SessionRepository {

    suspend fun createSession(title: String): Session

    suspend fun getSession(sessionId: String): Session?

    suspend fun updateSession(session: Session): Session

    suspend fun deleteSession(sessionId: String)

    fun observeAllSessions(): Flow<List<Session>>

    fun observeSession(sessionId: String): Flow<Session?>

    suspend fun addMessage(sessionId: String, message: Message): Message

    suspend fun getMessages(sessionId: String, limit: Int = Int.MAX_VALUE): List<Message>

    fun observeMessages(sessionId: String): Flow<List<Message>>

    suspend fun deleteMessages(sessionId: String)
}
