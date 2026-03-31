package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.model.KernelConfig
import kotlinx.coroutines.flow.Flow

interface ConfigManager {
    fun getConfig(): KernelConfig
    fun updateConfig(updates: Map<String, Any>): Result<KernelConfig>
    fun resetToDefault(): KernelConfig
    fun watchConfigChanges(): Flow<KernelConfig>
}
