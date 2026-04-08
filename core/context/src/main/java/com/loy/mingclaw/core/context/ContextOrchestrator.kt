package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.context.model.ConversationContext
import com.loy.mingclaw.core.context.model.ContextStats
import kotlinx.coroutines.flow.Flow

interface ContextOrchestrator {
    suspend fun buildContext(sessionId: String, userMessage: String): Result<ConversationContext>
    fun observeContextStats(): Flow<ContextStats>
}
