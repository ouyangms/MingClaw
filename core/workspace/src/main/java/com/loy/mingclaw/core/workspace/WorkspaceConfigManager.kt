package com.loy.mingclaw.core.workspace

import com.loy.mingclaw.core.model.workspace.ValidationResult
import com.loy.mingclaw.core.model.workspace.WorkspaceConfig
import java.nio.file.Path

interface WorkspaceConfigManager {
    suspend fun <T : WorkspaceConfig> readConfig(path: Path): Result<T>
    suspend fun <T : WorkspaceConfig> writeConfig(path: Path, config: T): Result<Unit>
    suspend fun <T : WorkspaceConfig> validateConfig(config: T): ValidationResult
}
