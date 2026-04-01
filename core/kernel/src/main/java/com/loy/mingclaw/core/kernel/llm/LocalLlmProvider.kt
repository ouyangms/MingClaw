package com.loy.mingclaw.core.kernel.llm

import com.loy.mingclaw.core.model.llm.ChatChunk
import com.loy.mingclaw.core.model.llm.ChatMessage
import com.loy.mingclaw.core.model.llm.ChatResponse
import com.loy.mingclaw.core.model.llm.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地 LLM 提供者空实现。
 * 未来可接入端侧模型（如通过 MLC-LLM、llama.cpp 等）。
 * 当前所有方法返回 UnsupportedOperationException。
 */
@Singleton
internal class LocalLlmProvider @Inject constructor() : LlmProvider {

    override suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int,
    ): Result<ChatResponse> = Result.failure(
        UnsupportedOperationException("Local LLM is not yet implemented")
    )

    override suspend fun chatStream(
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int,
    ): Result<Flow<ChatChunk>> = Result.failure(
        UnsupportedOperationException("Local LLM streaming is not yet implemented")
    )

    override suspend fun embed(
        model: String,
        texts: List<String>,
    ): Result<List<List<Float>>> = Result.failure(
        UnsupportedOperationException("Local embedding is not yet implemented")
    )

    override fun providerName(): String = "local"
}
