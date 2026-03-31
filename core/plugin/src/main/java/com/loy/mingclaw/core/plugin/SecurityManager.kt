package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginPermission

interface SecurityManager {
    suspend fun initialize(): Result<Unit>
    fun checkPluginPermission(pluginId: String, permission: PluginPermission): Boolean
    fun isPluginSafe(plugin: MingClawPlugin): Boolean
    fun verifySignature(data: ByteArray, signature: ByteArray): Boolean
}
