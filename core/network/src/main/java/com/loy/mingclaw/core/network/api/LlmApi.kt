package com.loy.mingclaw.core.network.api

import com.loy.mingclaw.core.network.dto.ChatCompletionChunk
import com.loy.mingclaw.core.network.dto.ChatCompletionRequest
import com.loy.mingclaw.core.network.dto.ChatCompletionResponse
import com.loy.mingclaw.core.network.dto.EmbeddingRequest
import com.loy.mingclaw.core.network.dto.EmbeddingResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface LlmApi {
    @POST("v1/chat/completions")
    suspend fun chatCompletion(
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse

    @POST("v1/embeddings")
    suspend fun createEmbedding(
        @Body request: EmbeddingRequest,
    ): EmbeddingResponse
}
