package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.llm.ChatResponse
import com.loy.mingclaw.core.model.llm.LlmProvider
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

class ContextCompressionManagerImplTest {
    private val llmProvider = mockk<LlmProvider>()
    private val tokenEstimator = mockk<TokenEstimator>()
    private lateinit var manager: ContextCompressionManagerImpl

    @Before
    fun setup() {
        manager = ContextCompressionManagerImpl(
            llmProvider = llmProvider,
            tokenEstimator = tokenEstimator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun makeMessage(id: String, role: MessageRole, content: String): Message =
        Message(id = id, sessionId = "s1", role = role, content = content, timestamp = Clock.System.now())

    @Test
    fun `compressHistory returns messages unchanged when count is at most 6`() = runTest {
        val messages = (1..5).map { makeMessage("$it", MessageRole.User, "Msg $it") }

        val result = manager.compressHistory(messages, maxTokens = 4000)
        assertTrue(result.isSuccess)
        val compressed = result.getOrThrow()
        assertEquals("", compressed.summary)
        assertEquals(0, compressed.summaryTokenCount)
        assertEquals(5, compressed.retainedMessages.size)
    }

    @Test
    fun `compressHistory summarizes old messages and retains recent`() = runTest {
        val messages = (1..10).map { makeMessage("$it", MessageRole.User, "Message number $it") }

        coEvery {
            llmProvider.chat(model = any(), messages = any(), temperature = any(), maxTokens = any())
        } returns Result.success(
            ChatResponse(id = "r1", content = "Summary of conversation", model = "qwen-plus")
        )
        every { tokenEstimator.estimate("Summary of conversation") } returns 5

        val result = manager.compressHistory(messages, maxTokens = 4000)
        assertTrue(result.isSuccess)
        val compressed = result.getOrThrow()
        assertEquals("Summary of conversation", compressed.summary)
        assertEquals(5, compressed.summaryTokenCount)
        assertEquals(6, compressed.retainedMessages.size)
        assertEquals("5", compressed.retainedMessages.first().id)
        assertEquals("10", compressed.retainedMessages.last().id)
    }

    @Test
    fun `compressHistory returns failure when LLM fails`() = runTest {
        val messages = (1..10).map { makeMessage("$it", MessageRole.User, "Message $it") }

        coEvery {
            llmProvider.chat(model = any(), messages = any(), temperature = any(), maxTokens = any())
        } returns Result.failure(RuntimeException("LLM error"))

        val result = manager.compressHistory(messages, maxTokens = 4000)
        assertTrue(result.isFailure)
    }

    @Test
    fun `compressHistory with exactly 6 messages returns no compression`() = runTest {
        val messages = (1..6).map { makeMessage("$it", MessageRole.User, "Msg $it") }

        val result = manager.compressHistory(messages, maxTokens = 4000)
        assertTrue(result.isSuccess)
        assertEquals("", result.getOrThrow().summary)
        assertEquals(6, result.getOrThrow().retainedMessages.size)
    }

    @Test
    fun `compressHistory with 7 messages summarizes 1 old message`() = runTest {
        val messages = (1..7).map { makeMessage("$it", MessageRole.User, "Msg $it") }

        coEvery {
            llmProvider.chat(model = any(), messages = any(), temperature = any(), maxTokens = any())
        } returns Result.success(
            ChatResponse(id = "r1", content = "Brief summary", model = "qwen-plus")
        )
        every { tokenEstimator.estimate("Brief summary") } returns 3

        val result = manager.compressHistory(messages, maxTokens = 4000)
        assertTrue(result.isSuccess)
        assertEquals("Brief summary", result.getOrThrow().summary)
        assertEquals(6, result.getOrThrow().retainedMessages.size)
    }
}
