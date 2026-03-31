package com.loy.mingclaw.core.kernel.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.kernel.EventBus
import com.loy.mingclaw.core.kernel.MingClawKernel
import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.KernelConfig
import com.loy.mingclaw.core.model.SystemStatus
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.EventSubscriber
import com.loy.mingclaw.core.model.common.Subscription
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MingClawKernelImpl @Inject constructor(
    private val eventBus: EventBus,
    private val configManager: ConfigManager,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : MingClawKernel {

    private val isRunning = AtomicBoolean(false)
    private val systemStatus = AtomicReference(SystemStatus())

    override fun getSystemStatus(): SystemStatus = systemStatus.get()

    override fun getConfig(): KernelConfig = configManager.getConfig()

    override fun updateConfig(updates: Map<String, Any>): Result<KernelConfig> {
        return configManager.updateConfig(updates)
    }

    override fun subscribe(eventType: String, subscriber: EventSubscriber): Subscription {
        return eventBus.subscribe(eventType, subscriber)
    }

    override fun publish(event: Event): List<EventResult> {
        return eventBus.publish(event)
    }

    override suspend fun shutdown() {
        isRunning.set(false)
        eventBus.shutdown()
        systemStatus.set(SystemStatus())
    }
}
