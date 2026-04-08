package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.data.repository.MemoryRepository
import com.loy.mingclaw.core.memory.EmbeddingService
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

class MemoryContextManagerImplTest {
    private val memoryRepository = mockk<MemoryRepository>()
    private val embeddingService = mockk<EmbeddingService>()
    private val tokenEstimator = mockk<TokenEstimator>()
    private lateinit var manager: MemoryContextManagerImpl

    @Before
    fun setup() {
        manager = MemoryContextManagerImpl(
            memoryRepository = memoryRepository,
            embeddingService = embeddingService,
            tokenEstimator = tokenEstimator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `retrieveRelevantMemories returns memories within token budget`() = runTest {
        val now = Clock.System.now()
        val embedding = listOf(0.1f, 0.2f, 0.3f)
        val memories = listOf(
            Memory(id = "m1", content = "Memory one", importance = 0.8f, createdAt = now, accessedAt = now),
            Memory(id = "m2", content = "Memory two", importance = 0.7f, createdAt = now, accessedAt = now),
        )

        coEvery { embeddingService.generateEmbedding("test query") } returns Result.success(embedding)
        coEvery { memoryRepository.vectorSearch(embedding, limit = 10, threshold = 0.5f) } returns Result.success(memories)
        every { tokenEstimator.estimate("Memory one") } returns 3
        every { tokenEstimator.estimate("Memory two") } returns 3

        val result = manager.retrieveRelevantMemories("test query", maxTokens = 100)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `retrieveRelevantMemories truncates when exceeding budget`() = runTest {
        val now = Clock.System.now()
        val embedding = listOf(0.1f, 0.2f, 0.3f)
        val memories = listOf(
            Memory(id = "m1", content = "First memory that is somewhat long", importance = 0.8f, createdAt = now, accessedAt = now),
            Memory(id = "m2", content = "Second memory also quite long", importance = 0.7f, createdAt = now, accessedAt = now),
            Memory(id = "m3", content = "Third memory", importance = 0.6f, createdAt = now, accessedAt = now),
        )

        coEvery { embeddingService.generateEmbedding("query") } returns Result.success(embedding)
        coEvery { memoryRepository.vectorSearch(embedding, limit = 10, threshold = 0.5f) } returns Result.success(memories)
        every { tokenEstimator.estimate("First memory that is somewhat long") } returns 20
        every { tokenEstimator.estimate("Second memory also quite long") } returns 20
        every { tokenEstimator.estimate("Third memory") } returns 10

        val result = manager.retrieveRelevantMemories("query", maxTokens = 30)
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
    }

    @Test
    fun `retrieveRelevantMemories returns empty when embedding fails`() = runTest {
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.failure(RuntimeException("Embedding failed"))

        val result = manager.retrieveRelevantMemories("query", maxTokens = 100)
        assertTrue(result.isFailure)
    }

    @Test
    fun `retrieveRelevantMemories returns empty when vector search fails`() = runTest {
        val embedding = listOf(0.1f, 0.2f, 0.3f)
        coEvery { embeddingService.generateEmbedding("query") } returns Result.success(embedding)
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.failure(RuntimeException("Search failed"))

        val result = manager.retrieveRelevantMemories("query", maxTokens = 100)
        assertTrue(result.isFailure)
    }

    @Test
    fun `retrieveRelevantMemories returns empty list when no matches`() = runTest {
        val embedding = listOf(0.1f, 0.2f, 0.3f)
        coEvery { embeddingService.generateEmbedding("query") } returns Result.success(embedding)
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.success(emptyList())

        val result = manager.retrieveRelevantMemories("query", maxTokens = 100)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }
}
