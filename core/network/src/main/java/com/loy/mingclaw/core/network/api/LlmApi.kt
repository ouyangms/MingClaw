package com.loy.mingclaw.core.network.api

import com.loy.mingclaw.core.network.dto.ChatCompletionChunk
import com.loy.mingclaw.core.network.dto.ChatCompletionRequest
import com.loy.mingclaw.core.network.dto.ChatCompletionResponse
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
}
