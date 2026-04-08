package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.context.ContextCompressionManager
import com.loy.mingclaw.core.context.ContextOrchestrator
import com.loy.mingclaw.core.context.ContextWindowManager
import com.loy.mingclaw.core.context.MemoryContextManager
import com.loy.mingclaw.core.context.SessionContextManager
import com.loy.mingclaw.core.context.model.CompressedContext
import com.loy.mingclaw.core.context.model.ContextStats
import com.loy.mingclaw.core.context.model.ConversationContext
import com.loy.mingclaw.core.context.model.TokenUsage
import com.loy.mingclaw.core.model.llm.ChatMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ContextOrchestratorImpl @Inject constructor(
    private val sessionContextManager: SessionContextManager,
    private val memoryContextManager: MemoryContextManager,
    private val contextWindowManager: ContextWindowManager,
    private val compressionManager: ContextCompressionManager,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContextOrchestrator {

    private val contextStats = MutableStateFlow(
        ContextStats(sessionId = "", totalTokensUsed = 0, compressionCount = 0, memoriesInjected = 0, budgetUtilization = 0f)
    )

    // MVP: 后续增强 - system prompt hardcoded, future DynamicPromptBuilder from Evolution layer
    override suspend fun buildContext(
        sessionId: String,
        userMessage: String,
    ): Result<ConversationContext> = withContext(ioDispatcher) {
        try {
            // Step 1: Get conversation history
            val historyResult = sessionContextManager.getConversationHistory(sessionId)
            val messages = historyResult.getOrElse { return@withContext Result.failure(it) }

            // Step 2: Calculate token budget
            val budget = contextWindowManager.calculateTokenBudget()

            // Step 3: Retrieve relevant memories
            val memoriesResult = memoryContextManager.retrieveRelevantMemories(userMessage, budget.memoryTokens)
            val memories = memoriesResult.getOrElse { emptyList() }

            // Step 4: Compress if needed
            var conversationMessages = messages
            var compressionCount = 0
            var summaryText = ""
            if (contextWindowManager.shouldCompress(messages, budget)) {
                val compressed = compressionManager.compressHistory(messages, budget.conversationTokens)
                compressed.getOrElse {
                    CompressedContext(summary = "", summaryTokenCount = 0, retainedMessages = messages)
                }.also { ctx ->
                    compressionCount = if (ctx.summary.isNotEmpty()) 1 else 0
                    summaryText = ctx.summary
                    conversationMessages = ctx.retainedMessages
                }
            }

            // Step 5: Build system prompt with memories and optional compression summary
            val memorySection = if (memories.isNotEmpty()) {
                val memoryText = memories.joinToString("\n") { "- ${it.content}" }
                "\n\n## 相关记忆\n$memoryText"
            } else {
                ""
            }
            val summarySection = if (summaryText.isNotEmpty()) {
                "\n\n## 对话历史摘要\n$summaryText"
            } else {
                ""
            }
            val systemPrompt = "你是一个智能助手，帮助用户完成各种任务。$memorySection$summarySection"

            // Step 6: Convert to ChatMessage list
            val chatMessages = mutableListOf<ChatMessage>()
            chatMessages.add(ChatMessage(role = "system", content = systemPrompt))
            for (msg in conversationMessages) {
                chatMessages.add(ChatMessage(role = msg.role.name.lowercase(), content = msg.content))
            }
            chatMessages.add(ChatMessage(role = "user", content = userMessage))

            // Calculate token usage
            val systemTokens = contextWindowManager.estimateTokens(systemPrompt)
            val memoryTokens = memories.sumOf { contextWindowManager.estimateTokens(it.content) }
            val conversationTokens = chatMessages.sumOf { contextWindowManager.estimateTokens(it.content) } - systemTokens
            val totalTokens = systemTokens + memoryTokens + conversationTokens

            val tokenUsage = TokenUsage(
                systemTokens = systemTokens,
                memoryTokens = memoryTokens,
                conversationTokens = conversationTokens,
                totalTokens = totalTokens,
                budget = budget,
            )

            val result = ConversationContext(
                systemPrompt = systemPrompt,
                messages = chatMessages,
                tokenUsage = tokenUsage,
                memories = memories,
            )

            // Update stats
            contextStats.value = ContextStats(
                sessionId = sessionId,
                totalTokensUsed = totalTokens,
                compressionCount = compressionCount,
                memoriesInjected = memories.size,
                budgetUtilization = if (budget.totalTokens > 0) totalTokens.toFloat() / budget.totalTokens else 0f,
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeContextStats(): Flow<ContextStats> = contextStats.asStateFlow()
}
