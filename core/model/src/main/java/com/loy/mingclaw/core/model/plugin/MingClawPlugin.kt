package com.loy.mingclaw.core.model.plugin

import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.common.EventResult

interface MingClawPlugin {
    val pluginId: String
    val version: String
    val name: String
    val description: String
    val author: String

    fun getDependencies(): List<PluginDependency>
    fun getRequiredPermissions(): List<PluginPermission>
    suspend fun onInitialize(context: PluginContext): Result<Unit>
    fun onStart()
    fun onStop()
    suspend fun onCleanup()
    fun getTools(): List<Tool>
    fun handleEvent(event: Event): EventResult
}

class PluginContext(
    val pluginId: String,
    val config: Map<String, Any> = emptyMap()
)
