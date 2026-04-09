package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.context.MemoryContextManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.data.repository.MemoryRepository
import com.loy.mingclaw.core.memory.EmbeddingService
import com.loy.mingclaw.core.model.memory.Memory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MemoryContextManagerImpl @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val embeddingService: EmbeddingService,
    private val tokenEstimator: TokenEstimator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : MemoryContextManager {

    override suspend fun retrieveRelevantMemories(
        query: String,
        maxTokens: Int,
    ): Result<List<Memory>> = withContext(ioDispatcher) {
        try {
            val embedding = embeddingService.generateEmbedding(query)
                .getOrElse { return@withContext Result.failure(it) }

            val candidates = memoryRepository.vectorSearch(
                queryEmbedding = embedding,
                limit = 20,
                threshold = 0.5f,
            ).getOrElse { return@withContext Result.failure(it) }

            val deduped = deduplicateByContent(candidates)

            val result = mutableListOf<Memory>()
            var usedTokens = 0
            for (memory in deduped) {
                val tokens = tokenEstimator.estimate(memory.content)
                if (usedTokens + tokens > maxTokens) break
                result.add(memory)
                usedTokens += tokens
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deduplicateByContent(memories: List<Memory>): List<Memory> {
        val result = mutableListOf<Memory>()
        for (memory in memories.sortedByDescending { it.importance }) {
            val isDuplicate = result.any { existing ->
                contentSimilarity(existing.content, memory.content) > 0.8
            }
            if (!isDuplicate) {
                result.add(memory)
            }
        }
        return result
    }

    private fun contentSimilarity(a: String, b: String): Float {
        if (a == b) return 1.0f
        val setA = a.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        val setB = b.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        if (setA.isEmpty() && setB.isEmpty()) return 1.0f
        if (setA.isEmpty() || setB.isEmpty()) return 0.0f
        val intersection = setA.intersect(setB).size
        val union = setA.union(setB).size
        return if (union > 0) intersection.toFloat() / union else 0.0f
    }
}
