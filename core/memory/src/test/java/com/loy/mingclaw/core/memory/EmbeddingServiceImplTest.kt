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

class EmbeddingServiceImplTest {

    private lateinit var llmService: LlmService
    private lateinit var embeddingService: EmbeddingServiceImpl

    @Before
    fun setup() {
        llmService = mockk()
        embeddingService = EmbeddingServiceImpl(llmService)
    }

    @Test
    fun generateEmbedding_success() = runTest {
        val embedding = listOf(0.1f, 0.2f, 0.3f)
        coEvery { llmService.generateEmbedding(texts = listOf("hello")) } returns Result.success(listOf(embedding))
        val result = embeddingService.generateEmbedding("hello")
        assertTrue(result.isSuccess)
        assertEquals(embedding, result.getOrThrow())
    }

    @Test
    fun generateEmbeddings_multipleTexts() = runTest {
        val embeddings = listOf(listOf(0.1f, 0.2f), listOf(0.3f, 0.4f))
        coEvery { llmService.generateEmbedding(texts = listOf("a", "b")) } returns Result.success(embeddings)
        val result = embeddingService.generateEmbeddings(listOf("a", "b"))
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun similarity_identicalVectors() {
        val v = listOf(1f, 0f, 0f)
        val sim = embeddingService.similarity(v, v)
        assertEquals(1.0f, sim, 0.01f)
    }

    @Test
    fun similarity_orthogonalVectors() {
        val a = listOf(1f, 0f)
        val b = listOf(0f, 1f)
        val sim = embeddingService.similarity(a, b)
        assertEquals(0.0f, sim, 0.01f)
    }

    @Test
    fun similarity_emptyVectors() {
        val sim = embeddingService.similarity(emptyList(), emptyList())
        assertEquals(0.0f, sim, 0.01f)
    }
}
