package com.loy.mingclaw.core.model.common

data class Subscription(
    val id: String,
    val eventType: String,
)

sealed class EventResult {
    data class Success(val subscriberId: String) : EventResult()
    data class Failed(val subscriberId: String, val error: Throwable) : EventResult()
    data class Skipped(val subscriberId: String) : EventResult()
}

interface EventSubscriber {
    val id: String
    fun onEvent(event: com.loy.mingclaw.core.model.Event): EventResult
}
