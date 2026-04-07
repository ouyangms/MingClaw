package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class RuleUpdateType { ADD, MODIFY, DELETE, REORDER, MERGE }

@Serializable
enum class DecisionOutcome { SUCCESS, FAILURE, PARTIAL }

@Serializable
data class AgentDecision(
    val decisionId: String,
    val timestamp: Instant,
    val ruleApplied: String,
    val reasoning: String,
    val outcome: DecisionOutcome,
)

@Serializable
data class RuleUpdate(
    val id: String,
    val ruleId: String,
    val updateType: RuleUpdateType,
    val currentRule: String,
    val proposedRule: String,
    val reason: String,
    val confidence: Float,
)

@Serializable
data class BehaviorAnalysis(
    val decisionCount: Int,
    val successRate: Float,
    val improvementAreas: List<String>,
    val suggestedRules: List<String>,
)
