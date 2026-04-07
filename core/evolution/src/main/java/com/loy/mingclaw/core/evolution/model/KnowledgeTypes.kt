package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class KnowledgeType { FACT, CONCEPT, PROCEDURE, PRINCIPLE, PREFERENCE, PATTERN, EXPERIENCE }

@Serializable
enum class KnowledgeCategory { USER_PROFILE, TASK_PATTERN, DOMAIN_KNOWLEDGE, COMMON_SENSE, PREFERENCE, CONTEXT }

@Serializable
enum class Importance { CRITICAL, HIGH, MEDIUM, LOW, TRIVIAL }

@Serializable
data class KnowledgePoint(
    val id: String,
    val type: KnowledgeType,
    val content: String,
    val confidence: Float,
    val importance: Float,
    val categories: Set<KnowledgeCategory>,
    val tags: Set<String>,
    val extractedAt: Instant,
)

@Serializable
data class ConsolidationResult(
    val added: Int,
    val updated: Int,
    val merged: Int,
    val skipped: Int,
)
