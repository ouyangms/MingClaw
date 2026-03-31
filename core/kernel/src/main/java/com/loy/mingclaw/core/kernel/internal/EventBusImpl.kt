package com.loy.mingclaw.core.kernel.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.kernel.EventBus
import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.EventSubscriber
import com.loy.mingclaw.core.model.common.Subscription
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EventBusImpl @Inject constructor(
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : EventBus {

    private val subscribers = ConcurrentHashMap<String, MutableList<EventSubscriber>>()
    private val subscriptionToSubscriber = ConcurrentHashMap<String, EventSubscriber>()
    @Volatile
    private var isShutdown = false
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    override fun subscribe(eventType: String, subscriber: EventSubscriber): Subscription {
        if (isShutdown) return Subscription(id = "", eventType = eventType)
        val subscriptionId = UUID.randomUUID().toString()
        subscribers.computeIfAbsent(eventType) { mutableListOf() }.add(subscriber)
        subscriptionToSubscriber[subscriptionId] = subscriber
        return Subscription(id = subscriptionId, eventType = eventType)
    }

    override fun unsubscribe(subscription: Subscription) {
        val subscriber = subscriptionToSubscriber.remove(subscription.id) ?: return
        subscribers[subscription.eventType]?.remove(subscriber)
    }

    override fun publish(event: Event): List<EventResult> {
        if (isShutdown) return emptyList()
        val eventType = event::class.simpleName ?: return emptyList()
        val eventSubscribers = subscribers[eventType] ?: return emptyList()

        return eventSubscribers.map { subscriber ->
            try {
                subscriber.onEvent(event)
            } catch (e: Exception) {
                EventResult.Failed(subscriberId = subscriber.id, error = e)
            }
        }
    }

    override fun publishAsync(event: Event): Job {
        return scope.launch { publish(event) }
    }

    override suspend fun shutdown() {
        isShutdown = true
        subscribers.clear()
        subscriptionToSubscriber.clear()
    }
}
