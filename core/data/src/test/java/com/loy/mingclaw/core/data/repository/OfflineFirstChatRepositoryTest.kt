package com.loy.mingclaw.core.data.repository

import app.cash.turbine.test
import com.loy.mingclaw.core.data.repository.internal.OfflineFirstChatRepository
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.llm.ChatChunk
import com.loy.mingclaw.core.model.llm.ChatMessage
import com.loy.mingclaw.core.model.llm.ChatResponse
import com.loy.mingclaw.core.model.llm.LlmProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineFirstChatRepositoryTest {

    private val sessionRepository = mockk<SessionRepository>()
    private val llmProvider = mockk<LlmProvider>()
    private lateinit var chatRepository: OfflineFirstChatRepository

    @Before
    fun setup() {
        chatRepository = OfflineFirstChatRepository(
            sessionRepository = sessionRepository,
            llmProvider = llmProvider,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun chat_persistsMessagesAndReturnsContent() = runTest {
        // Stub: addMessage returns the message
        coEvery { sessionRepository.addMessage(any(), any()) } answers {
            secondArg<Message>()
        }

        coEvery {
            llmProvider.chat(
                model = "gpt-4",
                messages = any(),
                temperature = any(),
                maxTokens = any(),
            )
        } returns Result.success(
            ChatResponse(id = "r1", content = "Hello from AI", model = "gpt-4")
        )

        val request = ChatRequest(
            sessionId = "sess-1",
            model = "gpt-4",
            messages = listOf(ChatMessage(role = "user", content = "Hi")),
        )
        val result = chatRepository.chat(request)

        assertTrue(result.isSuccess)
        assertEquals("Hello from AI", result.getOrThrow())

        // Verify user message + assistant message were persisted
        coVerify(exactly = 2) { sessionRepository.addMessage(eq("sess-1"), any()) }
    }

    @Test
    fun chat_returnsFailureWhenLlmFails() = runTest {
        coEvery { sessionRepository.addMessage(any(), any()) } answers {
            secondArg<Message>()
        }
        coEvery {
            llmProvider.chat(model = any(), messages = any(), temperature = any(), maxTokens = any())
        } returns Result.failure(RuntimeException("API error"))

        val request = ChatRequest(
            sessionId = "sess-1",
            model = "gpt-4",
            messages = listOf(ChatMessage(role = "user", content = "Hi")),
        )
        val result = chatRepository.chat(request)

        assertTrue(result.isFailure)
    }

    @Test
    fun chatStream_persistsMessagesAndEmitsChunks() = runTest {
        coEvery { sessionRepository.addMessage(any(), any()) } answers {
            secondArg<Message>()
        }

        val chunks = flowOf(
            ChatChunk(id = "c1", delta = "Hello", finishReason = null),
            ChatChunk(id = "c1", delta = " World", finishReason = "stop"),
        )
        coEvery {
            llmProvider.chatStream(model = any(), messages = any(), temperature = any(), maxTokens = any())
        } returns Result.success(chunks)

        val request = ChatRequest(
            sessionId = "sess-1",
            model = "gpt-4",
            messages = listOf(ChatMessage(role = "user", content = "Hi")),
        )

        chatRepository.chatStream(request).test {
            // First chunk
            val chunk1 = awaitItem()
            assertTrue(chunk1 is ChatStreamResult.Chunk)
            assertEquals("Hello", (chunk1 as ChatStreamResult.Chunk).content)

            // Second chunk
            val chunk2 = awaitItem()
            assertTrue(chunk2 is ChatStreamResult.Chunk)
            assertEquals(" World", (chunk2 as ChatStreamResult.Chunk).content)

            // Complete
            val complete = awaitItem()
            assertTrue(complete is ChatStreamResult.Complete)
            assertEquals("Hello World", (complete as ChatStreamResult.Complete).fullContent)

            awaitComplete()
        }

        // Verify: 1 user message + 1 assistant message = 2 calls
        coVerify(exactly = 2) { sessionRepository.addMessage(eq("sess-1"), any()) }
    }

    @Test
    fun chatStream_emitsErrorWhenLlmFails() = runTest {
        coEvery { sessionRepository.addMessage(any(), any()) } answers {
            secondArg<Message>()
        }
        coEvery {
            llmProvider.chatStream(model = any(), messages = any(), temperature = any(), maxTokens = any())
        } returns Result.failure(RuntimeException("Stream error"))

        val request = ChatRequest(
            sessionId = "sess-1",
            model = "gpt-4",
            messages = listOf(ChatMessage(role = "user", content = "Hi")),
        )

        chatRepository.chatStream(request).test {
            val error = awaitItem()
            assertTrue(error is ChatStreamResult.Error)
            assertEquals("Stream error", (error as ChatStreamResult.Error).message)
            awaitComplete()
        }
    }
}
