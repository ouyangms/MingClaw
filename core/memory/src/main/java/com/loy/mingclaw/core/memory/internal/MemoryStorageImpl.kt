package com.loy.mingclaw.core.memory.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.database.dao.EmbeddingDao
import com.loy.mingclaw.core.database.dao.MemoryDao
import com.loy.mingclaw.core.database.entity.EmbeddingEntity
import com.loy.mingclaw.core.database.entity.MemoryEntity
import com.loy.mingclaw.core.model.memory.Memory
import com.loy.mingclaw.core.model.memory.MemoryStatistics
import com.loy.mingclaw.core.model.memory.MemoryType
import com.loy.mingclaw.core.memory.MemoryStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MemoryStorageImpl @Inject constructor(
    private val memoryDao: MemoryDao,
    private val embeddingDao: EmbeddingDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : MemoryStorage {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun add(memory: Memory): Result<Memory> = withContext(ioDispatcher) {
        runCatching {
            memoryDao.insert(memory.toEntity())
            if (memory.embedding.isNotEmpty()) {
                embeddingDao.insert(EmbeddingEntity(
                    id = memory.id,
                    embedding = json.encodeToString(ListSerializer(Float.serializer()), memory.embedding),
                    dimension = memory.embedding.size,
                ))
            }
            memory
        }
    }

    override suspend fun get(id: String): Result<Memory> = withContext(ioDispatcher) {
        runCatching {
            val entity = memoryDao.getById(id) ?: throw NoSuchElementException("Memory not found: $id")
            val embeddingEntity = embeddingDao.getById(id)
            entity.toDomain(embeddingEntity?.embedding)
        }
    }

    override suspend fun update(memory: Memory): Result<Memory> = withContext(ioDispatcher) {
        runCatching {
            memoryDao.update(memory.toEntity())
            if (memory.embedding.isNotEmpty()) {
                embeddingDao.insert(EmbeddingEntity(
                    id = memory.id,
                    embedding = json.encodeToString(ListSerializer(Float.serializer()), memory.embedding),
                    dimension = memory.embedding.size,
                ))
            }
            memory
        }
    }

    override suspend fun delete(id: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            memoryDao.delete(id)
            embeddingDao.delete(id)
        }
    }

    override suspend fun search(query: String, limit: Int): Result<List<Memory>> = withContext(ioDispatcher) {
        runCatching {
            memoryDao.search(query, limit).map { it.toDomain(null) }
        }
    }

    override suspend fun getAll(type: MemoryType?): Result<List<Memory>> = withContext(ioDispatcher) {
        runCatching {
            val entities = if (type != null) {
                memoryDao.getByType(type.name)
            } else {
                memoryDao.getAll()
            }
            entities.map { it.toDomain(null) }
        }
    }

    override fun observeAll(): Flow<List<Memory>> =
        memoryDao.observeAll().map { entities -> entities.map { it.toDomain(null) } }

    override suspend fun getStatistics(): MemoryStatistics = withContext(ioDispatcher) {
        val total = memoryDao.count()
        val byType = MemoryType.values().associateWith { type ->
            memoryDao.countByType(type.name)
        }
        val avgImportance = memoryDao.getAverageImportance() ?: 0f
        MemoryStatistics(
            totalMemories = total,
            memoriesByType = byType,
            averageImportance = avgImportance,
            totalTokens = 0, // simplified
        )
    }

    override suspend fun cleanup(beforeTimestamp: Long): Result<Int> = withContext(ioDispatcher) {
        runCatching { memoryDao.deleteBefore(beforeTimestamp) }
    }

    private fun Memory.toEntity(): MemoryEntity = MemoryEntity(
        id = id,
        content = content,
        type = type.name,
        importance = importance,
        metadata = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), metadata),
        createdAt = createdAt.toEpochMilliseconds(),
        accessedAt = accessedAt.toEpochMilliseconds(),
        accessCount = accessCount,
        updatedAt = updatedAt?.toEpochMilliseconds(),
    )

    private fun MemoryEntity.toDomain(embeddingJson: String?): Memory {
        val embeddingList: List<Float> = embeddingJson?.let {
            try { json.decodeFromString<List<Float>>(it) } catch (_: Exception) { emptyList() }
        } ?: emptyList()
        return Memory(
            id = id,
            content = content,
            type = MemoryType.values().find { it.name == type } ?: MemoryType.ShortTerm,
            importance = importance,
            metadata = try { json.decodeFromString<Map<String, String>>(metadata) } catch (_: Exception) { emptyMap() },
            embedding = embeddingList,
            createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(createdAt),
            accessedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(accessedAt),
            accessCount = accessCount,
            updatedAt = updatedAt?.let { kotlinx.datetime.Instant.fromEpochMilliseconds(it) },
        )
    }
}
