package com.loy.mingclaw.core.plugin.internal

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginMetadata
import com.loy.mingclaw.core.plugin.PluginLoader
import com.loy.mingclaw.core.plugin.ValidationResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PluginLoaderImpl @Inject constructor() : PluginLoader {

    override suspend fun loadFromFile(file: File): Result<MingClawPlugin> {
        if (!file.exists()) {
            return Result.failure(IllegalArgumentException("Plugin file not found: ${file.path}"))
        }
        return Result.failure(UnsupportedOperationException("Dynamic plugin loading not yet implemented"))
    }

    override fun validatePlugin(plugin: MingClawPlugin): ValidationResult {
        val errors = mutableListOf<String>()
        if (plugin.pluginId.isBlank()) errors.add("Plugin ID cannot be blank")
        if (plugin.version.isBlank()) errors.add("Version cannot be blank")
        if (plugin.name.isBlank()) errors.add("Name cannot be blank")
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    override suspend fun extractMetadata(file: File): Result<PluginMetadata> {
        if (!file.exists()) {
            return Result.failure(IllegalArgumentException("Plugin file not found: ${file.path}"))
        }
        return Result.failure(UnsupportedOperationException("Metadata extraction not yet implemented"))
    }
}
