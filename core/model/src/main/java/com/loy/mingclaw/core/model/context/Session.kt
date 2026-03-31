package com.loy.mingclaw.core.model.context

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val metadata: Map<String, String> = emptyMap(),
    val status: SessionStatus = SessionStatus.Active,
)

enum class SessionStatus {
    Active, Archived, Deleted
}

data class SessionContext(
    val sessionId: String,
    val title: String,
    val messages: List<Message>,
    val metadata: Map<String, String> = emptyMap(),
    val status: SessionStatus = SessionStatus.Active,
) {
    val messageCount: Int get() = messages.size
}
