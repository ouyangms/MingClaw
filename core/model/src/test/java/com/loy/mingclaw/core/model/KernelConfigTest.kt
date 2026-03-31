package com.loy.mingclaw.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class KernelConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `KernelConfig serializes and deserializes with defaults`() {
        val original = KernelConfig()
        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<KernelConfig>(jsonString)
        assertEquals(original, restored)
    }

    @Test
    fun `KernelConfig has correct default values`() {
        val config = KernelConfig()
        assertEquals(8192, config.maxTokens)
        assertEquals("claude-opus-4-6", config.modelConfig.modelName)
        assertEquals(0.7, config.modelConfig.temperature, 0.001)
        assertEquals(4096, config.modelConfig.maxTokens)
    }

    @Test
    fun `KernelConfig serializes with custom values`() {
        val config = KernelConfig(
            maxTokens = 16384,
            modelConfig = ModelConfig(modelName = "gpt-4", temperature = 0.5)
        )
        val jsonString = json.encodeToString(config)
        val restored = json.decodeFromString<KernelConfig>(jsonString)
        assertEquals(16384, restored.maxTokens)
        assertEquals("gpt-4", restored.modelConfig.modelName)
        assertEquals(0.5, restored.modelConfig.temperature, 0.001)
    }
}
