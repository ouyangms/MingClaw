package com.loy.mingclaw.core.model.memory

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Memory(
    val id: String,
    val content: String,
    val type: MemoryType = MemoryType.ShortTerm,
    val importance: Float = 0.5f,
    val metadata: Map<String, String> = emptyMap(),
    val embedding: List<Float> = emptyList(),
    val createdAt: Instant,
    val accessedAt: Instant,
    val accessCount: Int = 0,
    val updatedAt: Instant? = null,
)

enum class MemoryType {
    ShortTerm, LongTerm, Episodic, Semantic, Procedural,
}

data class MemoryStatistics(
    val totalMemories: Int,
    val memoriesByType: Map<MemoryType, Int>,
    val averageImportance: Float,
    val totalTokens: Int,
)
