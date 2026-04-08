package com.loy.mingclaw.core.context.model

data class ContextStats(
    val sessionId: String,
    val totalTokensUsed: Int,
    val compressionCount: Int,
    val memoriesInjected: Int,
    val budgetUtilization: Float,
)
