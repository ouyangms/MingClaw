package com.loy.mingclaw.core.memory

import com.loy.mingclaw.core.model.memory.Memory
import com.loy.mingclaw.core.model.memory.MemoryStatistics
import com.loy.mingclaw.core.model.memory.MemoryType
import kotlinx.coroutines.flow.Flow

interface MemoryManager {
    suspend fun addMemory(content: String, type: MemoryType, importance: Float): Result<Memory>
    suspend fun getMemory(id: String): Result<Memory>
    suspend fun deleteMemory(id: String): Result<Unit>
    suspend fun searchMemories(query: String, limit: Int = 10): Result<List<Memory>>
    suspend fun getMemoriesByType(type: MemoryType): Result<List<Memory>>
    fun observeAllMemories(): Flow<List<Memory>>
    suspend fun getStatistics(): MemoryStatistics
    suspend fun cleanup(beforeTimestamp: Long): Result<Int>
}
