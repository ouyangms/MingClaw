package com.loy.mingclaw.core.workspace.internal

import com.loy.mingclaw.core.model.workspace.AgentsConfig
import com.loy.mingclaw.core.model.workspace.CapabilitiesConfig
import com.loy.mingclaw.core.model.workspace.ToolsConfig
import com.loy.mingclaw.core.model.workspace.ValidationResult
import com.loy.mingclaw.core.model.workspace.WorkspaceConfig
import com.loy.mingclaw.core.workspace.ConfigValidator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ConfigValidatorImpl @Inject constructor() : ConfigValidator {

    override fun validate(config: WorkspaceConfig): ValidationResult {
        val errors = mutableListOf<String>()

        if (config.version.isBlank()) {
            errors.add("Config version must not be blank")
        }
        if (config.schema.isBlank()) {
            errors.add("Config schema must not be blank")
        }

        when (config) {
            is AgentsConfig -> validateAgentsConfig(config, errors)
            is CapabilitiesConfig -> validateCapabilitiesConfig(config, errors)
            is ToolsConfig -> validateToolsConfig(config, errors)
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
        )
    }

    private fun validateAgentsConfig(config: AgentsConfig, errors: MutableList<String>) {
        config.agents.forEach { (key, agent) ->
            if (agent.id.isBlank()) errors.add("Agent '$key' has blank id")
            if (agent.name.isBlank()) errors.add("Agent '$key' has blank name")
        }
    }

    private fun validateCapabilitiesConfig(config: CapabilitiesConfig, errors: MutableList<String>) {
        config.capabilities.forEach { (key, cap) ->
            if (cap.id.isBlank()) errors.add("Capability '$key' has blank id")
            if (cap.name.isBlank()) errors.add("Capability '$key' has blank name")
        }
    }

    private fun validateToolsConfig(config: ToolsConfig, errors: MutableList<String>) {
        config.tools.forEach { (key, tool) ->
            if (tool.id.isBlank()) errors.add("Tool '$key' has blank id")
            if (tool.name.isBlank()) errors.add("Tool '$key' has blank name")
        }
    }
}
