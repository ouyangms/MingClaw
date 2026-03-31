package com.loy.mingclaw.core.workspace.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.workspace.ValidationResult
import com.loy.mingclaw.core.model.workspace.WorkspaceConfig
import com.loy.mingclaw.core.workspace.ConfigValidator
import com.loy.mingclaw.core.workspace.WorkspaceConfigManager
import com.loy.mingclaw.core.workspace.WorkspaceFileManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WorkspaceConfigManagerImpl @Inject constructor(
    private val fileManager: WorkspaceFileManager,
    private val configValidator: ConfigValidator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : WorkspaceConfigManager {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : WorkspaceConfig> readConfig(path: Path): Result<T> = withContext(ioDispatcher) {
        fileManager.readFile(path).mapCatching { content ->
            json.decodeFromString(WorkspaceConfig.serializer(), content) as T
        }
    }

    override suspend fun <T : WorkspaceConfig> writeConfig(path: Path, config: T): Result<Unit> {
        val validationResult = validateConfig(config)
        if (!validationResult.isValid) {
            return Result.failure(IllegalArgumentException(validationResult.errors.joinToString("; ")))
        }
        val content = json.encodeToString(WorkspaceConfig.serializer(), config)
        return fileManager.writeFile(path, content)
    }

    override suspend fun <T : WorkspaceConfig> validateConfig(config: T): ValidationResult {
        return configValidator.validate(config)
    }
}
