package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.Tool
import com.loy.mingclaw.core.model.plugin.ToolCategory
import com.loy.mingclaw.core.model.plugin.ToolParameter
import com.loy.mingclaw.core.model.plugin.ToolResult
import com.loy.mingclaw.core.plugin.internal.ToolRegistryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher

class ToolRegistryImplTest {

    private lateinit var registry: ToolRegistryImpl

    @Before
    fun setup() {
        registry = ToolRegistryImpl(StandardTestDispatcher())
    }

    private fun createTool(
        toolId: String = "tool.test",
        name: String = "Test Tool",
        description: String = "A test tool",
        category: ToolCategory = ToolCategory.Information,
    ): Tool {
        return object : Tool {
            override val toolId = toolId
            override val name = name
            override val description = description
            override val category = category
            override val parameters: Map<String, ToolParameter> = emptyMap()
            override val requiresConfirmation = false

            override suspend fun execute(args: Map<String, Any>): ToolResult {
                return ToolResult.Success(data = "executed")
            }
        }
    }

    @Test
    fun registerTool_addsAndRetrieves() {
        val tool = createTool(toolId = "tool.alpha")
        val result = registry.registerTool(tool)
        assertTrue(result.isSuccess)

        val retrieved = registry.getTool("tool.alpha")
        assertNotNull(retrieved)
        assertEquals("tool.alpha", retrieved!!.toolId)
    }

    @Test
    fun registerTool_rejectsDuplicate() {
        val tool = createTool(toolId = "tool.dup")
        registry.registerTool(tool)
        val result = registry.registerTool(tool)
        assertTrue(result.isFailure)
    }

    @Test
    fun unregisterTool_removesTool() {
        val tool = createTool(toolId = "tool.remove")
        registry.registerTool(tool)
        val result = registry.unregisterTool("tool.remove")
        assertTrue(result.isSuccess)
        assertNull(registry.getTool("tool.remove"))
    }

    @Test
    fun getAllTools_returnsAll() {
        registry.registerTool(createTool(toolId = "tool.a"))
        registry.registerTool(createTool(toolId = "tool.b"))
        val all = registry.getAllTools()
        assertEquals(2, all.size)
    }

    @Test
    fun getToolsByCategory_filters() {
        registry.registerTool(createTool(toolId = "tool.info", category = ToolCategory.Information))
        registry.registerTool(createTool(toolId = "tool.action", category = ToolCategory.Action))
        val infoTools = registry.getToolsByCategory(ToolCategory.Information)
        assertEquals(1, infoTools.size)
        assertEquals("tool.info", infoTools[0].toolId)
    }

    @Test
    fun executeTool_runsTool() = runTest {
        val tool = createTool(toolId = "tool.exec")
        registry.registerTool(tool)
        val result = registry.executeTool("tool.exec", emptyMap())
        assertTrue(result is ToolResult.Success)
        assertEquals("executed", (result as ToolResult.Success).data)
    }

    @Test
    fun executeTool_returnsErrorForUnknownTool() = runTest {
        val result = registry.executeTool("tool.nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
        assertEquals("TOOL_NOT_FOUND", (result as ToolResult.Error).code)
    }
}
