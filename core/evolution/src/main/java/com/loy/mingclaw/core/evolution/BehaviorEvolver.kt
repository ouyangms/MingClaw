package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.AgentDecision
import com.loy.mingclaw.core.evolution.model.BehaviorAnalysis
import com.loy.mingclaw.core.evolution.model.RuleUpdate

interface BehaviorEvolver {
    suspend fun recordDecision(decision: AgentDecision): Result<Unit>
    suspend fun analyzePatterns(): Result<BehaviorAnalysis>
    suspend fun suggestRuleUpdates(): Result<List<RuleUpdate>>
    suspend fun applyRuleUpdates(updates: List<RuleUpdate>): Result<Unit>
    suspend fun rollbackToVersion(version: String): Result<Unit>
}
