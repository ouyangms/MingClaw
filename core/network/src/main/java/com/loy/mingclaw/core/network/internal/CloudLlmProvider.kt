package com.loy.mingclaw.core.network.internal

import com.loy.mingclaw.core.model.llm.ChatChunk
import com.loy.mingclaw.core.model.llm.ChatMessage
import com.loy.mingclaw.core.model.llm.ChatResponse
import com.loy.mingclaw.core.model.llm.LlmProvider
import com.loy.mingclaw.core.network.LlmService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CloudLlmProvider @Inject constructor(
    private val llmService: LlmService,
) : LlmProvider {

    override suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int,
    ): Result<ChatResponse> {
        return llmService.chat(
            model = model,
            messages = messages.map { it.role to it.content },
            temperature = temperature,
            maxTokens = maxTokens,
        ).map { response ->
            ChatResponse(
                id = response.id,
                content = response.choices.firstOrNull()?.message?.content ?: "",
                model = model,
                promptTokens = response.usage?.prompt_tokens ?: 0,
                completionTokens = response.usage?.completion_tokens ?: 0,
                totalTokens = response.usage?.total_tokens ?: 0,
                finishReason = response.choices.firstOrNull()?.finish_reason,
            )
        }
    }

    override suspend fun chatStream(
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int,
    ): Result<Flow<ChatChunk>> {
        return llmService.chatStream(
            model = model,
            messages = messages.map { it.role to it.content },
            temperature = temperature,
            maxTokens = maxTokens,
        ).map { flow ->
            flow.map { chunk ->
                ChatChunk(
                    id = chunk.id,
                    delta = chunk.choices.firstOrNull()?.delta?.content ?: "",
                    finishReason = chunk.choices.firstOrNull()?.finish_reason,
                )
            }
        }
    }

    override suspend fun embed(
        model: String,
        texts: List<String>,
    ): Result<List<List<Float>>> {
        return llmService.generateEmbedding(model = model, texts = texts)
    }

    override fun providerName(): String = "cloud"
}
