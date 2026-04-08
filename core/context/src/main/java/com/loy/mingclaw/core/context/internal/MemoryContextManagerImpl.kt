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
                limit = 10,
                threshold = 0.5f,
            ).getOrElse { return@withContext Result.failure(it) }

            // MVP: 后续增强 - no memory expiry, importance decay, or dedup
            val result = mutableListOf<Memory>()
            var usedTokens = 0
            for (memory in candidates) {
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
}
