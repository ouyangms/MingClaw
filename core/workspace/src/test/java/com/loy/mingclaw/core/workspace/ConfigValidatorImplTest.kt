package com.loy.mingclaw.core.workspace

import com.loy.mingclaw.core.model.workspace.AgentConfig
import com.loy.mingclaw.core.model.workspace.AgentsConfig
import com.loy.mingclaw.core.model.workspace.ToolCategory
import com.loy.mingclaw.core.model.workspace.ToolConfig
import com.loy.mingclaw.core.model.workspace.ToolsConfig
import com.loy.mingclaw.core.workspace.internal.ConfigValidatorImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigValidatorImplTest {

    private val validator = ConfigValidatorImpl()

    @Test
    fun validate_validAgentsConfig_returnsValid() {
        val config = AgentsConfig(
            agents = mapOf(
                "assistant" to AgentConfig(
                    id = "assistant",
                    name = "Assistant",
                    description = "General assistant",
                ),
            ),
        )
        val result = validator.validate(config)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun validate_agentsConfigWithBlankId_returnsInvalid() {
        val config = AgentsConfig(
            agents = mapOf(
                "bad" to AgentConfig(id = "", name = "Test", description = ""),
            ),
        )
        val result = validator.validate(config)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("blank id") })
    }

    @Test
    fun validate_agentsConfigWithBlankName_returnsInvalid() {
        val config = AgentsConfig(
            agents = mapOf(
                "bad" to AgentConfig(id = "id", name = "", description = ""),
            ),
        )
        val result = validator.validate(config)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("blank name") })
    }

    @Test
    fun validate_validToolsConfig_returnsValid() {
        val config = ToolsConfig(
            tools = mapOf(
                "search" to ToolConfig(
                    id = "search",
                    name = "Search",
                    description = "Web search",
                    category = ToolCategory.SEARCH,
                ),
            ),
        )
        val result = validator.validate(config)
        assertTrue(result.isValid)
    }
}
