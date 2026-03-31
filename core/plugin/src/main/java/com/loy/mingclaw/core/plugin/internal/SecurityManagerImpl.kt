package com.loy.mingclaw.core.plugin.internal

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginPermission
import com.loy.mingclaw.core.plugin.SecurityManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SecurityManagerImpl @Inject constructor() : SecurityManager {

    private val pluginIdPattern = Regex("[a-z0-9_.]+")
    private var initialized = false

    override suspend fun initialize(): Result<Unit> {
        initialized = true
        return Result.success(Unit)
    }

    override fun checkPluginPermission(pluginId: String, permission: PluginPermission): Boolean {
        // MVP: all permissions denied by default
        return false
    }

    override fun isPluginSafe(plugin: MingClawPlugin): Boolean {
        return pluginIdPattern.matches(plugin.pluginId) &&
            plugin.version.isNotBlank() &&
            plugin.name.isNotBlank()
    }

    override fun verifySignature(data: ByteArray, signature: ByteArray): Boolean {
        // MVP: Signature verification not yet implemented
        return false
    }
}
