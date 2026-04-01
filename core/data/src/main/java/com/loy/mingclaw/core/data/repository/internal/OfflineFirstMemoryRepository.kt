package com.loy.mingclaw.core.data.repository.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.data.repository.MemoryRepository
import com.loy.mingclaw.core.model.memory.Memory
import com.loy.mingclaw.core.model.memory.MemoryStatistics
import com.loy.mingclaw.core.memory.EmbeddingService
import com.loy.mingclaw.core.memory.MemoryStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class OfflineFirstMemoryRepository @Inject constructor(
    private val memoryStorage: MemoryStorage,
    private val embeddingService: EmbeddingService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : MemoryRepository {

    override suspend fun save(memory: Memory): Result<Memory> = withContext(ioDispatcher) {
        memoryStorage.add(memory)
    }

    override suspend fun get(memoryId: String): Result<Memory> = withContext(ioDispatcher) {
        memoryStorage.get(memoryId)
    }

    override suspend fun update(memory: Memory): Result<Memory> = withContext(ioDispatcher) {
        memoryStorage.update(memory)
    }

    override suspend fun delete(memoryId: String): Result<Unit> = withContext(ioDispatcher) {
        memoryStorage.delete(memoryId)
    }

    override fun observeAll(): Flow<List<Memory>> = memoryStorage.observeAll()

    override suspend fun search(query: String, limit: Int): Result<List<Memory>> =
        withContext(ioDispatcher) {
            memoryStorage.search(query, limit)
        }

    override suspend fun vectorSearch(
        queryEmbedding: List<Float>,
        limit: Int,
        threshold: Float,
    ): Result<List<Memory>> = withContext(ioDispatcher) {
        runCatching {
            // MVP implementation: load all memories and filter in-memory by cosine similarity
            val allMemories = memoryStorage.getAll().getOrDefault(emptyList())
            allMemories
                .filter { it.embedding.isNotEmpty() }
                .map { memory ->
                    val similarity = embeddingService.similarity(queryEmbedding, memory.embedding)
                    memory to similarity
                }
                .filter { (_, similarity) -> similarity >= threshold }
                .sortedByDescending { (_, similarity) -> similarity }
                .take(limit)
                .map { (memory, _) -> memory }
        }
    }

    override suspend fun getStatistics(): MemoryStatistics = withContext(ioDispatcher) {
        memoryStorage.getStatistics()
    }

    override suspend fun cleanup(before: Instant): Result<Int> = withContext(ioDispatcher) {
        memoryStorage.cleanup(before.toEpochMilliseconds())
    }
}
