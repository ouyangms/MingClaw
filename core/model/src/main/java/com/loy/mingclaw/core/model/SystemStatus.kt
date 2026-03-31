package com.loy.mingclaw.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SystemStatus(
    val isRunning: Boolean = false,
    val loadedPluginCount: Int = 0,
    val activeTaskCount: Int = 0,
    val uptimeSeconds: Long = 0,
    val lastHealthCheckTimestamp: Long = 0,
)

enum class PluginState {
    Registered, Loading, Running, Stopped, Error, Unregistered,
}
