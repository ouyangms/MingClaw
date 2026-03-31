package com.loy.mingclaw.core.model.context

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Instant? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val editedAt: Instant? = null,
)

enum class MessageRole {
    User, Assistant, System, Tool
}

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)
