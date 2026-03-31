package com.loy.mingclaw.core.model.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginTypesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `PluginMetadata serializes and deserializes`() {
        val original = PluginMetadata(
            pluginId = "tools.calculator",
            version = "1.0.0",
            name = "Calculator",
            description = "Basic calculator",
            author = "MingClaw",
            category = PluginCategory.Tool,
            permissions = listOf("NetworkAccess"),
            dependencies = listOf(PluginDependency(pluginId = "core.math", minVersion = "1.0.0")),
            entryPoint = "com.loy.mingclaw.plugin.CalculatorPlugin",
            minKernelVersion = "1.0.0",
            checksum = "sha256:abc123"
        )
        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<PluginMetadata>(jsonString)
        assertEquals(original, restored)
    }

    @Test
    fun `PluginDependency default values`() {
        val dep = PluginDependency(pluginId = "core.utils", minVersion = "1.0.0")
        assertEquals("core.utils", dep.pluginId)
        assertEquals("1.0.0", dep.minVersion)
        assertEquals(null, dep.maxVersion)
        assertTrue(dep.required)
    }

    @Test
    fun `PluginPermission has all expected values`() {
        val permissions = PluginPermission.values()
        assertTrue(permissions.any { it.name == "NetworkAccess" })
        assertTrue(permissions.any { it.name == "FileSystemRead" })
        assertTrue(permissions.any { it.name == "PluginManagement" })
    }

    @Test
    fun `ToolParameter serializes with defaults`() {
        val param = ToolParameter(
            name = "query",
            type = ParameterType.String,
            description = "Search query"
        )
        val jsonString = json.encodeToString(param)
        val restored = json.decodeFromString<ToolParameter>(jsonString)
        assertEquals("query", restored.name)
        assertEquals(ParameterType.String, restored.type)
        assertEquals(false, restored.required)
        assertEquals(null, restored.default)
    }
}
