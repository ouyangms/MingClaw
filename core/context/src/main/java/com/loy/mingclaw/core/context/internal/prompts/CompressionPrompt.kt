package com.loy.mingclaw.core.context.internal.prompts

import com.loy.mingclaw.core.model.llm.ChatMessage

internal object CompressionPrompt {
    fun build(conversationHistory: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = "你是一个对话摘要专家。请将以下对话历史总结为简洁的摘要，保留关键信息、用户偏好和重要决策。只返回摘要文本，不要其他文字。",
        ),
        ChatMessage(
            role = "user",
            content = "请总结以下对话：\n\n$conversationHistory",
        ),
    )
}
