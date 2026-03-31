package com.loy.mingclaw.core.plugin.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginCategory
import com.loy.mingclaw.core.model.plugin.PluginInfo
import com.loy.mingclaw.core.model.plugin.PluginStatus
import com.loy.mingclaw.core.model.plugin.Tool
import com.loy.mingclaw.core.plugin.PluginRegistry
import com.loy.mingclaw.core.plugin.SecurityManager
import com.loy.mingclaw.core.plugin.ToolRegistry
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PluginRegistryImpl @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val securityManager: SecurityManager,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : PluginRegistry {

    private val plugins = ConcurrentHashMap<String, MingClawPlugin>()
    private val pluginStatus = ConcurrentHashMap<String, PluginStatus>()

    override suspend fun registerPlugin(plugin: MingClawPlugin): Result<Unit> {
        if (!securityManager.isPluginSafe(plugin)) {
            return Result.failure(IllegalArgumentException("Plugin failed security check: ${plugin.pluginId}"))
        }
        plugins[plugin.pluginId] = plugin
        pluginStatus[plugin.pluginId] = PluginStatus.Running

        plugin.getTools().forEach { tool ->
            toolRegistry.registerTool(tool)
        }
        return Result.success(Unit)
    }

    override suspend fun unregisterPlugin(pluginId: String): Result<Unit> {
        val plugin = plugins.remove(pluginId)
            ?: return Result.failure(IllegalArgumentException("Plugin not found: $pluginId"))
        plugin.getTools().forEach { tool ->
            toolRegistry.unregisterTool(tool.toolId)
        }
        pluginStatus[pluginId] = PluginStatus.Unregistered
        return Result.success(Unit)
    }

    override fun getPlugin(pluginId: String): MingClawPlugin? = plugins[pluginId]

    override fun getAllPlugins(): List<MingClawPlugin> = plugins.values.toList()

    override fun getPluginsByCategory(category: PluginCategory): List<MingClawPlugin> {
        return getAllPlugins()
    }

    override fun getPluginInfo(pluginId: String): PluginInfo? {
        val plugin = plugins[pluginId] ?: return null
        return PluginInfo(
            pluginId = plugin.pluginId,
            version = plugin.version,
            name = plugin.name,
            description = plugin.description,
            author = plugin.author,
            category = PluginCategory.Tool,
            status = pluginStatus[pluginId] ?: PluginStatus.Unknown,
            permissions = plugin.getRequiredPermissions(),
            dependencies = plugin.getDependencies(),
            tools = plugin.getTools().map { it.toolId }
        )
    }

    override fun searchPlugins(query: String): List<MingClawPlugin> {
        val lowerQuery = query.lowercase()
        return plugins.values.filter {
            it.name.lowercase().contains(lowerQuery) ||
                it.description.lowercase().contains(lowerQuery)
        }
    }

    override fun getAvailableTools(): List<Tool> = toolRegistry.getAllTools()

    override fun getPluginStatus(pluginId: String): PluginStatus? =
        pluginStatus[pluginId]
}
