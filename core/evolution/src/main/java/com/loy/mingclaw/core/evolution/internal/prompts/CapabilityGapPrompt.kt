package com.loy.mingclaw.core.evolution.internal.prompts

import com.loy.mingclaw.core.model.llm.ChatMessage

internal object CapabilityGapPrompt {

    fun build(failedTaskDescriptions: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """你是一个能力分析专家。分析失败的任务，识别 Agent 缺失的能力。
返回 JSON 数组，每个元素包含：
- capability: 缺失的能力名称
- currentLevel: NONE/BASIC/INTERMEDIATE/ADVANCED/EXPERT
- desiredLevel: NONE/BASIC/INTERMEDIATE/ADVANCED/EXPERT
- priority: LOW/MEDIUM/HIGH/CRITICAL
如果没有能力缺口，返回空数组 []。""",
        ),
        ChatMessage(
            role = "user",
            content = "以下是最近失败的任务：\n$failedTaskDescriptions",
        ),
    )
}
