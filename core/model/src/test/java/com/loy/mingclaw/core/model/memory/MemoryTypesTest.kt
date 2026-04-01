package com.loy.mingclaw.core.model.memory

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryTypesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun memory_serialization_roundTrip() {
        val now = Clock.System.now()
        val memory = Memory(
            id = "mem-1",
            content = "Test memory content",
            type = MemoryType.LongTerm,
            importance = 0.8f,
            metadata = mapOf("source" to "test"),
            embedding = listOf(0.1f, 0.2f, 0.3f),
            createdAt = now,
            accessedAt = now,
        )
        val encoded = json.encodeToString(Memory.serializer(), memory)
        val decoded = json.decodeFromString(Memory.serializer(), encoded)
        assertEquals(memory, decoded)
    }

    @Test
    fun memoryType_allValues() {
        val types = MemoryType.values()
        assertEquals(5, types.size)
        assertTrue(types.contains(MemoryType.ShortTerm))
        assertTrue(types.contains(MemoryType.LongTerm))
        assertTrue(types.contains(MemoryType.Semantic))
    }
}
