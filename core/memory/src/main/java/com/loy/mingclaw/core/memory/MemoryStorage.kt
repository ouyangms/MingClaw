package com.loy.mingclaw.core.memory

import com.loy.mingclaw.core.model.memory.Memory
import com.loy.mingclaw.core.model.memory.MemoryStatistics
import com.loy.mingclaw.core.model.memory.MemoryType
import kotlinx.coroutines.flow.Flow

interface MemoryStorage {
    suspend fun add(memory: Memory): Result<Memory>
    suspend fun get(id: String): Result<Memory>
    suspend fun update(memory: Memory): Result<Memory>
    suspend fun delete(id: String): Result<Unit>
    suspend fun search(query: String, limit: Int = 20): Result<List<Memory>>
    suspend fun getAll(type: MemoryType? = null): Result<List<Memory>>
    fun observeAll(): Flow<List<Memory>>
    suspend fun getStatistics(): MemoryStatistics
    suspend fun cleanup(beforeTimestamp: Long): Result<Int>
}
