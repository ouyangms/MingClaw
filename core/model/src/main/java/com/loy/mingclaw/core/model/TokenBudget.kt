package com.loy.mingclaw.core.model

data class TokenBudget(
    val totalTokens: Int,
    val systemTokens: Int,
    val memoryTokens: Int,
    val toolTokens: Int,
    val conversationTokens: Int,
) {
    companion object {
        fun calculate(maxTokens: Int, systemReserved: Int = 1000): TokenBudget {
            val available = maxTokens - systemReserved
            return TokenBudget(
                totalTokens = maxTokens,
                systemTokens = systemReserved,
                memoryTokens = (available * 0.2).toInt(),
                toolTokens = (available * 0.2).toInt(),
                conversationTokens = (available * 0.6).toInt(),
            )
        }
    }
}
