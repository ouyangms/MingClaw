package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.KernelConfig
import com.loy.mingclaw.core.model.SystemStatus
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.EventSubscriber
import com.loy.mingclaw.core.model.common.Subscription

interface MingClawKernel {
    fun getSystemStatus(): SystemStatus
    fun getConfig(): KernelConfig
    fun updateConfig(updates: Map<String, Any>): Result<KernelConfig>
    fun subscribe(eventType: String, subscriber: EventSubscriber): Subscription
    fun publish(event: Event): List<EventResult>
    suspend fun shutdown()
}
