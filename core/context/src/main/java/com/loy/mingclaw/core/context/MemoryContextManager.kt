package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.memory.Memory

interface MemoryContextManager {
    suspend fun retrieveRelevantMemories(query: String, maxTokens: Int): Result<List<Memory>>
}
