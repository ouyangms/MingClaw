package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.context.ContextWindowManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.model.TokenBudget
import com.loy.mingclaw.core.model.context.ContextComponent
import com.loy.mingclaw.core.model.context.SessionContext
import com.loy.mingclaw.core.model.context.WindowStatistics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ContextWindowManagerImpl @Inject constructor(
    private val tokenEstimator: TokenEstimator,
    private val configManager: ConfigManager,
) : ContextWindowManager {

    override fun calculateTokenBudget(): TokenBudget {
        val config = configManager.getConfig()
        return TokenBudget.calculate(maxTokens = config.maxTokens)
    }

    override fun allocateTokenBudget(budget: TokenBudget, components: List<ContextComponent>): Map<String, Int> {
        val allocation = mutableMapOf<String, Int>()
        var remaining = budget.totalTokens - budget.systemTokens
        for (component in components.sortedByDescending { it.priority }) {
            val allocated = minOf(component.requestedTokens, remaining, component.maxTokens)
            allocation[component.id] = allocated
            remaining -= allocated
            if (remaining <= 0) break
        }
        return allocation
    }

    override fun estimateTokens(content: String): Int = tokenEstimator.estimate(content)

    override fun shouldCompress(context: SessionContext): Boolean {
        val config = configManager.getConfig()
        val threshold = (config.maxTokens * 0.8).toInt()
        val contextTokens = tokenEstimator.estimateMessages(context.messages)
        return contextTokens > threshold
    }

    override fun getWindowStatistics(): WindowStatistics = WindowStatistics()
}
