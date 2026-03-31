package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.kernel.internal.EventBusImpl
import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.KernelConfig
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.EventSubscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventBusImplTest {

    private lateinit var eventBus: EventBusImpl
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        eventBus = EventBusImpl(testDispatcher)
    }

    @Test
    fun `publish delivers event to single subscriber`() = runTest(testDispatcher) {
        var receivedEvent: Event? = null
        val subscriber = object : EventSubscriber {
            override val id = "sub-1"
            override fun onEvent(event: Event): EventResult {
                receivedEvent = event
                return EventResult.Success(id)
            }
        }

        eventBus.subscribe("PluginLoaded", subscriber)
        val event = Event.PluginLoaded(pluginId = "test-plugin")
        val results = eventBus.publish(event)

        assertEquals(1, results.size)
        assertTrue(results[0] is EventResult.Success)
        assertEquals(event, receivedEvent)
    }

    @Test
    fun `publish delivers event to multiple subscribers`() = runTest(testDispatcher) {
        val received = mutableListOf<Event>()
        repeat(3) { index ->
            val subscriber = object : EventSubscriber {
                override val id = "sub-$index"
                override fun onEvent(event: Event): EventResult {
                    received.add(event)
                    return EventResult.Success(id)
                }
            }
            eventBus.subscribe("TaskStarted", subscriber)
        }

        val event = Event.TaskStarted(taskId = "task-1")
        val results = eventBus.publish(event)

        assertEquals(3, results.size)
        assertEquals(3, received.size)
    }

    @Test
    fun `unsubscribe removes subscriber`() = runTest(testDispatcher) {
        var callCount = 0
        val subscriber = object : EventSubscriber {
            override val id = "sub-remove"
            override fun onEvent(event: Event): EventResult {
                callCount++
                return EventResult.Success(id)
            }
        }

        val subscription = eventBus.subscribe("ConfigUpdated", subscriber)
        eventBus.unsubscribe(subscription)

        eventBus.publish(Event.ConfigUpdated(config = KernelConfig()))
        assertEquals(0, callCount)
    }

    @Test
    fun `publish returns empty results when no subscribers`() = runTest(testDispatcher) {
        val event = Event.PluginUnloaded(pluginId = "plugin-1")
        val results = eventBus.publish(event)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `subscriber failure returns Failed result`() = runTest(testDispatcher) {
        val subscriber = object : EventSubscriber {
            override val id = "failing-sub"
            override fun onEvent(event: Event): EventResult {
                throw RuntimeException("boom")
            }
        }

        eventBus.subscribe("PluginError", subscriber)
        val results = eventBus.publish(Event.PluginError(pluginId = "p1", error = "err"))

        assertEquals(1, results.size)
        assertTrue(results[0] is EventResult.Failed)
        assertEquals("failing-sub", (results[0] as EventResult.Failed).subscriberId)
    }

    @Test
    fun `shutdown prevents new subscriptions`() = runTest(testDispatcher) {
        eventBus.shutdown()
        val subscriber = object : EventSubscriber {
            override val id = "late-sub"
            override fun onEvent(event: Event): EventResult = EventResult.Success(id)
        }

        eventBus.subscribe("PluginLoaded", subscriber)
        val results = eventBus.publish(Event.PluginLoaded(pluginId = "p"))
        assertTrue(results.isEmpty())
    }
}
