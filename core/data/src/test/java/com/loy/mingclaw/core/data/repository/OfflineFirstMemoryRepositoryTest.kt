package com.loy.mingclaw.core.data.repository

import com.loy.mingclaw.core.data.repository.internal.OfflineFirstMemoryRepository
import com.loy.mingclaw.core.model.memory.Memory
import com.loy.mingclaw.core.model.memory.MemoryStatistics
import com.loy.mingclaw.core.model.memory.MemoryType
import com.loy.mingclaw.core.memory.EmbeddingService
import com.loy.mingclaw.core.memory.MemoryStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineFirstMemoryRepositoryTest {

    private val memoryStorage = mockk<MemoryStorage>()
    private val embeddingService = mockk<EmbeddingService>()
    private lateinit var repository: OfflineFirstMemoryRepository

    private val testInstant = Instant.fromEpochMilliseconds(1700000000000)

    private val testMemory = Memory(
        id = "mem-1",
        content = "test content",
        type = MemoryType.ShortTerm,
        importance = 0.7f,
        metadata = emptyMap(),
        embedding = listOf(0.1f, 0.2f, 0.3f),
        createdAt = testInstant,
        accessedAt = testInstant,
        accessCount = 1,
        updatedAt = null,
    )

    @Before
    fun setup() {
        repository = OfflineFirstMemoryRepository(
            memoryStorage = memoryStorage,
            embeddingService = embeddingService,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun save_delegatesToStorage() = runTest {
        coEvery { memoryStorage.add(testMemory) } returns Result.success(testMemory)

        val result = repository.save(testMemory)
        assertTrue(result.isSuccess)
        assertEquals("mem-1", result.getOrThrow().id)
    }

    @Test
    fun get_delegatesToStorage() = runTest {
        coEvery { memoryStorage.get("mem-1") } returns Result.success(testMemory)

        val result = repository.get("mem-1")
        assertTrue(result.isSuccess)
        assertEquals("mem-1", result.getOrThrow().id)
    }

    @Test
    fun update_delegatesToStorage() = runTest {
        val updated = testMemory.copy(content = "updated")
        coEvery { memoryStorage.update(updated) } returns Result.success(updated)

        val result = repository.update(updated)
        assertTrue(result.isSuccess)
        assertEquals("updated", result.getOrThrow().content)
    }

    @Test
    fun delete_delegatesToStorage() = runTest {
        coEvery { memoryStorage.delete("mem-1") } returns Result.success(Unit)

        val result = repository.delete("mem-1")
        assertTrue(result.isSuccess)
    }

    @Test
    fun search_delegatesToStorage() = runTest {
        coEvery { memoryStorage.search("test", 20) } returns Result.success(listOf(testMemory))

        val result = repository.search("test")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
    }

    @Test
    fun vectorSearch_filtersBySimilarity() = runTest {
        val memory2 = testMemory.copy(
            id = "mem-2",
            embedding = listOf(0.9f, 0.8f, 0.7f),
        )
        coEvery { memoryStorage.getAll() } returns Result.success(listOf(testMemory, memory2))
        every { embeddingService.similarity(any(), any()) } answers {
            val a = firstArg<List<Float>>()
            val b = secondArg<List<Float>>()
            // Simple dot product for test
            a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }.toFloat()
        }

        val queryEmbedding = listOf(1.0f, 0.0f, 0.0f)
        val result = repository.vectorSearch(queryEmbedding, limit = 10, threshold = 0.0f)
        assertTrue(result.isSuccess)
        val memories = result.getOrThrow()
        // mem-2 should rank higher: dot(1,0.9) = 0.9 > dot(1,0.1) = 0.1
        assertEquals(2, memories.size)
        assertEquals("mem-2", memories[0].id)
    }

    @Test
    fun vectorSearch_thresholdFilters() = runTest {
        coEvery { memoryStorage.getAll() } returns Result.success(listOf(testMemory))
        every { embeddingService.similarity(any(), any()) } returns 0.1f

        val result = repository.vectorSearch(
            queryEmbedding = listOf(1.0f, 0.0f, 0.0f),
            limit = 10,
            threshold = 0.5f,
        )
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun getStatistics_delegatesToStorage() = runTest {
        val stats = MemoryStatistics(
            totalMemories = 5,
            memoriesByType = mapOf(MemoryType.ShortTerm to 3, MemoryType.LongTerm to 2),
            averageImportance = 0.6f,
            totalTokens = 0,
        )
        coEvery { memoryStorage.getStatistics() } returns stats

        val result = repository.getStatistics()
        assertEquals(5, result.totalMemories)
    }

    @Test
    fun cleanup_delegatesToStorage() = runTest {
        coEvery { memoryStorage.cleanup(any()) } returns Result.success(3)

        val result = repository.cleanup(testInstant)
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrThrow())
    }

    @Test
    fun observeAll_delegatesToStorage() = runTest {
        every { memoryStorage.observeAll() } returns flowOf(listOf(testMemory))

        val flow = repository.observeAll()
        flow.collect { memories ->
            assertEquals(1, memories.size)
            assertEquals("mem-1", memories[0].id)
        }
    }
}
