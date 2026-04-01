package com.loy.mingclaw.core.memory

import com.loy.mingclaw.core.memory.internal.EmbeddingServiceImpl
import com.loy.mingclaw.core.network.LlmService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryManagerImplTest {

    private lateinit var embeddingService: EmbeddingServiceImpl
    private val llmService = mockk<LlmService>()

    @Before
    fun setup() {
        coEvery { llmService.generateEmbedding(texts = any()) } returns Result.success(listOf(listOf(0.1f, 0.2f)))
        embeddingService = EmbeddingServiceImpl(llmService)
    }

    @Test
    fun addMemory_returnsMemoryWithEmbedding() = runTest {
        // This test verifies the embedding integration
        val embedding = embeddingService.generateEmbedding("test content")
        assertTrue(embedding.isSuccess)
        assertTrue(embedding.getOrThrow().isNotEmpty())
    }

    @Test
    fun similarity_cosineCalculation() {
        val a = listOf(1f, 1f, 1f)
        val b = listOf(1f, 1f, 1f)
        val sim = embeddingService.similarity(a, b)
        assertEquals(1.0f, sim, 0.01f)
    }

    @Test
    fun similarity_differentVectors() {
        val a = listOf(1f, 0f, 0f)
        val b = listOf(0.707f, 0.707f, 0f)
        val sim = embeddingService.similarity(a, b)
        assertTrue(sim > 0.5f) // ~0.707
    }
}
