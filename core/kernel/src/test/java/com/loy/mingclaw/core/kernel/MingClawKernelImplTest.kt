package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.kernel.internal.ConfigManagerImpl
import com.loy.mingclaw.core.kernel.internal.DefaultKernelConfigProvider
import com.loy.mingclaw.core.kernel.internal.EventBusImpl
import com.loy.mingclaw.core.kernel.internal.MingClawKernelImpl
import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.KernelConfig
import com.loy.mingclaw.core.model.SystemStatus
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.EventSubscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MingClawKernelImplTest {

    private lateinit var kernel: MingClawKernelImpl
    private lateinit var eventBus: EventBusImpl
    private lateinit var configManager: ConfigManagerImpl
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        eventBus = EventBusImpl(testDispatcher)
        configManager = ConfigManagerImpl(DefaultKernelConfigProvider(), testDispatcher)
        kernel = MingClawKernelImpl(eventBus, configManager, testDispatcher)
    }

    @Test
    fun `getSystemStatus returns initial state`() = runTest(testDispatcher) {
        val status = kernel.getSystemStatus()
        assertFalse(status.isRunning)
        assertEquals(0, status.loadedPluginCount)
        assertEquals(0, status.activeTaskCount)
    }

    @Test
    fun `getConfig returns default config`() {
        val config = kernel.getConfig()
        val defaultConfig = KernelConfig()
        assertEquals(defaultConfig.maxTokens, config.maxTokens)
    }

    @Test
    fun `updateConfig delegates to config manager`() {
        val result = kernel.updateConfig(mapOf("maxTokens" to 16384))
        assertEquals(16384, result.getOrNull()?.maxTokens)
    }

    @Test
    fun `subscribe and publish work through kernel`() = runTest(testDispatcher) {
        var receivedEvent: Event? = null
        val subscriber = object : EventSubscriber {
            override val id = "test-sub"
            override fun onEvent(event: Event): EventResult {
                receivedEvent = event
                return EventResult.Success(id)
            }
        }

        kernel.subscribe("PluginLoaded", subscriber)
        val results = kernel.publish(Event.PluginLoaded(pluginId = "p1"))
        assertEquals(1, results.size)
        assertEquals("p1", (receivedEvent as Event.PluginLoaded).pluginId)
    }

    @Test
    fun `shutdown updates system status`() = runTest(testDispatcher) {
        kernel.shutdown()
        val status = kernel.getSystemStatus()
        assertFalse(status.isRunning)
    }
}
