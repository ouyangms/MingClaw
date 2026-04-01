package com.loy.mingclaw.core.data.repository

import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    fun chatStream(request: ChatRequest): Flow<ChatStreamResult>

    suspend fun chat(request: ChatRequest): Result<String>
}
