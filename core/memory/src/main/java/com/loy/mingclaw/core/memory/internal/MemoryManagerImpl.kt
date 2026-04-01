package com.loy.mingclaw.core.memory.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.memory.Memory
import com.loy.mingclaw.core.model.memory.MemoryStatistics
import com.loy.mingclaw.core.model.memory.MemoryType
import com.loy.mingclaw.core.memory.EmbeddingService
import com.loy.mingclaw.core.memory.MemoryManager
import com.loy.mingclaw.core.memory.MemoryStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MemoryManagerImpl @Inject constructor(
    private val storage: MemoryStorage,
    private val embeddingService: EmbeddingService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : MemoryManager {

    override suspend fun addMemory(
        content: String,
        type: MemoryType,
        importance: Float,
    ): Result<Memory> = withContext(ioDispatcher) {
        runCatching {
            val now = Clock.System.now()
            val embedding = embeddingService.generateEmbedding(content).getOrDefault(emptyList())
            val memory = Memory(
                id = UUID.randomUUID().toString(),
                content = content,
                type = type,
                importance = importance,
                embedding = embedding,
                createdAt = now,
                accessedAt = now,
            )
            storage.add(memory).getOrThrow()
        }
    }

    override suspend fun getMemory(id: String): Result<Memory> = storage.get(id)

    override suspend fun deleteMemory(id: String): Result<Unit> = storage.delete(id)

    override suspend fun searchMemories(query: String, limit: Int): Result<List<Memory>> {
        // MVP: text-based search. Future: vector similarity search
        return storage.search(query, limit)
    }

    override suspend fun getMemoriesByType(type: MemoryType): Result<List<Memory>> =
        storage.getAll(type)

    override fun observeAllMemories(): Flow<List<Memory>> = storage.observeAll()

    override suspend fun getStatistics(): MemoryStatistics = storage.getStatistics()

    override suspend fun cleanup(beforeTimestamp: Long): Result<Int> = storage.cleanup(beforeTimestamp)
}
