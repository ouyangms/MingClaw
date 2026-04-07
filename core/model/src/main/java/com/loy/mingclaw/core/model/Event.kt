package com.loy.mingclaw.core.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

sealed interface Event {
    val timestamp: Instant

    @Serializable
    data class PluginLoaded(
        val pluginId: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class PluginUnloaded(
        val pluginId: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class PluginError(
        val pluginId: String,
        val error: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class TaskStarted(
        val taskId: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class TaskCompleted(
        val taskId: String,
        val result: String? = null,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class TaskFailed(
        val taskId: String,
        val error: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class ConfigUpdated(
        val config: KernelConfig,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class EvolutionTriggered(
        val evolutionType: String,
        val reason: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class EvolutionCompleted(
        val evolutionId: String,
        val changes: List<String>,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class EvolutionFailed(
        val evolutionId: String,
        val error: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event
}
