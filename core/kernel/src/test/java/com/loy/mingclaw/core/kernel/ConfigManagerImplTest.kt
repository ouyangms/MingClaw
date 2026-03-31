package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.kernel.internal.ConfigManagerImpl
import com.loy.mingclaw.core.kernel.internal.DefaultKernelConfigProvider
import com.loy.mingclaw.core.model.KernelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigManagerImplTest {

    private lateinit var configManager: ConfigManagerImpl
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        configManager = ConfigManagerImpl(
            defaultConfigProvider = DefaultKernelConfigProvider(),
            dispatcher = testDispatcher,
        )
    }

    @Test
    fun `getConfig returns default config initially`() {
        val config = configManager.getConfig()
        val defaultConfig = KernelConfig()
        assertEquals(defaultConfig.maxTokens, config.maxTokens)
        assertEquals(defaultConfig.modelConfig.modelName, config.modelConfig.modelName)
    }

    @Test
    fun `updateConfig applies partial updates`() {
        val result = configManager.updateConfig(
            updates = mapOf("maxTokens" to 16384),
        )
        val updated = result.getOrNull()
        assertEquals(16384, updated?.maxTokens)
    }

    @Test
    fun `updateConfig rejects invalid maxTokens`() {
        val result = configManager.updateConfig(
            updates = mapOf("maxTokens" to -1),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `updateConfig rejects blank modelName`() {
        val result = configManager.updateConfig(
            updates = mapOf("modelName" to ""),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `resetToDefault restores defaults`() {
        configManager.updateConfig(mapOf("maxTokens" to 16384))
        configManager.resetToDefault()
        val config = configManager.getConfig()
        assertEquals(KernelConfig().maxTokens, config.maxTokens)
    }

    @Test
    fun `watchConfigChanges emits on update`() = runTest(testDispatcher) {
        var emittedConfig: KernelConfig? = null
        val job = CoroutineScope(testDispatcher).launch {
            emittedConfig = configManager.watchConfigChanges().first()
        }

        configManager.updateConfig(mapOf("maxTokens" to 4096))
        job.join()

        assertEquals(4096, emittedConfig?.maxTokens)
    }
}
