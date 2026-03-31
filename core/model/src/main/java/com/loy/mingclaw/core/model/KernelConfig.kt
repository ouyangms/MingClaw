package com.loy.mingclaw.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class KernelConfig(
    val maxTokens: Int = 8192,
    val modelConfig: ModelConfig = ModelConfig(),
    val pluginConfig: PluginConfig = PluginConfig(),
)

@Serializable
data class ModelConfig(
    val modelName: String = "claude-opus-4-6",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val timeoutSeconds: Long = 120,
) {
    val timeout: Duration get() = timeoutSeconds.seconds
}

@Serializable
data class PluginConfig(
    val autoLoad: List<String> = emptyList(),
    val disabledPlugins: List<String> = emptyList(),
    val pluginDirectories: List<String> = listOf("/system/plugins", "/user/plugins"),
)
