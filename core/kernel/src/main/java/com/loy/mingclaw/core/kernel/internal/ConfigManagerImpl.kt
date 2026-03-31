package com.loy.mingclaw.core.kernel.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.model.KernelConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ConfigManagerImpl @Inject constructor(
    private val defaultConfigProvider: DefaultKernelConfigProvider,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : ConfigManager {

    private val configRef = AtomicReference(defaultConfigProvider.provide())
    private val _configChanges = MutableSharedFlow<KernelConfig>(replay = 1)

    override fun getConfig(): KernelConfig = configRef.get()

    override fun updateConfig(updates: Map<String, Any>): Result<KernelConfig> {
        val current = configRef.get()
        val newConfig = applyUpdates(current, updates)
            ?: return Result.failure(IllegalArgumentException("Invalid config updates"))

        configRef.set(newConfig)
        _configChanges.tryEmit(newConfig)
        return Result.success(newConfig)
    }

    override fun resetToDefault(): KernelConfig {
        val default = defaultConfigProvider.provide()
        configRef.set(default)
        _configChanges.tryEmit(default)
        return default
    }

    override fun watchConfigChanges(): Flow<KernelConfig> = _configChanges.asSharedFlow()

    private fun applyUpdates(
        current: KernelConfig,
        updates: Map<String, Any>,
    ): KernelConfig? {
        var config = current

        updates.forEach { (key, value) ->
            config = when (key) {
                "maxTokens" -> {
                    val tokens = (value as? Number)?.toInt() ?: return null
                    if (tokens <= 0) return null
                    config.copy(maxTokens = tokens)
                }
                "modelName" -> {
                    val name = value as? String ?: return null
                    if (name.isBlank()) return null
                    config.copy(
                        modelConfig = config.modelConfig.copy(modelName = name)
                    )
                }
                "temperature" -> {
                    val temp = (value as? Number)?.toDouble() ?: return null
                    if (temp < 0 || temp > 2) return null
                    config.copy(
                        modelConfig = config.modelConfig.copy(temperature = temp)
                    )
                }
                else -> return null
            }
        }
        return config
    }
}
