package com.loy.mingclaw.core.data.repository

import com.loy.mingclaw.core.model.llm.ChatMessage

data class ChatRequest(
    val sessionId: String,
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
)
