package com.loy.mingclaw.core.evolution.internal.prompts

import com.loy.mingclaw.core.model.llm.ChatMessage

internal object BehaviorAnalysisPrompt {

    fun analyzePatterns(decisionHistory: String, currentRules: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """你是一个 AI 行为分析专家。分析 Agent 的决策历史，识别成功和失败的模式。
返回 JSON 对象：
{
  "decisionCount": 数字,
  "successRate": 0.0-1.0,
  "improvementAreas": ["改进领域1", "改进领域2"],
  "suggestedRules": ["建议规则1", "建议规则2"]
}""",
        ),
        ChatMessage(
            role = "user",
            content = "决策历史：\n$decisionHistory\n\n当前行为规则（AGENTS.md）：\n$currentRules",
        ),
    )

    fun suggestRules(analysis: String, currentRules: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """你是一个 AI 规则优化专家。基于行为分析结果，建议具体的行为规则修改。
返回 JSON 数组，每个元素包含：
- ruleId: 规则标识
- updateType: ADD/MODIFY/DELETE
- currentRule: 当前规则内容（如果是 MODIFY 或 DELETE）
- proposedRule: 建议的新规则内容
- reason: 修改原因
- confidence: 置信度 (0.0-1.0)""",
        ),
        ChatMessage(
            role = "user",
            content = "分析结果：\n$analysis\n\n当前规则：\n$currentRules",
        ),
    )
}
