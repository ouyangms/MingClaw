package com.loy.mingclaw.core.data.repository.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.common.llm.CloudLlm
import com.loy.mingclaw.core.data.repository.ChatRepository
import com.loy.mingclaw.core.data.repository.ChatRequest
import com.loy.mingclaw.core.data.repository.ChatStreamResult
import com.loy.mingclaw.core.data.repository.SessionRepository
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.llm.ChatMessage
import com.loy.mingclaw.core.model.llm.LlmProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class OfflineFirstChatRepository @Inject constructor(
    private val sessionRepository: SessionRepository,
    @CloudLlm private val llmProvider: LlmProvider,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ChatRepository {

    override fun chatStream(request: ChatRequest): Flow<ChatStreamResult> = flow {
        // Persist user messages first
        for (msg in request.messages) {
            val userMessage = msg.toDomainMessage(request.sessionId)
            sessionRepository.addMessage(request.sessionId, userMessage)
        }

        // Stream from LLM
        val streamResult = withContext(ioDispatcher) {
            llmProvider.chatStream(
                model = request.model,
                messages = request.messages,
                temperature = request.temperature,
                maxTokens = request.maxTokens,
            )
        }

        if (streamResult.isFailure) {
            emit(ChatStreamResult.Error(streamResult.exceptionOrNull()?.message ?: "Unknown error"))
            return@flow
        }

        val chunks = StringBuilder()
        var finishReason: String? = null

        streamResult.getOrThrow().collect { chunk ->
            if (chunk.delta.isNotEmpty()) {
                chunks.append(chunk.delta)
                emit(ChatStreamResult.Chunk(content = chunk.delta, finishReason = chunk.finishReason))
            }
            if (chunk.finishReason != null) {
                finishReason = chunk.finishReason
            }
        }

        val fullContent = chunks.toString()

        // Persist assistant response
        val assistantMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = request.sessionId,
            role = MessageRole.Assistant,
            content = fullContent,
            timestamp = Clock.System.now(),
        )
        sessionRepository.addMessage(request.sessionId, assistantMessage)

        emit(ChatStreamResult.Complete(fullContent))
    }.flowOn(ioDispatcher)

    override suspend fun chat(request: ChatRequest): Result<String> = withContext(ioDispatcher) {
        // Persist user messages
        for (msg in request.messages) {
            val userMessage = msg.toDomainMessage(request.sessionId)
            sessionRepository.addMessage(request.sessionId, userMessage)
        }

        val result = llmProvider.chat(
            model = request.model,
            messages = request.messages,
            temperature = request.temperature,
            maxTokens = request.maxTokens,
        )

        if (result.isFailure) {
            return@withContext Result.failure(result.exceptionOrNull() ?: RuntimeException("Unknown error"))
        }

        val response = result.getOrThrow()

        // Persist assistant response
        val assistantMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = request.sessionId,
            role = MessageRole.Assistant,
            content = response.content,
            timestamp = Clock.System.now(),
        )
        sessionRepository.addMessage(request.sessionId, assistantMessage)

        Result.success(response.content)
    }

    private fun ChatMessage.toDomainMessage(sessionId: String): Message = Message(
        id = UUID.randomUUID().toString(),
        sessionId = sessionId,
        role = when (role.lowercase()) {
            "user" -> MessageRole.User
            "assistant" -> MessageRole.Assistant
            "system" -> MessageRole.System
            "tool" -> MessageRole.Tool
            else -> MessageRole.User
        },
        content = content,
        timestamp = Clock.System.now(),
    )
}
