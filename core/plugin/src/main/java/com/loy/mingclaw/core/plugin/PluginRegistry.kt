package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginCategory
import com.loy.mingclaw.core.model.plugin.PluginInfo
import com.loy.mingclaw.core.model.plugin.PluginStatus
import com.loy.mingclaw.core.model.plugin.Tool

interface PluginRegistry {
    suspend fun registerPlugin(plugin: MingClawPlugin): Result<Unit>
    suspend fun unregisterPlugin(pluginId: String): Result<Unit>
    fun getPlugin(pluginId: String): MingClawPlugin?
    fun getAllPlugins(): List<MingClawPlugin>
    fun getPluginsByCategory(category: PluginCategory): List<MingClawPlugin>
    fun getPluginInfo(pluginId: String): PluginInfo?
    fun searchPlugins(query: String): List<MingClawPlugin>
    fun getAvailableTools(): List<Tool>
    fun getPluginStatus(pluginId: String): PluginStatus?
}
