package com.loy.mingclaw.core.network.internal

import com.loy.mingclaw.core.network.LlmService
import com.loy.mingclaw.core.network.api.LlmApi
import com.loy.mingclaw.core.network.dto.ChatCompletionChunk
import com.loy.mingclaw.core.network.dto.ChatCompletionRequest
import com.loy.mingclaw.core.network.dto.ChatCompletionResponse
import com.loy.mingclaw.core.network.dto.ChatMessageDto
import com.loy.mingclaw.core.network.dto.EmbeddingRequest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LlmServiceImpl @Inject constructor(
    private val llmApi: LlmApi,
) : LlmService {

    @Volatile
    private var apiKey: String = ""

    @Volatile
    private var baseUrl: String = ""

    override suspend fun chat(
        model: String,
        messages: List<Pair<String, String>>,
        temperature: Double,
        maxTokens: Int,
    ): Result<ChatCompletionResponse> = runCatching {
        val request = ChatCompletionRequest(
            model = model,
            messages = messages.map { ChatMessageDto(role = it.first, content = it.second) },
            temperature = temperature,
            max_tokens = maxTokens,
            stream = false,
        )
        llmApi.chatCompletion("Bearer $apiKey", request)
    }

    override suspend fun chatStream(
        model: String,
        messages: List<Pair<String, String>>,
        temperature: Double,
        maxTokens: Int,
    ): Result<Flow<ChatCompletionChunk>> = runCatching {
        val request = ChatCompletionRequest(
            model = model,
            messages = messages.map { ChatMessageDto(role = it.first, content = it.second) },
            temperature = temperature,
            max_tokens = maxTokens,
            stream = true,
        )
        llmApi.chatCompletionStream("Bearer $apiKey", request)
    }

    override suspend fun generateEmbedding(
        model: String,
        texts: List<String>,
    ): Result<List<List<Float>>> = runCatching {
        val request = EmbeddingRequest(model = model, input = texts)
        val response = llmApi.createEmbedding("Bearer $apiKey", request)
        response.data.sortedBy { it.index }.map { it.embedding }
    }

    override fun setApiKey(apiKey: String) {
        this.apiKey = apiKey
    }

    override fun setBaseUrl(baseUrl: String) {
        this.baseUrl = baseUrl
    }
}
