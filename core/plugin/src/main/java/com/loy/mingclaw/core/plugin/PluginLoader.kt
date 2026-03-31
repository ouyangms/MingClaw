package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginMetadata
import java.io.File

interface PluginLoader {
    suspend fun loadFromFile(file: File): Result<MingClawPlugin>
    fun validatePlugin(plugin: MingClawPlugin): ValidationResult
    suspend fun extractMetadata(file: File): Result<PluginMetadata>
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)
