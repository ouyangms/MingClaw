package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class SkillLevel { NONE, BASIC, INTERMEDIATE, ADVANCED, EXPERT }

@Serializable
enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
data class CapabilityGap(
    val id: String,
    val capability: String,
    val currentLevel: SkillLevel,
    val desiredLevel: SkillLevel,
    val priority: Priority,
    val detectedAt: Instant,
)
