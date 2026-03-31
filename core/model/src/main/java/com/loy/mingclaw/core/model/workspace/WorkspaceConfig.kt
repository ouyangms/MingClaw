package com.loy.mingclaw.core.model.workspace

import kotlinx.serialization.Serializable

@Serializable
sealed class WorkspaceConfig {
    abstract val version: String
    abstract val schema: String
}

@Serializable
data class AgentsConfig(
    override val version: String = "1.0",
    override val schema: String = "agents-v1",
    val agents: Map<String, AgentConfig> = emptyMap(),
) : WorkspaceConfig()

@Serializable
data class AgentConfig(
    val id: String,
    val name: String,
    val description: String,
    val capabilities: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

@Serializable
data class CapabilitiesConfig(
    override val version: String = "1.0",
    override val schema: String = "capabilities-v1",
    val capabilities: Map<String, CapabilityConfig> = emptyMap(),
) : WorkspaceConfig()

@Serializable
data class CapabilityConfig(
    val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean = true,
    val parameters: Map<String, ParameterConfig> = emptyMap(),
)

@Serializable
data class ParameterConfig(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val default: String? = null,
)

@Serializable
data class ToolsConfig(
    override val version: String = "1.0",
    override val schema: String = "tools-v1",
    val tools: Map<String, ToolConfig> = emptyMap(),
) : WorkspaceConfig()

@Serializable
data class ToolConfig(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val enabled: Boolean = true,
    val permissions: List<String> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

@Serializable
enum class ToolCategory {
    SEARCH, FILE_SYSTEM, CALCULATOR, DATETIME, NETWORK, CUSTOM,
}
