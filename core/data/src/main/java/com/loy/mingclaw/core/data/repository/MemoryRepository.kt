package com.loy.mingclaw.core.data.repository

import com.loy.mingclaw.core.model.memory.Memory
import com.loy.mingclaw.core.model.memory.MemoryStatistics
import com.loy.mingclaw.core.model.memory.MemoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface MemoryRepository {

    suspend fun save(memory: Memory): Result<Memory>

    suspend fun get(memoryId: String): Result<Memory>

    suspend fun update(memory: Memory): Result<Memory>

    suspend fun delete(memoryId: String): Result<Unit>

    fun observeAll(): Flow<List<Memory>>

    suspend fun search(query: String, limit: Int = 20): Result<List<Memory>>

    suspend fun vectorSearch(
        queryEmbedding: List<Float>,
        limit: Int = 10,
        threshold: Float = 0.0f,
    ): Result<List<Memory>>

    suspend fun getStatistics(): MemoryStatistics

    suspend fun cleanup(before: Instant): Result<Int>
}
