# MingClaw 工作区与配置管理

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [工作区概述](#工作区概述)
2. [工作区结构](#工作区结构)
3. [文件管理](#文件管理)
4. [配置管理](#配置管理)
5. [目录扫描](#目录扫描)
6. [依赖关系](#依赖关系)
7. [附录](#附录)

---

## 工作区概述

### 设计目标

MingClaw 工作区管理系统提供：

| 目标 | 说明 | 实现方式 |
|------|------|----------|
| **目录隔离** | 不同工作区相互独立 | 独立的文件和配置空间 |
| **灵活配置** | 支持多种配置方式 | Markdown + YAML |
| **热加载** | 配置变更即时生效 | 文件监听 + 事件通知 |
| **版本管理** | 配置可回滚 | 历史版本记录 |
| **安全性** | 防止配置冲突 | 校验和冲突检测 |

### 工作区架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Workspace Manager                                │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                      Workspace Registry                           │  │
│  │  - 工作区注册和管理                                                │  │
│  │  - 工作区切换                                                      │  │
│  │  - 工作区状态跟踪                                                  │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌───────────────┬─────────────────┼─────────────────┬─────────────────┐ │
│  │               │                 │                 │                 │ │
│  ▼               ▼                 ▼                 ▼                 │ │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ │
│  │Directory  │ │   File    │ │  Config   │ │  Version  │ │ Validation│ │
│  │ Scanner   │ │  Manager  │ │  Manager  │ │ Controller│ │  Manager  │ │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Workspace Structure                            │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  MingClawWorkspace/                                               │  │
│  │  ├── AGENTS.md              # 行为规则                            │  │
│  │  ├── MEMORY.md              # 长期记忆                            │  │
│  │  ├── CONFIG/                # 配置目录                            │  │
│  │  │   ├── agents.yaml        # 代理配置                            │  │
│  │  │   ├── capabilities.yaml  # 能力配置                            │  │
│  │  │   └── tools.yaml         # 工具配置                            │  │
│  │  ├── SKILLS/                # 技能包                              │  │
│  │  │   ├── skill-name/        # 技能目录                            │  │
│  │  │   │   ├── skill.md       # 技能描述                            │  │
│  │  │   │   ├── schema.yaml    # 工具模式                            │  │
│  │  │   │   └── impl.kt        # 实现                                │  │
│  │  │   └── ...                                                      │  │
│  │  └── EXPERIENCE/             # 经验数据                            │  │
│  │      ├── feedback/           # 反馈数据                            │  │
│  │      ├── patterns/           # 模式数据                            │  │
│  │      └── metrics/            # 指标数据                            │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 工作区结构

### 核心接口

```kotlin
/**
 * 工作区管理器接口
 */
interface WorkspaceManager {

    /**
     * 获取当前工作区
     */
    fun getCurrentWorkspace(): Workspace

    /**
     * 列出所有工作区
     */
    fun listWorkspaces(): Flow<List<Workspace>>

    /**
     * 创建新工作区
     */
    suspend fun createWorkspace(
        name: String,
        template: WorkspaceTemplate? = null
    ): Result<Workspace>

    /**
     * 切换工作区
     */
    suspend fun switchWorkspace(workspaceId: String): Result<Unit>

    /**
     * 删除工作区
     */
    suspend fun deleteWorkspace(workspaceId: String): Result<Unit>

    /**
     * 克隆工作区
     */
    suspend fun cloneWorkspace(
        sourceId: String,
        newName: String
    ): Result<Workspace>

    /**
     * 导出工作区
     */
    suspend fun exportWorkspace(
        workspaceId: String,
        destination: Uri
    ): Result<Unit>

    /**
     * 导入工作区
     */
    suspend fun importWorkspace(source: Uri): Result<Workspace>
}

/**
 * 工作区数据类
 */
@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val path: String,
    val createdAt: Instant,
    val modifiedAt: Instant,
    val isActive: Boolean = false,
    val metadata: WorkspaceMetadata = WorkspaceMetadata()
)

@Serializable
data class WorkspaceMetadata(
    val description: String = "",
    val tags: List<String> = emptyList(),
    val version: String = "1.0",
    val templateId: String? = null
)
```

### 工作区实现

```kotlin
/**
 * 工作区管理器实现
 */
internal class WorkspaceManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileManager: WorkspaceFileManager,
    private val configManager: WorkspaceConfigManager,
    private val directoryScanner: DirectoryScanner,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : WorkspaceManager {

    private val currentWorkspaceId = MutableStateFlow<String?>(null)
    private val workspacesCache = mutableMapOf<String, Workspace>()

    override fun getCurrentWorkspace(): Workspace {
        val workspaceId = currentWorkspaceId.value ?: loadDefaultWorkspace()
        return workspacesCache[workspaceId] ?: loadWorkspace(workspaceId)
    }

    override fun listWorkspaces(): Flow<List<Workspace>> = flow {
        val workspaceDir = getWorkspacesRootDirectory()
        val directories = directoryScanner.scanDirectories(workspaceDir)

        val workspaces = directories.map { dir ->
            loadWorkspaceFromDirectory(dir)
        }

        emit(workspaces)
    }.flowOn(ioDispatcher)

    override suspend fun createWorkspace(
        name: String,
        template: WorkspaceTemplate?
    ): Result<Workspace> = withContext(ioDispatcher) {
        try {
            // 1. 验证工作区名称
            if (!validateWorkspaceName(name)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid workspace name: $name")
                )
            }

            // 2. 创建工作区目录
            val workspaceId = generateWorkspaceId(name)
            val workspacePath = createWorkspaceDirectory(workspaceId)

            // 3. 应用模板
            template?.let { applyTemplate(workspacePath, it) }

            // 4. 创建工作区对象
            val workspace = Workspace(
                id = workspaceId,
                name = name,
                path = workspacePath,
                createdAt = Clock.System.now(),
                modifiedAt = Clock.System.now(),
                isActive = true
            )

            // 5. 注册工作区
            workspacesCache[workspaceId] = workspace
            currentWorkspaceId.value = workspaceId

            Result.success(workspace)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadDefaultWorkspace(): String {
        val defaultId = "default"
        if (!workspacesCache.containsKey(defaultId)) {
            workspacesCache[defaultId] = loadWorkspaceFromDirectory(
                getWorkspacesRootDirectory().resolve(defaultId)
            )
        }
        return defaultId
    }

    private fun loadWorkspace(workspaceId: String): Workspace {
        return workspacesCache[workspaceId] ?: run {
            val workspaceDir = getWorkspacesRootDirectory().resolve(workspaceId)
            loadWorkspaceFromDirectory(workspaceDir)
        }
    }

    private suspend fun loadWorkspaceFromDirectory(dir: Path): Workspace {
        val configPath = dir.resolve("workspace.json")
        val config = if (fileManager.exists(configPath)) {
            fileManager.readJson<WorkspaceConfig>(configPath)
        } else {
            WorkspaceConfig.default()
        }

        return Workspace(
            id = dir.name,
            name = config.name,
            path = dir.toString(),
            createdAt = config.createdAt,
            modifiedAt = config.modifiedAt,
            isActive = config.isActive
        )
    }

    companion object {
        private const val WORKSPACES_DIR = "workspaces"

        fun getWorkspacesRootDirectory(): Path {
            return Path(
                Environment.getExternalStorageDirectory()
                    .resolve("MingClaw")
                    .resolve(WORKSPACES_DIR)
                    .absolutePath
            )
        }
    }
}
```

---

## 文件管理

### 文件管理器接口

```kotlin
/**
 * 工作区文件管理器接口
 */
interface WorkspaceFileManager {

    /**
     * 读取文件内容
     */
    suspend fun readFile(path: Path): Result<String>

    /**
     * 写入文件内容
     */
    suspend fun writeFile(path: Path, content: String): Result<Unit>

    /**
     * 追加文件内容
     */
    suspend fun appendFile(path: Path, content: String): Result<Unit>

    /**
     * 删除文件
     */
    suspend fun deleteFile(path: Path): Result<Unit>

    /**
     * 检查文件是否存在
     */
    fun exists(path: Path): Boolean

    /**
     * 复制文件
     */
    suspend fun copyFile(source: Path, destination: Path): Result<Unit>

    /**
     * 移动文件
     */
    suspend fun moveFile(source: Path, destination: Path): Result<Unit>

    /**
     * 读取JSON文件
     */
    suspend inline fun <reified T> readJson(path: Path): T

    /**
     * 写入JSON文件
     */
    suspend fun <T> writeJson(path: Path, data: T): Result<Unit>

    /**
     * 监听文件变化
     */
    fun watchFile(path: Path): Flow<FileChangeEvent>

    /**
     * 创建备份
     */
    suspend fun createBackup(path: Path): Result<Path>

    /**
     * 恢复备份
     */
    suspend fun restoreBackup(backupPath: Path): Result<Unit>
}

/**
 * 文件变化事件
 */
sealed class FileChangeEvent {
    data class Created(val path: Path) : FileChangeEvent()
    data class Modified(val path: Path) : FileChangeEvent()
    data class Deleted(val path: Path) : FileChangeEvent()
    data class Moved(val from: Path, val to: Path) : FileChangeEvent()
}
```

### 文件管理器实现

```kotlin
/**
 * 工作区文件管理器实现
 */
internal class WorkspaceFileManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : WorkspaceFileManager {

    private val fileWatchers = mutableMapOf<Path, Job>()
    private val mutex = Mutex()

    override suspend fun readFile(path: Path): Result<String> = withContext(ioDispatcher) {
        try {
            val content = context.contentResolver.openInputStream(path.toUri())?.bufferedReader()
                ?.use { it.readText() }
                ?: return@withContext Result.failure(IOException("File not found: $path"))

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeFile(path: Path, content: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            mutex.withLock {
                // 创建备份
                createBackup(path)

                // 写入临时文件
                val tempFile = path.resolveSibling("${path.name}.tmp")
                context.contentResolver.openOutputStream(tempFile.toUri())?.bufferedWriter()
                    ?.use { it.write(content) }
                    ?: throw IOException("Cannot open output stream")

                // 原子性重命名
                moveFile(tempFile, path)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun appendFile(path: Path, content: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val existingContent = readFile(path).getOrDefault("")
            writeFile(path, existingContent + content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(path: Path): Result<Unit> = withContext(ioDispatcher) {
        try {
            // 创建备份
            createBackup(path)

            // 删除文件
            DocumentsContract.deleteDocument(context.contentResolver, path.toUri())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun exists(path: Path): Boolean {
        return context.contentResolver.query(
            path.toUri(),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null
        )?.use { it.count > 0 } ?: false
    }

    override suspend fun copyFile(source: Path, destination: Path): Result<Unit> = withContext(ioDispatcher) {
        try {
            context.contentResolver.openInputStream(source.toUri())?.use { input ->
                context.contentResolver.openOutputStream(destination.toUri())?.use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun moveFile(source: Path, destination: Path): Result<Unit> = withContext(ioDispatcher) {
        try {
            DocumentsContract.moveDocument(
                context.contentResolver,
                source.toUri(),
                source.parent?.toUri(),
                destination.toUri()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend inline fun <reified T> readJson(path: Path): T {
        val content = readFile(path).getOrThrow()
        return Json.decodeFromString(content)
    }

    override suspend fun <T> writeJson(path: Path, data: T): Result<Unit> {
        val content = Json.encodeToString(data)
        return writeFile(path, content)
    }

    override fun watchFile(path: Path): Flow<FileChangeEvent> = channelFlow {
        val watcher = FileObserver(path.toString()) {
            trySend(FileChangeEvent.Modified(path))
        }
        watcher.startWatching()

        awaitClose {
            watcher.stopWatching()
        }
    }.flowOn(ioDispatcher)

    override suspend fun createBackup(path: Path): Result<Path> = withContext(ioDispatcher) {
        try {
            val timestamp = Clock.System.now().toEpochMilliseconds()
            val backupPath = path.resolveSibling("${path.name}.backup.$timestamp")
            copyFile(path, backupPath).map { backupPath }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreBackup(backupPath: Path): Result<Unit> = withContext(ioDispatcher) {
        try {
            val originalPath = backupPath.resolveSibling(
                backupPath.name.removeSuffix(".backup.${backupPath.name.split(".").last()}")
            )
            copyFile(backupPath, originalPath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Path.toUri(): Uri = Uri.parse(this.toString())
}
```

---

## 配置管理

### 配置管理器接口

```kotlin
/**
 * 工作区配置管理器接口
 */
interface WorkspaceConfigManager {

    /**
     * 读取配置
     */
    suspend fun <T : WorkspaceConfig> readConfig(
        path: Path,
        configClass: KClass<T>
    ): Result<T>

    /**
     * 写入配置
     */
    suspend fun <T : WorkspaceConfig> writeConfig(
        path: Path,
        config: T
    ): Result<Unit>

    /**
     * 合并配置
     */
    suspend fun <T : WorkspaceConfig> mergeConfig(
        path: Path,
        updates: Map<String, Any>,
        configClass: KClass<T>
    ): Result<T>

    /**
     * 验证配置
     */
    suspend fun <T : WorkspaceConfig> validateConfig(
        config: T
    ): ValidationResult

    /**
     * 监听配置变化
     */
    fun <T : WorkspaceConfig> watchConfig(
        path: Path,
        configClass: KClass<T>
    ): Flow<T>

    /**
     * 获取配置版本
     */
    suspend fun getConfigVersion(path: Path): Result<String>

    /**
     * 回滚配置
     */
    suspend fun rollbackConfig(
        path: Path,
        version: String
    ): Result<Unit>
}

/**
 * 工作区配置基类
 */
@Serializable
sealed class WorkspaceConfig {
    abstract val version: String
    abstract val schema: String
}

/**
 * 代理配置
 */
@Serializable
data class AgentsConfig(
    override val version: String = "1.0",
    override val schema: String = "agents-v1",
    val agents: Map<String, AgentConfig> = emptyMap()
) : WorkspaceConfig()

@Serializable
data class AgentConfig(
    val id: String,
    val name: String,
    val description: String,
    val capabilities: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

/**
 * 能力配置
 */
@Serializable
data class CapabilitiesConfig(
    override val version: String = "1.0",
    override val schema: String = "capabilities-v1",
    val capabilities: Map<String, CapabilityConfig> = emptyMap()
) : WorkspaceConfig()

@Serializable
data class CapabilityConfig(
    val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean = true,
    val parameters: Map<String, ParameterConfig> = emptyMap()
)

@Serializable
data class ParameterConfig(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val default: String? = null
)

/**
 * 工具配置
 */
@Serializable
data class ToolsConfig(
    override val version: String = "1.0",
    override val schema: String = "tools-v1",
    val tools: Map<String, ToolConfig> = emptyMap()
) : WorkspaceConfig()

@Serializable
data class ToolConfig(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val enabled: Boolean = true,
    val permissions: List<String> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

@Serializable
enum class ToolCategory {
    SEARCH,
    FILE_SYSTEM,
    CALCULATOR,
    DATETIME,
    NETWORK,
    CUSTOM
}
```

### 配置管理器实现

```kotlin
/**
 * 工作区配置管理器实现
 */
internal class WorkspaceConfigManagerImpl @Inject constructor(
    private val fileManager: WorkspaceFileManager,
    private val validator: ConfigValidator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : WorkspaceConfigManager {

    private val configCache = mutableMapOf<String, WorkspaceConfig>()
    private val versionHistory = mutableMapOf<String, List<ConfigVersion>>()

    override suspend fun <T : WorkspaceConfig> readConfig(
        path: Path,
        configClass: KClass<T>
    ): Result<T> = withContext(ioDispatcher) {
        try {
            // 检查缓存
            val cacheKey = path.toString()
            configCache[cacheKey]?.let {
                return@withContext Result.success(it as T)
            }

            // 读取配置文件
            val config = fileManager.readJson<T>(path)

            // 验证配置
            val validationResult = validator.validate(config)
            if (!validationResult.isValid) {
                return@withContext Result.failure(
                    ConfigValidationException(validationResult.error)
                )
            }

            // 缓存配置
            configCache[cacheKey] = config

            Result.success(config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun <T : WorkspaceConfig> writeConfig(
        path: Path,
        config: T
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            // 验证配置
            val validationResult = validator.validate(config)
            if (!validationResult.isValid) {
                return@withContext Result.failure(
                    ConfigValidationException(validationResult.error)
                )
            }

            // 保存当前版本
            val currentVersion = getConfigVersion(path).getOrNull()
            currentVersion?.let {
                saveConfigVersion(path, config)
            }

            // 写入配置
            fileManager.writeJson(path, config)

            // 更新缓存
            configCache[path.toString()] = config

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun <T : WorkspaceConfig> mergeConfig(
        path: Path,
        updates: Map<String, Any>,
        configClass: KClass<T>
    ): Result<T> = withContext(ioDispatcher) {
        try {
            // 读取当前配置
            val currentConfig = readConfig(path, configClass).getOrThrow()

            // 合并更新
            val mergedConfig = mergeConfigValues(currentConfig, updates)

            // 验证合并后的配置
            val validationResult = validator.validate(mergedConfig)
            if (!validationResult.isValid) {
                return@withContext Result.failure(
                    ConfigValidationException(validationResult.error)
                )
            }

            // 写入合并后的配置
            writeConfig(path, mergedConfig as T)

            Result.success(mergedConfig as T)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun <T : WorkspaceConfig> validateConfig(
        config: T
    ): ValidationResult = withContext(ioDispatcher) {
        validator.validate(config)
    }

    override fun <T : WorkspaceConfig> watchConfig(
        path: Path,
        configClass: KClass<T>
    ): Flow<T> = fileManager.watchFile(path)
        .filterIsInstance<FileChangeEvent.Modified>()
        .map { readConfig(path, configClass).getOrNull() }
        .filterNotNull()
        .flowOn(ioDispatcher)

    override suspend fun getConfigVersion(path: Path): Result<String> = withContext(ioDispatcher) {
        try {
            val config = fileManager.readFile(path).getOrThrow()
            val version = extractVersionFromConfig(config)
            Result.success(version)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun rollbackConfig(
        path: Path,
        version: String
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val versionList = versionHistory[path.toString()] ?: emptyList()
            val targetVersion = versionList.find { it.version == version }
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Version not found: $version")
                )

            // 恢复配置
            fileManager.writeFile(path, targetVersion.content)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mergeConfigValues(
        config: WorkspaceConfig,
        updates: Map<String, Any>
    ): WorkspaceConfig {
        // 使用 JsonElement 进行合并
        val configJson = Json.encodeToJsonElement(config)
        val updatesJson = Json.encodeToJsonElement(updates)

        val merged = mergeJsonElements(configJson, updatesJson)

        return Json.decodeFromJsonElement(merged)
    }

    private fun mergeJsonElements(
        base: JsonElement,
        override: JsonElement
    ): JsonElement {
        if (base is JsonObject && override is JsonObject) {
            val merged = JsonObject(base.toMutableMap())
            override.forEach { (key, value) ->
                merged[key] = if (merged.containsKey(key)) {
                    mergeJsonElements(merged[key]!!, value)
                } else {
                    value
                }
            }
            return merged
        }
        return override
    }

    private fun saveConfigVersion(path: Path, config: WorkspaceConfig) {
        val version = Clock.System.now().toEpochMilliseconds().toString()
        val content = Json.encodeToString(config)

        versionHistory[path.toString()] = versionHistory.getOrDefault(path.toString(), emptyList()) +
                ConfigVersion(version, content)
    }

    private fun extractVersionFromConfig(content: String): String {
        val json = Json.parseToJsonElement(content)
        return json.jsonObject["version"]?.jsonPrimitive?.content ?: "unknown"
    }

    @Serializable
    private data class ConfigVersion(
        val version: String,
        val content: String
    )
}
```

---

## 目录扫描

### 目录扫描器接口

```kotlin
/**
 * 目录扫描器接口
 */
interface DirectoryScanner {

    /**
     * 扫描目录
     */
    suspend fun scanDirectory(
        path: Path,
        options: ScanOptions = ScanOptions.Default
    ): Result<List<Path>>

    /**
     * 扫描目录（递归）
     */
    suspend fun scanDirectories(
        path: Path,
        options: ScanOptions = ScanOptions.Default
    ): List<Path>

    /**
     * 监听目录变化
     */
    fun watchDirectory(
        path: Path,
        options: WatchOptions = WatchOptions.Default
    ): Flow<DirectoryChangeEvent>

    /**
     * 按模式扫描
     */
    suspend fun scanByPattern(
        path: Path,
        pattern: Regex
    ): Result<List<Path>>

    /**
     * 获取目录信息
     */
    suspend fun getDirectoryInfo(path: Path): Result<DirectoryInfo>

    /**
     * 获取文件树
     */
    suspend fun getFileTree(path: Path): Result<FileTreeNode>
}

/**
 * 扫描选项
 */
@Serializable
data class ScanOptions(
    val recursive: Boolean = true,
    val includeHidden: Boolean = false,
    val maxDepth: Int = Int.MAX_VALUE,
    val fileFilter: ((Path) -> Boolean)? = null,
    val directoryFilter: ((Path) -> Boolean)? = null
) {
    companion object {
        val Default = ScanOptions()
    }
}

/**
 * 监听选项
 */
@Serializable
data class WatchOptions(
    val recursive: Boolean = true,
    val eventTypes: Set<WatchEventType> = WatchEventType.All,
    val notifyOnInitialScan: Boolean = false
) {
    companion object {
        val Default = WatchOptions()
    }
}

@Serializable
enum class WatchEventType {
    CREATE,
    MODIFY,
    DELETE,
    MOVE;

    companion object {
        val All = values().toSet()
    }
}

/**
 * 目录变化事件
 */
sealed class DirectoryChangeEvent {
    abstract val path: Path

    data class Created(override val path: Path) : DirectoryChangeEvent()
    data class Modified(override val path: Path) : DirectoryChangeEvent()
    data class Deleted(override val path: Path) : DirectoryChangeEvent()
    data class Moved(override val path: Path, val oldPath: Path) : DirectoryChangeEvent()
}

/**
 * 目录信息
 */
@Serializable
data class DirectoryInfo(
    val path: String,
    val name: String,
    val fileCount: Int,
    val directoryCount: Int,
    val totalSize: Long,
    val lastModified: Long,
    val isReadable: Boolean,
    val isWritable: Boolean
)

/**
 * 文件树节点
 */
@Serializable
sealed class FileTreeNode {
    abstract val path: Path
    abstract val name: String

    @Serializable
    data class FileNode(
        override val path: Path,
        override val name: String,
        val size: Long,
        val lastModified: Long
    ) : FileTreeNode()

    @Serializable
    data class DirectoryNode(
        override val path: Path,
        override val name: String,
        val children: List<FileTreeNode> = emptyList()
    ) : FileTreeNode()
}
```

### 目录扫描器实现

```kotlin
/**
 * 目录扫描器实现
 */
internal class DirectoryScannerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : DirectoryScanner {

    override suspend fun scanDirectory(
        path: Path,
        options: ScanOptions
    ): Result<List<Path>> = withContext(ioDispatcher) {
        try {
            val results = mutableListOf<Path>()

            fun scan(dir: Path, depth: Int) {
                if (depth > options.maxDepth) return

                val children = context.contentResolver.query(
                    dir.toUri(),
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    generateSequence { if (cursor.moveToNext()) cursor else null }
                        .map { cursor ->
                            val documentId = cursor.getString(0)
                            val name = cursor.getString(1)
                            val mimeType = cursor.getString(2)
                            Triple(documentId, name, mimeType)
                        }
                        .toList()
                } ?: emptyList()

                children.forEach { (documentId, name, mimeType) ->
                    val childPath = dir.resolve(name)

                    when {
                        mimeType == DocumentsContract.Document.MIME_TYPE_DIR &&
                                options.directoryFilter?.invoke(childPath) != false -> {
                            results.add(childPath)
                            if (options.recursive) {
                                scan(childPath, depth + 1)
                            }
                        }
                        mimeType != DocumentsContract.Document.MIME_TYPE_DIR &&
                                options.fileFilter?.invoke(childPath) != false -> {
                            results.add(childPath)
                        }
                    }
                }
            }

            scan(path, 0)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun scanDirectories(
        path: Path,
        options: ScanOptions
    ): List<Path> = withContext(ioDispatcher) {
        scanDirectory(path, options.copy(fileFilter = { false }))
            .getOrNull() ?: emptyList()
    }

    override fun watchDirectory(
        path: Path,
        options: WatchOptions
    ): Flow<DirectoryChangeEvent> = channelFlow {
        val observer = object : FileObserver(path.toString()) {
            override fun onEvent(event: Int, pathStr: String?) {
                val childPath = pathStr?.let { path.resolve(it) } ?: return

                when (event and FileObserver.ALL_EVENTS) {
                    FileObserver.CREATE -> {
                        if (options.eventTypes.contains(WatchEventType.CREATE)) {
                            trySend(DirectoryChangeEvent.Created(childPath))
                        }
                    }
                    FileObserver.MODIFY -> {
                        if (options.eventTypes.contains(WatchEventType.MODIFY)) {
                            trySend(DirectoryChangeEvent.Modified(childPath))
                        }
                    }
                    FileObserver.DELETE -> {
                        if (options.eventTypes.contains(WatchEventType.DELETE)) {
                            trySend(DirectoryChangeEvent.Deleted(childPath))
                        }
                    }
                    FileObserver.MOVED_FROM, FileObserver.MOVED_TO -> {
                        if (options.eventTypes.contains(WatchEventType.MOVE)) {
                            trySend(DirectoryChangeEvent.Moved(childPath, childPath))
                        }
                    }
                }
            }
        }

        observer.startWatching()

        if (options.notifyOnInitialScan) {
            launch {
                scanDirectories(path, ScanOptions(recursive = options.recursive))
                    .forEach { childPath ->
                        send(DirectoryChangeEvent.Created(childPath))
                    }
            }
        }

        awaitClose {
            observer.stopWatching()
        }
    }.flowOn(ioDispatcher)

    override suspend fun scanByPattern(
        path: Path,
        pattern: Regex
    ): Result<List<Path>> = withContext(ioDispatcher) {
        try {
            val allPaths = scanDirectory(path).getOrNull() ?: emptyList()
            val matchedPaths = allPaths.filter { pattern.matches(it.name) }
            Result.success(matchedPaths)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDirectoryInfo(path: Path): Result<DirectoryInfo> = withContext(ioDispatcher) {
        try {
            var fileCount = 0
            var directoryCount = 0
            var totalSize = 0L
            var lastModified = 0L

            fun collectInfo(dir: Path) {
                context.contentResolver.query(
                    dir.toUri(),
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1)
                        val mimeType = cursor.getString(2)
                        val size = cursor.getLong(3)
                        val modified = cursor.getLong(4)

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            directoryCount++
                            collectInfo(dir.resolve(name))
                        } else {
                            fileCount++
                            totalSize += size
                        }
                        lastModified = maxOf(lastModified, modified)
                    }
                }
            }

            collectInfo(path)

            Result.success(
                DirectoryInfo(
                    path = path.toString(),
                    name = path.name,
                    fileCount = fileCount,
                    directoryCount = directoryCount,
                    totalSize = totalSize,
                    lastModified = lastModified,
                    isReadable = true, // TODO: 实现权限检查
                    isWritable = true
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFileTree(path: Path): Result<FileTreeNode> = withContext(ioDispatcher) {
        try {
            fun buildTree(dir: Path): FileTreeNode.DirectoryNode {
                val children = mutableListOf<FileTreeNode>()

                context.contentResolver.query(
                    dir.toUri(),
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0)
                        val mimeType = cursor.getString(1)
                        val size = cursor.getLong(2)
                        val modified = cursor.getLong(3)
                        val childPath = dir.resolve(name)

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            children.add(buildTree(childPath))
                        } else {
                            children.add(
                                FileTreeNode.FileNode(
                                    path = childPath,
                                    name = name,
                                    size = size,
                                    lastModified = modified
                                )
                            )
                        }
                    }
                }

                return FileTreeNode.DirectoryNode(
                    path = path,
                    name = path.name,
                    children = children
                )
            }

            Result.success(buildTree(path))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun Path.toUri(): Uri = Uri.parse(this.toString())
}
```

---

## 依赖关系

### 模块依赖

```
WorkspaceManager
    ├─→ WorkspaceFileManager
    ├─→ WorkspaceConfigManager
    ├─→ DirectoryScanner
    └─→ FileWatcher

WorkspaceConfigManager
    ├─→ WorkspaceFileManager
    └─→ ConfigValidator

DirectoryScanner
    └─→ WorkspaceFileManager
```

### 数据流

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        User Interaction                                 │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Workspace Manager                                │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  createWorkspace() → switchWorkspace() → deleteWorkspace()        │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        File Operations                                  │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  writeFile() → readFile() → watchFile() → createBackup()          │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Configuration                                   │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  readConfig() → writeConfig() → mergeConfig() → validateConfig()  │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Directory Scanning                              │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │  scanDirectory() → watchDirectory() → getFileTree()               │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 附录

### A. 配置文件示例

**agents.yaml**
```yaml
version: "1.0"
schema: "agents-v1"

agents:
  assistant:
    name: "Assistant"
    description: "General purpose assistant"
    capabilities:
      - conversation
      - information_retrieval
    tools:
      - web_search
      - calculator
    settings:
      max_tokens: 4096
      temperature: 0.7
```

**capabilities.yaml**
```yaml
version: "1.0"
schema: "capabilities-v1"

capabilities:
  conversation:
    name: "Conversation"
    description: "Natural language conversation"
    enabled: true
    parameters:
      max_tokens:
        type: "integer"
        description: "Maximum response tokens"
        required: false
        default: "2048"
```

### B. 工作区目录结构

```
MingClawWorkspace/
├── AGENTS.md              # 行为规则
├── MEMORY.md              # 长期记忆
├── CONFIG/
│   ├── agents.yaml
│   ├── capabilities.yaml
│   └── tools.yaml
├── SKILLS/
│   ├── web_search/
│   │   ├── skill.md
│   │   ├── schema.yaml
│   │   └── impl.kt
│   └── calculator/
│       ├── skill.md
│       ├── schema.yaml
│       └── impl.kt
├── EXPERIENCE/
│   ├── feedback/
│   ├── patterns/
│   └── metrics/
└── BACKUP/
    ├── AGENTS.md.backup.1234567890
    └── MEMORY.md.backup.1234567890
```

### C. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构设计
- [03-core-modules.md](./03-core-modules.md) - 核心模块设计
- [05-plugin-system.md](./05-plugin-system.md) - 插件系统设计

---

**文档维护**: 本文档应随着工作区管理功能演进持续更新
**审查周期**: 每月一次或重大功能变更时
