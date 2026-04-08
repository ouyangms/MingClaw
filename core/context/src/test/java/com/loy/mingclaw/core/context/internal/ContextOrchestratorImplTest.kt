package com.loy.mingclaw.core.context.internal

import app.cash.turbine.test
import com.loy.mingclaw.core.context.ContextCompressionManager
import com.loy.mingclaw.core.context.ContextWindowManager
import com.loy.mingclaw.core.context.MemoryContextManager
import com.loy.mingclaw.core.context.SessionContextManager
import com.loy.mingclaw.core.context.model.CompressedContext
import com.loy.mingclaw.core.model.TokenBudget
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.memory.Memory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextOrchestratorImplTest {
    private val sessionContextManager = mockk<SessionContextManager>()
    private val memoryContextManager = mockk<MemoryContextManager>()
    private val contextWindowManager = mockk<ContextWindowManager>()
    private val compressionManager = mockk<ContextCompressionManager>()
    private lateinit var orchestrator: ContextOrchestratorImpl

    @Before
    fun setup() {
        orchestrator = ContextOrchestratorImpl(
            sessionContextManager = sessionContextManager,
            memoryContextManager = memoryContextManager,
            contextWindowManager = contextWindowManager,
            compressionManager = compressionManager,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun defaultSetup(
        messages: List<Message> = emptyList(),
        memories: List<Memory> = emptyList(),
        shouldCompress: Boolean = false,
        budget: TokenBudget = TokenBudget.calculate(8192),
    ) {
        coEvery { sessionContextManager.getConversationHistory(any(), any()) } returns Result.success(messages)
        every { contextWindowManager.calculateTokenBudget() } returns budget
        coEvery { memoryContextManager.retrieveRelevantMemories(any(), any()) } returns Result.success(memories)
        every { contextWindowManager.shouldCompress(any(), any()) } returns shouldCompress
        every { contextWindowManager.estimateTokens(any()) } answers {
            (firstArg<String>().length + 3) / 4
        }
    }

    @Test
    fun `buildContext with no history and no memories returns basic context`() = runTest {
        defaultSetup()

        val result = orchestrator.buildContext("s1", "Hello")
        assertTrue(result.isSuccess)
        val context = result.getOrThrow()
        assertEquals("system", context.messages.first().role)
        assertEquals("Hello", context.messages.last().content)
        assertEquals(0, context.memories.size)
    }

    @Test
    fun `buildContext injects memories into system prompt`() = runTest {
        val now = Clock.System.now()
        val memories = listOf(
            Memory(id = "m1", content = "User likes Kotlin", importance = 0.8f, createdAt = now, accessedAt = now),
        )
        defaultSetup(memories = memories)

        val result = orchestrator.buildContext("s1", "What language?")
        assertTrue(result.isSuccess)
        val context = result.getOrThrow()
        assertTrue(context.systemPrompt.contains("Kotlin"))
        assertTrue(context.systemPrompt.contains("相关记忆"))
        assertEquals(1, context.memories.size)
    }

    @Test
    fun `buildContext includes conversation history`() = runTest {
        val now = Clock.System.now()
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "Previous question", timestamp = now),
            Message(id = "2", sessionId = "s1", role = MessageRole.Assistant, content = "Previous answer", timestamp = now),
        )
        defaultSetup(messages = messages)

        val result = orchestrator.buildContext("s1", "New question")
        assertTrue(result.isSuccess)
        val context = result.getOrThrow()
        assertEquals(4, context.messages.size)
    }

    @Test
    fun `buildContext triggers compression when needed`() = runTest {
        val messages = (1..20).map { i ->
            Message(id = "$i", sessionId = "s1", role = MessageRole.User, content = "Message $i with padding text", timestamp = Clock.System.now())
        }
        val compressed = CompressedContext(
            summary = "Summary of old messages",
            summaryTokenCount = 5,
            retainedMessages = messages.takeLast(6),
        )

        defaultSetup(messages = messages, shouldCompress = true)
        coEvery { compressionManager.compressHistory(any(), any()) } returns Result.success(compressed)

        val result = orchestrator.buildContext("s1", "New message")
        assertTrue(result.isSuccess)
        val context = result.getOrThrow()
        assertEquals(8, context.messages.size)
        // Verify summary is injected into system prompt
        assertTrue(context.systemPrompt.contains("对话历史摘要"))
        assertTrue(context.systemPrompt.contains("Summary of old messages"))
    }

    @Test
    fun `buildContext degrades gracefully when memory retrieval fails`() = runTest {
        defaultSetup()
        coEvery { memoryContextManager.retrieveRelevantMemories(any(), any()) } returns Result.failure(RuntimeException("Embedding failed"))

        val result = orchestrator.buildContext("s1", "Hello")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().memories.size)
    }

    @Test
    fun `buildContext degrades gracefully when compression fails`() = runTest {
        val messages = (1..20).map { i ->
            Message(id = "$i", sessionId = "s1", role = MessageRole.User, content = "Message $i", timestamp = Clock.System.now())
        }
        defaultSetup(messages = messages, shouldCompress = true)
        coEvery { compressionManager.compressHistory(any(), any()) } returns Result.failure(RuntimeException("LLM error"))

        val result = orchestrator.buildContext("s1", "New")
        assertTrue(result.isSuccess)
        assertEquals(22, result.getOrThrow().messages.size)
    }

    @Test
    fun `buildContext returns failure when history retrieval fails`() = runTest {
        coEvery { sessionContextManager.getConversationHistory(any(), any()) } returns Result.failure(RuntimeException("DB error"))

        val result = orchestrator.buildContext("s1", "Hello")
        assertTrue(result.isFailure)
    }

    @Test
    fun `observeContextStats emits updated stats after buildContext`() = runTest {
        defaultSetup()

        orchestrator.observeContextStats().test {
            val initial = awaitItem()
            assertEquals(0, initial.totalTokensUsed)

            orchestrator.buildContext("s1", "Hello")
            val updated = awaitItem()
            assertEquals("s1", updated.sessionId)
            assertTrue(updated.totalTokensUsed > 0)
            assertTrue(updated.budgetUtilization > 0f)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
