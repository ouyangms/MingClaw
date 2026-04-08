package com.loy.mingclaw.core.context.model

import com.loy.mingclaw.core.model.TokenBudget

data class TokenUsage(
    val systemTokens: Int,
    val memoryTokens: Int,
    val conversationTokens: Int,
    val totalTokens: Int,
    val budget: TokenBudget,
)
