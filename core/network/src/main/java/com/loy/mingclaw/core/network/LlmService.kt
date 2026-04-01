package com.loy.mingclaw.core.network

import com.loy.mingclaw.core.network.dto.ChatCompletionChunk
import com.loy.mingclaw.core.network.dto.ChatCompletionResponse
import kotlinx.coroutines.flow.Flow

interface LlmService {
    suspend fun chat(
        model: String,
        messages: List<Pair<String, String>>,
        temperature: Double = 0.7,
        maxTokens: Int = 4096,
    ): Result<ChatCompletionResponse>

    suspend fun chatStream(
        model: String,
        messages: List<Pair<String, String>>,
        temperature: Double = 0.7,
        maxTokens: Int = 4096,
    ): Result<Flow<ChatCompletionChunk>>

    fun setApiKey(apiKey: String)
    fun setBaseUrl(baseUrl: String)
}
