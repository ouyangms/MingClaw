package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.ConsolidationResult
import com.loy.mingclaw.core.evolution.model.KnowledgePoint
import kotlinx.datetime.Instant

interface KnowledgeEvolver {
    suspend fun extractKnowledge(sessionId: String): Result<List<KnowledgePoint>>
    suspend fun consolidateToMemory(knowledge: List<KnowledgePoint>): Result<ConsolidationResult>
    suspend fun searchMemory(query: String): Result<List<KnowledgePoint>>
}
