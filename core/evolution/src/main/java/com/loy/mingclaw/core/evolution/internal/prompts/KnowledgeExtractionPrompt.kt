package com.loy.mingclaw.core.evolution.internal.prompts

import com.loy.mingclaw.core.model.llm.ChatMessage

internal object KnowledgeExtractionPrompt {

    fun build(conversationContent: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """你是一个知识提取专家。从对话内容中提取有价值的知识点。
每个知识点包含以下字段，以 JSON 数组格式返回：
- type: FACT/CONCEPT/PROCEDURE/PRINCIPLE/PREFERENCE/PATTERN/EXPERIENCE
- content: 知识内容（简洁的一句话）
- confidence: 置信度 (0.0-1.0)
- importance: 重要性 (0.0-1.0)
- categories: 分类数组，可选值: USER_PROFILE/TASK_PATTERN/DOMAIN_KNOWLEDGE/COMMON_SENSE/PREFERENCE/CONTEXT
- tags: 标签数组（字符串）

只返回 JSON 数组，不要其他文字。如果没有有价值的知识点，返回空数组 []。""",
        ),
        ChatMessage(
            role = "user",
            content = "请从以下对话中提取知识点：\n\n$conversationContent",
        ),
    )
}
