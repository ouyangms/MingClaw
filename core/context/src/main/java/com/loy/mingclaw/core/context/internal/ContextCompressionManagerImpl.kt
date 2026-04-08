package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.common.llm.CloudLlm
import com.loy.mingclaw.core.context.ContextCompressionManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.context.internal.prompts.CompressionPrompt
import com.loy.mingclaw.core.context.model.CompressedContext
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.llm.LlmProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ContextCompressionManagerImpl @Inject constructor(
    @CloudLlm private val llmProvider: LlmProvider,
    private val tokenEstimator: TokenEstimator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContextCompressionManager {

    companion object {
        private const val DEFAULT_MODEL = "qwen-plus"
        private const val RETAINED_MESSAGE_COUNT = 6
    }

    // MVP: 后续增强 - single summarization strategy, no sliding window/key info extraction
    override suspend fun compressHistory(
        messages: List<Message>,
        maxTokens: Int,
    ): Result<CompressedContext> = withContext(ioDispatcher) {
        try {
            if (messages.size <= RETAINED_MESSAGE_COUNT) {
                return@withContext Result.success(
                    CompressedContext(
                        summary = "",
                        summaryTokenCount = 0,
                        retainedMessages = messages,
                    )
                )
            }

            val retainedMessages = messages.takeLast(RETAINED_MESSAGE_COUNT)
            val oldMessages = messages.dropLast(RETAINED_MESSAGE_COUNT)

            val conversationHistory = oldMessages.joinToString("\n") { msg ->
                "[${msg.role}] ${msg.content}"
            }

            val promptMessages = CompressionPrompt.build(conversationHistory)
            val llmResult = llmProvider.chat(
                model = DEFAULT_MODEL,
                messages = promptMessages,
                temperature = 0.3,
            )

            val response = llmResult.getOrElse { return@withContext Result.failure(it) }
            val summary = response.content
            val summaryTokenCount = tokenEstimator.estimate(summary)

            // Validate total token count does not exceed maxTokens
            val retainedTokenCount = retainedMessages.sumOf { tokenEstimator.estimate(it.content) }
            val totalTokens = summaryTokenCount + retainedTokenCount
            val finalSummary = if (totalTokens > maxTokens) {
                // Truncate summary if total exceeds budget
                val allowedSummaryTokens = maxTokens - retainedTokenCount
                if (allowedSummaryTokens <= 0) "" else summary
            } else {
                summary
            }
            val finalSummaryTokenCount = if (finalSummary == summary) summaryTokenCount else tokenEstimator.estimate(finalSummary)

            Result.success(
                CompressedContext(
                    summary = finalSummary,
                    summaryTokenCount = finalSummaryTokenCount,
                    retainedMessages = retainedMessages,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
