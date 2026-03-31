# MingClaw 插件系统设计

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [插件系统概述](#插件系统概述)
2. [插件接口](#插件接口)
3. [工具系统](#工具系统)
4. [插件加载器](#插件加载器)
5. [插件注册表](#插件注册表)
6. [依赖关系](#依赖关系)
7. [附录](#附录)

---

## 插件系统概述

### 设计目标

MingClaw 插件系统实现：

| 目标 | 说明 | 实现方式 |
|------|------|----------|
| **动态加载** | 运行时加载/卸载插件 | 反射 + 类加载器 |
| **隔离性** | 插件间相互隔离 | 独立 ClassLoader |
| **安全性** | 防止恶意插件 | 权限检查 + 沙箱 |
| **可发现性** | 自动发现插件 | 扫描 + 注解 |
| **可管理** | 统一管理插件生命周期 | 生命周期钩子 |

### 架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Plugin System Layer                           │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                      Plugin Orchestrator                          │  │
│  │  - 协调插件加载和卸载                                               │  │
│  │  - 管理插件依赖                                                    │  │
│  │  - 处理插件通信                                                    │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌───────────────┬─────────────────┼─────────────────┬─────────────────┐ │
│  │               │                 │                 │                 │ │
│  ▼               ▼                 ▼                 ▼                 │ │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ │
│  │  Plugin   │ │   Tool    │ │  Plugin   │ │Dependency │ │  Plugin   │ │
│  │  Loader   │ │ Registry  │ │ Registry  │ │ Resolver  │ │  Manager  │ │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Plugin Instances                              │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐  │
│  │   Plugin A   │ │   Plugin B   │ │   Plugin C   │ │   Plugin D   │  │
│  │  (WebTool)   │ │ (FileSystem) │ │ (Calculator) │ │ (DateTime)   │  │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 插件接口

### 核心插件接口

```kotlin
/**
 * MingClaw 插件基础接口
 *
 * 所有插件必须实现此接口
 */
interface MingClawPlugin {

    /**
     * 插件唯一标识符
     *
     * 格式: [category].[name]
     * 示例: tools.web_search
     */
    val pluginId: String

    /**
     * 插件版本
     */
    val version: Version

    /**
     * 插件名称
     */
    val name: String

    /**
     * 插件描述
     */
    val description: String

    /**
     * 插件作者
     */
    val author: String

    /**
     * 插件依赖
     */
    fun getDependencies(): List<PluginDependency>

    /**
     * 插件权限
     */
    fun getRequiredPermissions(): List<PluginPermission>

    /**
     * 初始化插件
     *
     * @param context 插件上下文
     * @return 初始化结果
     */
    suspend fun onInitialize(context: PluginContext): Result<Unit>

    /**
     * 启动插件
     */
    fun onStart()

    /**
     * 停止插件
     */
    fun onStop()

    /**
     * 清理插件资源
     */
    suspend fun onCleanup()

    /**
     * 获取插件提供的工具
     */
    fun getTools(): List<Tool>

    /**
     * 处理事件
     */
    fun handleEvent(event: Event): Result<Unit>
}
```

### 插件上下文

```kotlin
/**
 * 插件上下文
 *
 * 提供给插件的运行时环境
 */
class PluginContext(
    val pluginId: String,
    private val kernel: MingClawKernel,
    val config: PluginConfig
) {

    /**
     * 获取配置
     */
    fun <T> getConfig(key: String, default: T): T {
        return config.get(key, default)
    }

    /**
     * 订阅事件
     */
    fun subscribe(
        eventType: String,
        handler: EventHandler
    ): Subscription {
        return kernel.subscribe(eventType, handler)
    }

    /**
     * 发布事件
     */
    fun publish(event: Event): List<EventResult> {
        return kernel.publish(event)
    }

    /**
     * 调度任务
     */
    suspend fun dispatchTask(task: AgentTask): TaskResult {
        return kernel.dispatchTask(task)
    }

    /**
     * 获取其他插件
     */
    fun getPlugin(pluginId: String): MingClawPlugin? {
        return kernel.getPluginInfo(pluginId)
    }

    /**
     * 访问数据存储
     */
    fun getDataStore(): DataStore {
        return PluginDataStore(pluginId)
    }
}
```

### 插件元数据

```kotlin
/**
 * 插件元数据
 */
@Serializable
data class PluginMetadata(
    val pluginId: String,
    val version: String,
    val name: String,
    val description: String,
    val author: String,
    val category: PluginCategory,
    val permissions: List<String>,
    val dependencies: List<PluginDependency>,
    val entryPoint: String,
    val minKernelVersion: String,
    val checksum: String
)

/**
 * 插件类别
 */
enum class PluginCategory {
    Tool,
    Service,
    UI,
    Integration,
    Experimental
}

/**
 * 插件依赖
 */
@Serializable
data class PluginDependency(
    val pluginId: String,
    val minVersion: String,
    val maxVersion: String? = null,
    val required: Boolean = true
)

/**
 * 插件权限
 */
enum class PluginPermission(val description: String) {
    NetworkAccess("访问网络"),
    FileSystemRead("读取文件系统"),
    FileSystemWrite("写入文件系统"),
    CameraAccess("访问摄像头"),
    MicrophoneAccess("访问麦克风"),
    LocationAccess("访问位置信息"),
    ContactAccess("访问联系人"),
    NotificationAccess("显示通知"),
    BackgroundExecution("后台执行"),
    SystemSettings("修改系统设置"),
    SensitiveData("访问敏感数据"),
    PluginManagement("管理其他插件")
}
```

---

## 工具系统

### 核心工具接口

```kotlin
/**
 * 工具接口
 *
 * 定义可被 AI 助手调用的操作
 */
interface Tool {

    /**
     * 工具唯一标识符
     */
    val toolId: String

    /**
     * 工具名称
     */
    val name: String

    /**
     * 工具描述（给 AI 的提示）
     */
    val description: String

    /**
     * 工具类别
     */
    val category: ToolCategory

    /**
     * 参数定义
     */
    val parameters: Map<String, ToolParameter>

    /**
     * 是否需要确认
     */
    val requiresConfirmation: Boolean
        get() = false

    /**
     * 执行工具
     *
     * @param args 参数
     * @param context 执行上下文
     * @return 执行结果
     */
    suspend fun execute(
        args: Map<String, Any>,
        context: ToolContext
    ): ToolResult
}

/**
 * 工具类别
 */
enum class ToolCategory {
    Information,
    Action,
    Computation,
    Media,
    System,
    Custom
}

/**
 * 工具参数定义
 */
@Serializable
data class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = false,
    val default: Any? = null,
    val enum: List<Any>? = null,
    val format: String? = null
)

/**
 * 参数类型
 */
enum class ParameterType {
    String,
    Number,
    Integer,
    Boolean,
    Array,
    Object,
    Null
}

/**
 * 工具执行上下文
 */
class ToolContext(
    val sessionId: String,
    val userId: String,
    val pluginContext: PluginContext,
    val cancellationToken: CancellationToken
)

/**
 * 工具执行结果
 */
sealed interface ToolResult {
    data class Success(
        val data: Any,
        val format: ResultFormat = ResultFormat.Text
    ) : ToolResult

    data class Error(
        val message: String,
        val code: String? = null,
        val details: Map<String, Any>? = null
    ) : ToolResult

    data class Partial(
        val progress: Float,
        val message: String? = null,
        val data: Any? = null
    ) : ToolResult
}

/**
 * 结果格式
 */
enum class ResultFormat {
    Text,
    Json,
    Markdown,
    Html,
    Binary
}
```

### 工具注册表

```kotlin
/**
 * 工具注册表接口
 */
interface ToolRegistry {

    /**
     * 注册工具
     */
    fun registerTool(tool: Tool): Result<Unit>

    /**
     * 注销工具
     */
    fun unregisterTool(toolId: String): Result<Unit>

    /**
     * 获取工具
     */
    fun getTool(toolId: String): Tool?

    /**
     * 获取所有工具
     */
    fun getAllTools(): List<Tool>

    /**
     * 按类别获取工具
     */
    fun getToolsByCategory(category: ToolCategory): List<Tool>

    /**
     * 搜索工具
     */
    fun searchTools(query: String): List<Tool>

    /**
     * 执行工具
     */
    suspend fun executeTool(
        toolId: String,
        args: Map<String, Any>,
        context: ToolContext
    ): ToolResult
}
```

### 工具注册表实现

```kotlin
/**
 * 工具注册表实现
 */
@Singleton
internal class ToolRegistryImpl @Inject constructor(
    private val securityManager: SecurityManager,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : ToolRegistry {

    // 工具存储
    private val tools = mutableMapOf<String, Tool>()

    // 工具索引
    private val toolsByCategory = mutableMapOf<ToolCategory, MutableList<String>>()

    // 工具名称索引（用于搜索）
    private val toolNameIndex = mutableMapOf<String, String>()

    override fun registerTool(tool: Tool): Result<Unit> {
        return try {
            // 1. 验证工具
            validateTool(tool)
                .onFailure { error -> return Result.failure(error) }

            // 2. 检查权限
            if (!securityManager.checkToolPermission(tool.toolId)) {
                return Result.failure(
                    SecurityException("Tool permission denied: ${tool.toolId}")
                )
            }

            // 3. 注册工具
            tools[tool.toolId] = tool
            toolsByCategory.getOrPut(tool.category) { mutableListOf() }
                .add(tool.toolId)
            toolNameIndex[tool.name.lowercase()] = tool.toolId

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun unregisterTool(toolId: String): Result<Unit> {
        return try {
            val tool = tools[toolId]
                ?: return Result.failure(ToolNotFoundException(toolId))

            // 从索引中移除
            toolsByCategory[tool.category]?.remove(toolId)
            toolNameIndex.remove(tool.name.lowercase())

            // 移除工具
            tools.remove(toolId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getTool(toolId: String): Tool? {
        return tools[toolId]
    }

    override fun getAllTools(): List<Tool> {
        return tools.values.toList()
    }

    override fun getToolsByCategory(category: ToolCategory): List<Tool> {
        return toolsByCategory[category]?.mapNotNull { tools[it] }
            ?: emptyList()
    }

    override fun searchTools(query: String): List<Tool> {
        val lowerQuery = query.lowercase()
        return tools.values.filter { tool ->
            tool.name.lowercase().contains(lowerQuery) ||
            tool.description.lowercase().contains(lowerQuery) ||
            tool.toolId.lowercase().contains(lowerQuery)
        }
    }

    override suspend fun executeTool(
        toolId: String,
        args: Map<String, Any>,
        context: ToolContext
    ): ToolResult = withContext(ioDispatcher) {
        val tool = tools[toolId]
            ?: return@withContext ToolResult.Error(
                message = "Tool not found: $toolId",
                code = "TOOL_NOT_FOUND"
            )

        try {
            // 验证参数
            val validationResult = validateParameters(tool, args)
            if (!validationResult.isValid) {
                return@withContext ToolResult.Error(
                    message = "Invalid parameters: ${validationResult.errors.joinToString()}",
                    code = "INVALID_PARAMETERS",
                    details = mapOf("errors" to validationResult.errors)
                )
            }

            // 检查是否需要确认
            if (tool.requiresConfirmation) {
                val confirmed = requestConfirmation(tool, args)
                if (!confirmed) {
                    return@withContext ToolResult.Error(
                        message = "Tool execution cancelled by user",
                        code = "CANCELLED"
                    )
                }
            }

            // 执行工具
            tool.execute(args, context)
        } catch (e: CancellationException) {
            ToolResult.Error(
                message = "Tool execution cancelled",
                code = "CANCELLED"
            )
        } catch (e: Exception) {
            ToolResult.Error(
                message = "Tool execution failed: ${e.message}",
                code = "EXECUTION_ERROR",
                details = mapOf("exception" to e.stackTraceToString())
            )
        }
    }

    // 私有辅助方法
    private fun validateTool(tool: Tool): Result<Unit> {
        // 验证工具 ID 格式
        if (!tool.toolId.matches(Regex("[a-z0-9_]+"))) {
            return Result.failure(
                ValidationException("Invalid tool ID format")
            )
        }

        // 验证参数定义
        for (param in tool.parameters.values) {
            if (param.required && param.default != null) {
                return Result.failure(
                    ValidationException("Required parameter ${param.name} cannot have default value")
                )
            }
        }

        return Result.success(Unit)
    }

    private fun validateParameters(
        tool: Tool,
        args: Map<String, Any>
    ): ValidationResult {
        val errors = mutableListOf<String>()

        // 检查必需参数
        for ((name, param) in tool.parameters) {
            if (param.required && !args.containsKey(name)) {
                errors.add("Missing required parameter: $name")
            }

            // 检查参数类型
            args[name]?.let { value ->
                if (!isTypeValid(value, param.type)) {
                    errors.add("Parameter $name has invalid type")
                }
            }
        }

        // 检查未知参数
        for (name in args.keys) {
            if (!tool.parameters.containsKey(name)) {
                errors.add("Unknown parameter: $name")
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    private fun isTypeValid(value: Any, type: ParameterType): Boolean {
        return when (type) {
            ParameterType.String -> value is String
            ParameterType.Number -> value is Number
            ParameterType.Integer -> value is Int || value is Long
            ParameterType.Boolean -> value is Boolean
            ParameterType.Array -> value is List<*>
            ParameterType.Object -> value is Map<*, *>
            ParameterType.Null -> value == null
        }
    }

    private suspend fun requestConfirmation(
        tool: Tool,
        args: Map<String, Any>
    ): Boolean {
        // 通过事件总线请求用户确认
        // 实现略
        return true
    }
}
```

---

## 插件加载器

### 核心接口

```kotlin
/**
 * 插件加载器接口
 */
interface PluginLoader {

    /**
     * 从文件加载插件
     *
     * @param file 插件文件
     * @return 加载结果
     */
    suspend fun loadFromFile(file: File): Result<MingClawPlugin>

    /**
     * 从 URL 加载插件
     *
     * @param url 插件 URL
     * @return 加载结果
     */
    suspend fun loadFromUrl(url: String): Result<MingClawPlugin>

    /**
     * 从目录加载插件
     *
     * @param directory 插件目录
     * @return 加载结果
     */
    suspend fun loadFromDirectory(directory: File): Result<List<MingClawPlugin>>

    /**
     * 验证插件
     *
     * @param plugin 插件
     * @return 验证结果
     */
    fun validatePlugin(plugin: MingClawPlugin): ValidationResult

    /**
     * 提取插件元数据
     *
     * @param file 插件文件
     * @return 元数据
     */
    suspend fun extractMetadata(file: File): Result<PluginMetadata>
}
```

### 实现类

```kotlin
/**
 * 插件加载器实现
 */
@Singleton
internal class PluginLoaderImpl @Inject constructor(
    private val securityManager: SecurityManager,
    private val verifier: PluginVerifier,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : PluginLoader {

    // 插件 ClassLoader 缓存
    private val classLoaders = mutableMapOf<String, PluginClassLoader>()

    // 已加载的插件类
    private val loadedClasses = mutableMapOf<String, Class<out MingClawPlugin>>()

    override suspend fun loadFromFile(file: File): Result<MingClawPlugin> {
        return withContext(ioDispatcher) {
            try {
                // 1. 验证文件
                if (!file.exists()) {
                    return@withContext Result.failure(
                        PluginFileNotFoundException(file.path)
                    )
                }

                // 2. 验证签名
                val signatureValid = verifier.verifySignature(file)
                if (!signatureValid) {
                    return@withContext Result.failure(
                        SecurityException("Plugin signature verification failed")
                    )
                }

                // 3. 提取元数据
                val metadata = extractMetadata(file).getOrThrow()

                // 4. 创建独立的 ClassLoader
                val classLoader = createClassLoader(file)
                classLoaders[metadata.pluginId] = classLoader

                // 5. 加载插件类
                val pluginClass = classLoader.loadClass(metadata.entryPoint)
                    .asSubclass(MingClawPlugin::class.java)

                // 6. 实例化插件
                val plugin = pluginClass.getDeclaredConstructor().newInstance()

                // 7. 验证插件
                val validation = validatePlugin(plugin)
                if (!validation.isValid) {
                    return@withContext Result.failure(
                        PluginValidationException(validation.errors.joinToString())
                    )
                }

                // 8. 缓存
                loadedClasses[metadata.pluginId] = pluginClass

                Result.success(plugin)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun loadFromUrl(url: String): Result<MingClawPlugin> {
        return withContext(ioDispatcher) {
            try {
                // 1. 下载插件
                val tempFile = downloadPlugin(url)

                // 2. 从文件加载
                loadFromFile(tempFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun loadFromDirectory(
        directory: File
    ): Result<List<MingClawPlugin>> = withContext(ioDispatcher) {
        try {
            if (!directory.isDirectory) {
                return@withContext Result.failure(
                    IllegalArgumentException("Not a directory: ${directory.path}")
                )
            }

            // 查找所有插件文件
            val pluginFiles = directory.listFiles { file ->
                file.extension == "jar" || file.extension == "apk"
            } ?: emptyArray()

            // 加载所有插件
            val plugins = mutableListOf<MingClawPlugin>()
            val errors = mutableListOf<Exception>()

            for (file in pluginFiles) {
                loadFromFile(file)
                    .onSuccess { plugins.add(it) }
                    .onFailure { errors.add(it as Exception) }
            }

            if (plugins.isEmpty() && errors.isNotEmpty()) {
                Result.failure(
                    PluginLoadException(
                        "Failed to load any plugin. Errors: ${errors.map { it.message }}"
                    )
                )
            } else {
                Result.success(plugins)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun validatePlugin(plugin: MingClawPlugin): ValidationResult {
        val errors = mutableListOf<String>()

        // 验证插件 ID
        if (!plugin.pluginId.matches(Regex("[a-z0-9_]+"))) {
            errors.add("Invalid plugin ID format")
        }

        // 验证版本
        if (plugin.version == Version.UNKNOWN) {
            errors.add("Plugin version is required")
        }

        // 验证名称
        if (plugin.name.isBlank()) {
            errors.add("Plugin name cannot be blank")
        }

        // 验证描述
        if (plugin.description.isBlank()) {
            errors.add("Plugin description cannot be blank")
        }

        // 验证工具
        val tools = plugin.getTools()
        for (tool in tools) {
            val toolValidation = validateTool(tool)
            if (!toolValidation.isValid) {
                errors.addAll(toolValidation.errors.map { "Tool ${tool.toolId}: $it" })
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    override suspend fun extractMetadata(file: File): Result<PluginMetadata> {
        return withContext(ioDispatcher) {
            try {
                // 从 JAR 文件中提取 plugin.json
                val jarFile = JarFile(file)
                val entry = jarFile.getJarEntry("plugin.json")
                    ?: return@withContext Result.failure(
                        PluginMetadataNotFoundException("plugin.json not found")
                    )

                val content = jarFile.getInputStream(entry).bufferedReader().readText()
                val metadata = Json.decodeFromString<PluginMetadata>(content)

                Result.success(metadata)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // 私有辅助方法
    private fun createClassLoader(file: File): PluginClassLoader {
        // 创建独立的 ClassLoader 以实现隔离
        return PluginClassLoader(
            jarFile = file,
            parent = MingClawPlugin::class.java.classLoader
        )
    }

    private suspend fun downloadPlugin(url: String): File {
        // 下载插件到临时文件
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()

        val response = client.newCall(request).execute()
        val tempFile = File.createTempFile("plugin", ".jar")

        response.body?.byteStream()?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }

    private fun validateTool(tool: Tool): ValidationResult {
        val errors = mutableListOf<String>()

        if (tool.toolId.isBlank()) {
            errors.add("Tool ID cannot be blank")
        }

        if (tool.name.isBlank()) {
            errors.add("Tool name cannot be blank")
        }

        if (tool.description.isBlank()) {
            errors.add("Tool description cannot be blank")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }
}

/**
 * 插件 ClassLoader
 *
 * 实现插件隔离
 */
internal class PluginClassLoader(
    private val jarFile: File,
    parent: ClassLoader
) : URLClassLoader(arrayOf(jarFile.toURI().toURL()), parent) {

    private val pluginClasses = mutableSetOf<String>()

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // 检查是否已加载
        findLoadedClass(name)?.let { return it }

        // 插件类优先从此 ClassLoader 加载
        if (isPluginClass(name)) {
            return findClass(name).also { pluginClasses.add(name) }
        }

        // 其他类使用父加载器
        return super.loadClass(name, resolve)
    }

    private fun isPluginClass(name: String): Boolean {
        return name.startsWith("com.loy.mingclaw.plugin.")
    }

    fun cleanup() {
        pluginClasses.clear()
        close()
    }
}
```

---

## 插件注册表

### 核心接口

```kotlin
/**
 * 插件注册表接口
 */
interface PluginRegistry {

    /**
     * 注册插件
     */
    suspend fun registerPlugin(plugin: MingClawPlugin): Result<Unit>

    /**
     * 注销插件
     */
    suspend fun unregisterPlugin(pluginId: String): Result<Unit>

    /**
     * 获取插件
     */
    fun getPlugin(pluginId: String): MingClawPlugin?

    /**
     * 获取所有插件
     */
    fun getAllPlugins(): List<MingClawPlugin>

    /**
     * 按类别获取插件
     */
    fun getPluginsByCategory(category: PluginCategory): List<MingClawPlugin>

    /**
     * 获取插件信息
     */
    fun getPluginInfo(pluginId: String): PluginInfo?

    /**
     * 搜索插件
     */
    fun searchPlugins(query: String): List<MingClawPlugin>

    /**
     * 获取可用工具
     */
    fun getAvailableTools(sessionId: String): List<Tool>

    /**
     * 获取插件状态
     */
    fun getPluginStatus(pluginId: String): PluginStatus?
}
```

### 实现类

```kotlin
/**
 * 插件注册表实现
 */
@Singleton
internal class PluginRegistryImpl @Inject constructor(
    private val pluginLoader: PluginLoader,
    private val securityManager: SecurityManager,
    private val toolRegistry: ToolRegistry,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : PluginRegistry {

    // 插件存储
    private val plugins = mutableMapOf<String, MingClawPlugin>()

    // 插件元数据
    private val pluginMetadata = mutableMapOf<String, PluginMetadata>()

    // 插件状态
    private val pluginStatus = mutableMapOf<String, PluginStatus>()

    override suspend fun registerPlugin(
        plugin: MingClawPlugin
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val pluginId = plugin.pluginId

            // 1. 验证插件
            val validation = pluginLoader.validatePlugin(plugin)
            if (!validation.isValid) {
                return@withContext Result.failure(
                    PluginValidationException(validation.errors.joinToString())
                )
            }

            // 2. 检查安全
            if (!securityManager.isPluginSafe(plugin)) {
                return@withContext Result.failure(
                    SecurityException("Plugin security check failed")
                )
            }

            // 3. 检查依赖
            val dependencyResult = checkDependencies(plugin)
            if (!dependencyResult.isValid) {
                return@withContext Result.failure(
                    DependencyException(dependencyResult.errors.joinToString())
                )
            }

            // 4. 注册插件
            plugins[pluginId] = plugin
            pluginStatus[pluginId] = PluginStatus.Registered

            // 5. 注册工具
            for (tool in plugin.getTools()) {
                toolRegistry.registerTool(tool)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unregisterPlugin(pluginId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val plugin = plugins[pluginId]
                    ?: return@withContext Result.failure(
                        PluginNotRegisteredException(pluginId)
                    )

                // 检查是否有其他插件依赖此插件
                val dependents = findDependents(pluginId)
                if (dependents.isNotEmpty()) {
                    return@withContext Result.failure(
                        DependencyException(
                            "Cannot unregister plugin. " +
                            "Dependents: ${dependents.joinToString()}"
                        )
                    )
                }

                // 注销工具
                for (tool in plugin.getTools()) {
                    toolRegistry.unregisterTool(tool.toolId)
                }

                // 移除插件
                plugins.remove(pluginId)
                pluginMetadata.remove(pluginId)
                pluginStatus.remove(pluginId)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun getPlugin(pluginId: String): MingClawPlugin? {
        return plugins[pluginId]
    }

    override fun getAllPlugins(): List<MingClawPlugin> {
        return plugins.values.toList()
    }

    override fun getPluginsByCategory(
        category: PluginCategory
    ): List<MingClawPlugin> {
        return plugins.values.filter { plugin ->
            pluginMetadata[plugin.pluginId]?.category == category
        }
    }

    override fun getPluginInfo(pluginId: String): PluginInfo? {
        val plugin = plugins[pluginId] ?: return null
        val metadata = pluginMetadata[pluginId]
        val status = pluginStatus[pluginId]

        return PluginInfo(
            pluginId = plugin.pluginId,
            version = plugin.version,
            name = plugin.name,
            description = plugin.description,
            author = plugin.author,
            category = metadata?.category ?: PluginCategory.Tool,
            status = status ?: PluginStatus.Unknown,
            permissions = plugin.getRequiredPermissions(),
            dependencies = plugin.getDependencies(),
            tools = plugin.getTools().map { it.toolId }
        )
    }

    override fun searchPlugins(query: String): List<MingClawPlugin> {
        val lowerQuery = query.lowercase()
        return plugins.values.filter { plugin ->
            plugin.name.lowercase().contains(lowerQuery) ||
            plugin.description.lowercase().contains(lowerQuery) ||
            plugin.pluginId.lowercase().contains(lowerQuery)
        }
    }

    override fun getAvailableTools(sessionId: String): List<Tool> {
        // 获取所有已加载插件的工具
        return plugins.values
            .filter { pluginStatus[it.pluginId] == PluginStatus.Running }
            .flatMap { it.getTools() }
    }

    override fun getPluginStatus(pluginId: String): PluginStatus? {
        return pluginStatus[pluginId]
    }

    // 私有辅助方法
    private fun checkDependencies(
        plugin: MingClawPlugin
    ): ValidationResult {
        val errors = mutableListOf<String>()

        for (dependency in plugin.getDependencies()) {
            val depPlugin = plugins[dependency.pluginId]
            if (depPlugin == null) {
                if (dependency.required) {
                    errors.add("Missing required dependency: ${dependency.pluginId}")
                }
            } else {
                // 检查版本
                val depVersion = depPlugin.version
                val minVersion = Version.parse(dependency.minVersion)
                if (depVersion < minVersion) {
                    errors.add(
                        "Dependency ${dependency.pluginId} version " +
                        "$depVersion is below minimum $minVersion"
                    )
                }

                dependency.maxVersion?.let { maxVer ->
                    val maxVersion = Version.parse(maxVer)
                    if (depVersion > maxVersion) {
                        errors.add(
                            "Dependency ${dependency.pluginId} version " +
                            "$depVersion exceeds maximum $maxVersion"
                        )
                    }
                }
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    private fun findDependents(pluginId: String): List<String> {
        return plugins.entries
            .filter { (_, plugin) ->
                plugin.getDependencies().any {
                    it.pluginId == pluginId
                }
            }
            .map { it.key }
    }
}

/**
 * 插件状态
 */
enum class PluginStatus {
    Unknown,
    Registered,
    Loading,
    Running,
    Stopped,
    Error,
    Unregistered
}

/**
 * 插件信息
 */
@Serializable
data class PluginInfo(
    val pluginId: String,
    val version: Version,
    val name: String,
    val description: String,
    val author: String,
    val category: PluginCategory,
    val status: PluginStatus,
    val permissions: List<PluginPermission>,
    val dependencies: List<PluginDependency>,
    val tools: List<String>
)
```

---

## 依赖关系

### 模块依赖图

```
┌─────────────────────────────────────────────────────────────────┐
│                      Plugin Orchestrator                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Plugin     │  │    Tool      │  │  Dependency  │         │
│  │   Loader     │  │   Registry   │  │   Resolver   │         │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
│         │                  │                  │                  │
└─────────┼──────────────────┼──────────────────┼──────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Shared Dependencies                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Security   │  │    Plugin    │  │     Event    │         │
│  │   Manager    │  │   Verifier   │  │     Bus      │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

### 依赖说明

| 模块 | 依赖 | 说明 |
|------|------|------|
| **PluginLoader** | SecurityManager, PluginVerifier | 加载和验证插件 |
| **ToolRegistry** | SecurityManager | 权限检查 |
| **PluginRegistry** | PluginLoader, ToolRegistry, SecurityManager | 插件生命周期管理 |

---

## 附录

### A. 插件示例

```kotlin
/**
 * 简单计算器插件示例
 */
class CalculatorPlugin : MingClawPlugin {

    override val pluginId = "tools.calculator"
    override val version = Version(1, 0, 0)
    override val name = "Calculator"
    override val description = "Basic calculator tool"
    override val author = "MingClaw Team"

    private lateinit var context: PluginContext

    override fun getDependencies() = emptyList<PluginDependency>()

    override fun getRequiredPermissions() = emptyList<PluginPermission>()

    override suspend fun onInitialize(context: PluginContext): Result<Unit> {
        this.context = context
        return Result.success(Unit)
    }

    override fun onStart() {
        context.publish(Event.PluginLoaded(pluginId))
    }

    override fun onStop() {
        context.publish(Event.PluginUnloaded(pluginId))
    }

    override suspend fun onCleanup() {
        // 清理资源
    }

    override fun getTools() = listOf(
        AddTool(),
        SubtractTool(),
        MultiplyTool(),
        DivideTool()
    )

    override fun handleEvent(event: Event): Result<Unit> {
        return Result.success(Unit)
    }
}

/**
 * 加法工具
 */
class AddTool : Tool {

    override val toolId = "calculator.add"
    override val name = "Add"
    override val description = "Adds two numbers together"
    override val category = ToolCategory.Computation

    override val parameters = mapOf(
        "a" to ToolParameter(
            name = "a",
            type = ParameterType.Number,
            description = "First number",
            required = true
        ),
        "b" to ToolParameter(
            name = "b",
            type = ParameterType.Number,
            description = "Second number",
            required = true
        )
    )

    override suspend fun execute(
        args: Map<String, Any>,
        context: ToolContext
    ): ToolResult {
        val a = args["a"] as Number
        val b = args["b"] as Number

        val result = a.toDouble() + b.toDouble()

        return ToolResult.Success(
            data = mapOf(
                "result" to result
            ),
            format = ResultFormat.Json
        )
    }
}
```

### B. plugin.json 示例

```json
{
  "pluginId": "tools.calculator",
  "version": "1.0.0",
  "name": "Calculator",
  "description": "Basic calculator tool",
  "author": "MingClaw Team",
  "category": "Tool",
  "permissions": [],
  "dependencies": [],
  "entryPoint": "com.loy.mingclaw.plugin.CalculatorPlugin",
  "minKernelVersion": "1.0.0",
  "checksum": "sha256:abc123..."
}
```

### C. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构设计
- [03-core-modules.md](./03-core-modules.md) - 核心模块设计
- [07-task-orchestration.md](./07-task-orchestration.md) - 任务编排引擎

---

**文档维护**: 本文档应随着插件系统的实现持续更新
**审查周期**: 每两周一次或重大变更时
