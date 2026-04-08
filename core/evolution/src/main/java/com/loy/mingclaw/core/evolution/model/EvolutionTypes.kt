package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class EvolutionType { BEHAVIOR, KNOWLEDGE, CAPABILITY }

@Serializable
enum class EvolutionTrigger {
    USER_FEEDBACK, TASK_FAILURE, PERFORMANCE_DEGRADATION,
    SCHEDULED, MANUAL, KNOWLEDGE_THRESHOLD, CAPABILITY_GAP
}

@Serializable
enum class EvolutionPriority { LOW, MEDIUM, HIGH, IMMEDIATE }

@Serializable
data class EvolutionContext(
    val sessionId: String,
    val feedbackScore: Float,
    val taskSuccessRate: Float,
    val memoryCount: Int,
    val lastEvolution: Instant?,
)

@Serializable
data class EvolutionProposal(
    val id: String,
    val type: EvolutionType,
    val description: String,
    val reason: String,
    val expectedImpact: String,
    val priority: EvolutionPriority,
    val confidence: Float,
)

@Serializable
data class EvolutionResult(
    val proposalId: String,
    val success: Boolean,
    val changes: List<String>,
    val error: String? = null,
)
