package com.loy.mingclaw.core.model.workspace

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun agentsConfig_serialization_roundTrip() {
        val config = AgentsConfig(
            agents = mapOf(
                "assistant" to AgentConfig(
                    id = "assistant",
                    name = "Assistant",
                    description = "General purpose assistant",
                    capabilities = listOf("conversation"),
                    tools = listOf("web_search"),
                ),
            ),
        )
        val encoded = json.encodeToString(AgentsConfig.serializer(), config)
        val decoded = json.decodeFromString(AgentsConfig.serializer(), encoded)
        assertEquals(config, decoded)
    }

    @Test
    fun toolsConfig_serialization_roundTrip() {
        val config = ToolsConfig(
            tools = mapOf(
                "search" to ToolConfig(
                    id = "search",
                    name = "Web Search",
                    description = "Search the web",
                    category = ToolCategory.SEARCH,
                ),
            ),
        )
        val encoded = json.encodeToString(ToolsConfig.serializer(), config)
        val decoded = json.decodeFromString(ToolsConfig.serializer(), encoded)
        assertEquals(config, decoded)
    }

    @Test
    fun toolCategory_allValues() {
        val categories = ToolCategory.values()
        assertEquals(6, categories.size)
        assertTrue(categories.contains(ToolCategory.SEARCH))
        assertTrue(categories.contains(ToolCategory.CUSTOM))
    }
}
