package com.loy.mingclaw.core.network.api

import com.loy.mingclaw.core.network.dto.ChatCompletionChunk
import com.loy.mingclaw.core.network.dto.ChatCompletionRequest
import com.loy.mingclaw.core.network.dto.ChatCompletionResponse
import com.loy.mingclaw.core.network.dto.EmbeddingRequest
import com.loy.mingclaw.core.network.dto.EmbeddingResponse
import kotlinx.coroutines.flow.Flow
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface LlmApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse

    @POST("v1/chat/completions")
    suspend fun chatCompletionStream(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest,
    ): Flow<ChatCompletionChunk>

    @POST("v1/embeddings")
    suspend fun createEmbedding(
        @Header("Authorization") authorization: String,
        @Body request: EmbeddingRequest,
    ): EmbeddingResponse
}
