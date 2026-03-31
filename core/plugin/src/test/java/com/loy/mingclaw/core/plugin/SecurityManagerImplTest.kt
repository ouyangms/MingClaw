package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginContext
import com.loy.mingclaw.core.model.plugin.PluginDependency
import com.loy.mingclaw.core.model.plugin.PluginPermission
import com.loy.mingclaw.core.model.plugin.Tool
import com.loy.mingclaw.core.model.plugin.ToolCategory
import com.loy.mingclaw.core.model.plugin.ToolParameter
import com.loy.mingclaw.core.plugin.internal.SecurityManagerImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.common.EventResult

class SecurityManagerImplTest {

    private lateinit var securityManager: SecurityManagerImpl

    @Before
    fun setup() {
        securityManager = SecurityManagerImpl()
    }

    private fun createPlugin(
        pluginId: String = "com.test.plugin",
        version: String = "1.0.0",
        name: String = "Test Plugin",
    ): MingClawPlugin {
        return object : MingClawPlugin {
            override val pluginId = pluginId
            override val version = version
            override val name = name
            override val description = "A test plugin"
            override val author = "Test Author"

            override fun getDependencies(): List<PluginDependency> = emptyList()
            override fun getRequiredPermissions(): List<PluginPermission> = emptyList()
            override suspend fun onInitialize(context: PluginContext): Result<Unit> = Result.success(Unit)
            override fun onStart() {}
            override fun onStop() {}
            override suspend fun onCleanup() {}
            override fun getTools(): List<Tool> = emptyList()
            override fun handleEvent(event: Event): EventResult = EventResult.Skipped("test")
        }
    }

    @Test
    fun initialize_succeeds() = runTest {
        val result = securityManager.initialize()
        assertTrue(result.isSuccess)
    }

    @Test
    fun checkPluginPermission_returnsFalseForUngranted() {
        val result = securityManager.checkPluginPermission(
            "com.test.plugin",
            PluginPermission.NetworkAccess
        )
        assertFalse(result)
    }

    @Test
    fun isPluginSafe_validatesGoodPluginIdFormat() {
        val plugin = createPlugin(pluginId = "com.test.my_plugin")
        assertTrue(securityManager.isPluginSafe(plugin))
    }

    @Test
    fun isPluginSafe_rejectsInvalidPluginId() {
        val plugin = createPlugin(pluginId = "INVALID PLUGIN ID!")
        assertFalse(securityManager.isPluginSafe(plugin))
    }

    @Test
    fun verifySignature_returnsFalse() {
        val result = securityManager.verifySignature(
            data = "test".toByteArray(),
            signature = "sig".toByteArray()
        )
        assertFalse(result)
    }
}
