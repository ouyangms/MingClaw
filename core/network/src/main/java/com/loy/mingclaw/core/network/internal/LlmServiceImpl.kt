package com.loy.mingclaw.core.network.internal

import com.loy.mingclaw.core.network.LlmService
import com.loy.mingclaw.core.network.api.LlmApi
import com.loy.mingclaw.core.network.dto.ChatCompletionChunk
import com.loy.mingclaw.core.network.dto.ChatCompletionRequest
import com.loy.mingclaw.core.network.dto.ChatCompletionResponse
import com.loy.mingclaw.core.network.dto.ChatMessageDto
import com.loy.mingclaw.core.network.dto.EmbeddingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LlmServiceImpl @Inject constructor(
    private val llmApi: LlmApi,
    private val authInterceptor: AuthInterceptor,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val sseParser: SseParser,
) : LlmService {

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
        llmApi.chatCompletion(request)
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
        val httpRequest = Request.Builder()
            .url("${authInterceptor.baseUrl}v1/chat/completions")
            .post(json.encodeToString(ChatCompletionRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType()))
            .build()
        val response = okHttpClient.newCall(httpRequest).execute()
        sseParser.parseStream(response).flowOn(Dispatchers.IO)
    }

    override suspend fun generateEmbedding(
        model: String,
        texts: List<String>,
    ): Result<List<List<Float>>> = runCatching {
        val request = EmbeddingRequest(model = model, input = texts)
        val response = llmApi.createEmbedding(request)
        response.data.sortedBy { it.index }.map { it.embedding }
    }

    override fun setApiKey(apiKey: String) {
        authInterceptor.apiKey = apiKey
    }
}
