package com.loy.mingclaw.core.context.model

import com.loy.mingclaw.core.model.llm.ChatMessage
import com.loy.mingclaw.core.model.memory.Memory

data class ConversationContext(
    val systemPrompt: String,
    val messages: List<ChatMessage>,
    val tokenUsage: TokenUsage,
    val memories: List<Memory>,
)
