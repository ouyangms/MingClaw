package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginCategory
import com.loy.mingclaw.core.model.plugin.PluginContext
import com.loy.mingclaw.core.model.plugin.PluginDependency
import com.loy.mingclaw.core.model.plugin.PluginPermission
import com.loy.mingclaw.core.model.plugin.Tool
import com.loy.mingclaw.core.model.plugin.ToolCategory
import com.loy.mingclaw.core.model.plugin.ToolParameter
import com.loy.mingclaw.core.model.plugin.ToolResult
import com.loy.mingclaw.core.model.plugin.PluginStatus
import com.loy.mingclaw.core.plugin.internal.PluginRegistryImpl
import com.loy.mingclaw.core.plugin.internal.SecurityManagerImpl
import com.loy.mingclaw.core.plugin.internal.ToolRegistryImpl
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.common.EventResult

class PluginRegistryImplTest {

    private lateinit var registry: PluginRegistryImpl
    private lateinit var toolRegistry: ToolRegistryImpl
    private lateinit var securityManager: SecurityManagerImpl

    @Before
    fun setup() {
        val dispatcher = StandardTestDispatcher()
        toolRegistry = ToolRegistryImpl(dispatcher)
        securityManager = SecurityManagerImpl()
        registry = PluginRegistryImpl(toolRegistry, securityManager, dispatcher)
    }

    private fun createTool(
        toolId: String = "tool.test",
        name: String = "Test Tool",
    ): Tool {
        return object : Tool {
            override val toolId = toolId
            override val name = name
            override val description = "A test tool"
            override val category = ToolCategory.Information
            override val parameters: Map<String, ToolParameter> = emptyMap()
            override val requiresConfirmation = false

            override suspend fun execute(args: Map<String, Any>): ToolResult {
                return ToolResult.Success(data = "executed")
            }
        }
    }

    private fun createPlugin(
        pluginId: String = "com.test.plugin",
        name: String = "Test Plugin",
        tools: List<Tool> = emptyList(),
    ): MingClawPlugin {
        return object : MingClawPlugin {
            override val pluginId = pluginId
            override val version = "1.0.0"
            override val name = name
            override val description = "A test plugin"
            override val author = "Test Author"

            override fun getDependencies(): List<PluginDependency> = emptyList()
            override fun getRequiredPermissions(): List<PluginPermission> = emptyList()
            override suspend fun onInitialize(context: PluginContext): Result<Unit> = Result.success(Unit)
            override fun onStart() {}
            override fun onStop() {}
            override suspend fun onCleanup() {}
            override fun getTools(): List<Tool> = tools
            override fun handleEvent(event: Event): EventResult = EventResult.Skipped("test")
        }
    }

    @Test
    fun registerPlugin_storesPlugin() = runTest {
        val plugin = createPlugin(pluginId = "com.test.alpha")
        val result = registry.registerPlugin(plugin)
        assertTrue(result.isSuccess)
        assertNotNull(registry.getPlugin("com.test.alpha"))
    }

    @Test
    fun registerPlugin_registersPluginTools() = runTest {
        val tool = createTool(toolId = "tool.from_plugin")
        val plugin = createPlugin(pluginId = "com.test.tools", tools = listOf(tool))
        registry.registerPlugin(plugin)

        val allTools = registry.getAvailableTools()
        assertEquals(1, allTools.size)
        assertEquals("tool.from_plugin", allTools[0].toolId)
    }

    @Test
    fun unregisterPlugin_removesPluginAndTools() = runTest {
        val tool = createTool(toolId = "tool.to_remove")
        val plugin = createPlugin(pluginId = "com.test.remove", tools = listOf(tool))
        registry.registerPlugin(plugin)

        val result = registry.unregisterPlugin("com.test.remove")
        assertTrue(result.isSuccess)
        assertNull(registry.getPlugin("com.test.remove"))
        assertEquals(0, registry.getAvailableTools().size)
    }

    @Test
    fun getAllPlugins_returnsAllRegistered() = runTest {
        registry.registerPlugin(createPlugin(pluginId = "com.test.p1"))
        registry.registerPlugin(createPlugin(pluginId = "com.test.p2"))
        val all = registry.getAllPlugins()
        assertEquals(2, all.size)
    }

    @Test
    fun getPluginsByCategory_returnsPlugins() = runTest {
        registry.registerPlugin(createPlugin(pluginId = "com.test.cat"))
        val plugins = registry.getPluginsByCategory(PluginCategory.Tool)
        assertEquals(1, plugins.size)
    }

    @Test
    fun registerPlugin_rejectsInvalidPluginId() = runTest {
        val plugin = createPlugin(pluginId = "INVALID ID!")
        val result = registry.registerPlugin(plugin)
        assertTrue(result.isFailure)
    }
}
