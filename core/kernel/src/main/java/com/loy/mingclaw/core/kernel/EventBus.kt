package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.Subscription
import com.loy.mingclaw.core.model.common.EventSubscriber
import kotlinx.coroutines.Job

interface EventBus {
    fun subscribe(eventType: String, subscriber: EventSubscriber): Subscription
    fun unsubscribe(subscription: Subscription)
    fun publish(event: Event): List<EventResult>
    fun publishAsync(event: Event): Job
    suspend fun shutdown()
}
