package com.loy.mingclaw.core.model.context

data class TokenStats(
    val totalTokens: Int,
    val userTokens: Int,
    val assistantTokens: Int,
)

sealed interface SessionEvent {
    data class Created(val session: Session) : SessionEvent
    data class MessageAdded(val message: Message) : SessionEvent
    data class MessageUpdated(val message: Message) : SessionEvent
    data class MessageDeleted(val messageId: String) : SessionEvent
    data class StatusChanged(val status: SessionStatus) : SessionEvent
    data class Deleted(val sessionId: String) : SessionEvent
}

data class ContextComponent(
    val id: String,
    val name: String,
    val requestedTokens: Int,
    val maxTokens: Int = Int.MAX_VALUE,
    val priority: Int = 0,
)

data class WindowStatistics(
    val averageTokenUsage: Double = 0.0,
    val peakTokenUsage: Int = 0,
    val compressionCount: Int = 0,
)
