package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.TokenBudget
import com.loy.mingclaw.core.model.context.ContextComponent
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.WindowStatistics

interface ContextWindowManager {
    fun calculateTokenBudget(): TokenBudget
    fun allocateTokenBudget(budget: TokenBudget, components: List<ContextComponent>): Map<String, Int>
    fun estimateTokens(content: String): Int
    fun shouldCompress(messages: List<Message>, budget: TokenBudget): Boolean
    fun getWindowStatistics(): WindowStatistics
    fun recordUsage(tokenCount: Int)
    fun recordCompression()
}
