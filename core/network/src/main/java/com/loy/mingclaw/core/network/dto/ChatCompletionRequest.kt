package com.loy.mingclaw.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 4096,
    val stream: Boolean = false,
)

@Serializable
data class ChatMessageDto(
    val role: String,
    val content: String,
)
