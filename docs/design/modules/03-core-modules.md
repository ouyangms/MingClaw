# MingClaw 核心模块设计

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [核心模块概述](#核心模块概述)
2. [微内核实现](#微内核实现)
3. [动态提示构建器](#动态提示构建器)
4. [事件总线](#事件总线)
5. [配置管理](#配置管理)
6. [安全模块](#安全模块)
7. [依赖关系](#依赖关系)
8. [附录](#附录)

---

## 核心模块概述

### 设计目标

MingClaw 核心模块提供系统的基础能力，确保：

| 目标 | 说明 | 实现方式 |
|------|------|----------|
| **稳定性** | 核心功能可靠运行 | 完善的错误处理和恢复机制 |
| **可扩展性** | 支持插件动态加载 | 微内核架构 |
| **高性能** | 低延迟响应 | 异步处理和缓存优化 |
| **安全性** | 保护用户数据和系统安全 | 多层安全验证 |

### 核心模块架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Core Modules Layer                             │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                        Microkernel                                │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │  │
│  │  │   Plugin     │ │    Event     │ │    Task      │             │  │
│  │  │  Registry    │ │    Bus       │ │ Dispatcher   │             │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘             │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌─────────────────────────────────▼─────────────────────────────────┐  │
│  │                      Core Services                                 │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐             │  │
│  │  │   Dynamic    │ │    Config    │ │   Security   │             │  │
│  │  │   Prompt     │ │   Manager    │ │   Manager    │             │  │
│  │  │   Builder    │ │              │ │              │             │  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘             │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 微内核实现

### 核心接口

```kotlin
/**
 * MingClaw 微内核核心接口
 *
 * 职责：
 * - 管理插件生命周期
 * - 分发任务到合适的处理器
 * - 协调系统各组件
 */
interface MingClawKernel {

    /**
     * 插件管理
     */
    suspend fun loadPlugin(pluginId: String): Result<PluginContext>
    suspend fun unloadPlugin(pluginId: String): Result<Unit>
    suspend fun reloadPlugin(pluginId: String): Result<PluginContext>
    fun getLoadedPlugins(): List<PluginInfo>
    fun getPluginInfo(pluginId: String): PluginInfo?

    /**
     * 任务调度
     */
    suspend fun dispatchTask(task: AgentTask): TaskResult
    fun scheduleRecurringTask(task: ScheduledTask): CancellableTask
    fun scheduleDelayedTask(task: AgentTask, delay: Duration): CancellableTask
    fun cancelTask(taskId: String): Result<Unit>

    /**
     * 事件订阅
     */
    fun subscribe(eventType: String, handler: EventHandler): Subscription
    fun subscribeOnce(eventType: String, handler: EventHandler): Subscription
    fun unsubscribe(subscriptionId: String): Result<Unit>
    fun publish(event: Event): List<EventResult>
    fun publishAsync(event: Event): Job

    /**
     * 配置管理
     */
    fun getConfig(): KernelConfig
    fun updateConfig(updates: ConfigUpdates): Result<KernelConfig>
    fun watchConfigChanges(): Flow<KernelConfig>

    /**
     * 系统状态
     */
    fun getSystemStatus(): SystemStatus
    fun getHealthCheck(): HealthCheckResult
    fun shutdown(): Result<Unit>
}
```

### 微内核实现

```kotlin
/**
 * 微内核实现类
 *
 * @param pluginRegistry 插件注册表
 * @param eventBus 事件总线
 * @param taskDispatcher 任务分发器
 * @param configManager 配置管理器
 * @param securityManager 安全管理器
 */
@Singleton
internal class MingClawKernelImpl @Inject constructor(
    private val pluginRegistry: PluginRegistry,
    private val eventBus: EventBus,
    private val taskDispatcher: TaskDispatcher,
    private val configManager: KernelConfigManager,
    private val securityManager: SecurityManager,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : MingClawKernel {

    // 插件状态管理
    private val loadedPlugins = mutableMapOf<String, PluginContext>()
    private val pluginStates = mutableMapOf<String, PluginState>()

    // 任务管理
    private val scheduledTasks = mutableMapOf<String, CancellableTask>()
    private val taskScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // 系统状态
    private val _systemStatus = MutableStateFlow(SystemStatus.Initializing)
    override val systemStatus: StateFlow<SystemStatus> = _systemStatus.asStateFlow()

    // 初始化
    init {
        taskScope.launch {
            initializeCore()
        }
    }

    private suspend fun initializeCore() {
        _systemStatus.value = SystemStatus.Initializing

        // 1. 加载配置
        configManager.loadConfig()

        // 2. 初始化安全模块
        securityManager.initialize()

        // 3. 加载核心插件
        loadCorePlugins()

        // 4. 启动定期任务
        startMaintenanceTasks()

        _systemStatus.value = SystemStatus.Running
    }

    override suspend fun loadPlugin(pluginId: String): Result<PluginContext> {
        return withContext(ioDispatcher) {
            // 1. 检查权限
            if (!securityManager.checkPluginPermission(pluginId)) {
                return@withContext Result.failure(
                    SecurityException("Plugin permission denied: $pluginId")
                )
            }

            // 2. 检查依赖
            val plugin = pluginRegistry.getPlugin(pluginId)
                ?: return@withContext Result.failure(
                    PluginNotFoundException(pluginId)
                )

            val dependencies = plugin.getDependencies()
            for (dependency in dependencies) {
                if (!loadedPlugins.containsKey(dependency.pluginId)) {
                    // 递归加载依赖
                    loadPlugin(dependency.pluginId)
                        .onFailure { error ->
                            return@withContext Result.failure(
                                DependencyLoadException(
                                    "Failed to load dependency: ${dependency.pluginId}",
                                    error
                                )
                            )
                        }
                }
            }

            // 3. 创建插件上下文
            val context = PluginContext(
                pluginId = pluginId,
                kernel = this@MingClawKernelImpl,
                config = configManager.getPluginConfig(pluginId)
            )

            // 4. 初始化插件
            val initResult = plugin.onInitialize(context)

            initResult.mapCatching {
                // 5. 注册到插件管理器
                loadedPlugins[pluginId] = context
                pluginStates[pluginId] = PluginState.Running

                // 6. 发布加载事件
                eventBus.publish(Event.PluginLoaded(pluginId))

                // 7. 启动插件
                plugin.onStart()

                context
            }
        }
    }

    override suspend fun unloadPlugin(pluginId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            val context = loadedPlugins[pluginId]
                ?: return@withContext Result.failure(
                    PluginNotLoadedException(pluginId)
                )

            // 1. 检查是否有其他插件依赖此插件
            val dependents = findDependentPlugins(pluginId)
            if (dependents.isNotEmpty()) {
                return@withContext Result.failure(
                    DependencyException(
                        "Cannot unload plugin $pluginId. " +
                        "Dependents: ${dependents.joinToString()}"
                    )
                )
            }

            // 2. 停止插件
            val plugin = pluginRegistry.getPlugin(pluginId)
            plugin?.onStop()

            // 3. 清理资源
            plugin?.onCleanup()

            // 4. 更新状态
            loadedPlugins.remove(pluginId)
            pluginStates[pluginId] = PluginState.Unloaded

            // 5. 发布卸载事件
            eventBus.publish(Event.PluginUnloaded(pluginId))

            Result.success(Unit)
        }
    }

    override suspend fun dispatchTask(task: AgentTask): TaskResult {
        // 1. 验证任务
        val validationResult = taskValidator.validate(task)
        if (!validationResult.isValid) {
            return TaskResult.Failure(
                ValidationError(validationResult.errors)
            )
        }

        // 2. 记录任务开始
        eventBus.publish(Event.TaskStarted(task.id))

        // 3. 查找处理器
        val handler = taskDispatcher.findHandler(task.type)
            ?: return TaskResult.Failure(
                HandlerNotFoundException(task.type)
            )

        // 4. 执行任务
        return try {
            val result = handler.execute(task)

            // 5. 发布完成事件
            when (result) {
                is TaskResult.Success -> {
                    eventBus.publish(Event.TaskCompleted(task.id, result.data))
                }
                is TaskResult.Failure -> {
                    eventBus.publish(Event.TaskFailed(task.id, result.error))
                }
                is TaskResult.Partial -> {
                    eventBus.publish(Event.TaskProgress(task.id, result.progress))
                }
            }

            result
        } catch (e: Exception) {
            val error = TaskExecutionException(
                "Task execution failed", e
            )
            eventBus.publish(Event.TaskFailed(task.id, error.message ?: "Unknown error"))
            TaskResult.Failure(error)
        }
    }

    override fun scheduleRecurringTask(task: ScheduledTask): CancellableTask {
        val job = taskScope.launch {
            while (isActive) {
                try {
                    dispatchTask(task.task)
                } catch (e: Exception) {
                    // 记录错误但继续调度
                    eventBus.publish(Event.TaskError(task.task.id, e))
                }
                delay(task.interval)
            }
        }

        val cancellableTask = CancellableTask(
            id = UUID.randomUUID().toString(),
            cancel = { job.cancel() }
        )

        scheduledTasks[cancellableTask.id] = cancellableTask
        return cancellableTask
    }

    override fun subscribe(
        eventType: String,
        handler: EventHandler
    ): Subscription {
        return eventBus.subscribe(eventType, handler)
    }

    override fun publish(event: Event): List<EventResult> {
        return eventBus.publish(event)
    }

    override fun getConfig(): KernelConfig {
        return configManager.getConfig()
    }

    override fun updateConfig(updates: ConfigUpdates): Result<KernelConfig> {
        return configManager.updateConfig(updates)
            .also { result ->
                result.onSuccess { config ->
                    eventBus.publish(Event.ConfigUpdated(config))
                }
            }
    }

    override fun getSystemStatus(): SystemStatus {
        return _systemStatus.value
    }

    override fun getHealthCheck(): HealthCheckResult {
        return HealthCheckResult(
            status = _systemStatus.value,
            components = mapOf(
                "plugins" to checkPluginHealth(),
                "tasks" to checkTaskHealth(),
                "events" to checkEventBusHealth()
            )
        )
    }

    override suspend fun shutdown(): Result<Unit> {
        return withContext(ioDispatcher) {
            _systemStatus.value = SystemStatus.ShuttingDown

            // 1. 取消所有计划任务
            scheduledTasks.values.forEach { it.cancel() }
            scheduledTasks.clear()

            // 2. 卸载所有插件
            loadedPlugins.keys.toList().forEach { pluginId ->
                unloadPlugin(pluginId)
            }

            // 3. 保存配置
            configManager.saveConfig()

            // 4. 关闭事件总线
            eventBus.shutdown()

            _systemStatus.value = SystemStatus.Shutdown
            Result.success(Unit)
        }
    }

    // 私有辅助方法
    private fun findDependentPlugins(pluginId: String): List<String> {
        return loadedPlugins.entries
            .filter { (_, context) ->
                val plugin = pluginRegistry.getPlugin(context.pluginId)
                plugin?.getDependencies()?.any { it.pluginId == pluginId } == true
            }
            .map { it.key }
    }

    private suspend fun loadCorePlugins() {
        val corePlugins = listOf(
            "context-manager",
            "memory-manager",
            "plugin-loader"
        )

        corePlugins.forEach { pluginId ->
            loadPlugin(pluginId)
                .onFailure { error ->
                    Log.e("MingClawKernel", "Failed to load core plugin: $pluginId", error)
                }
        }
    }

    private fun startMaintenanceTasks() {
        // 定期健康检查
        scheduleRecurringTask(
            ScheduledTask(
                id = "health-check",
                task = AgentTask(
                    id = "health-check-task",
                    type = "system.health"
                ),
                interval = 5.minutes
            )
        )

        // 定期配置同步
        scheduleRecurringTask(
            ScheduledTask(
                id = "config-sync",
                task = AgentTask(
                    id = "config-sync-task",
                    type = "system.config-sync"
                ),
                interval = 1.hours
            )
        )
    }
}
```

---

## 动态提示构建器

### 核心接口

```kotlin
/**
 * 动态提示构建器接口
 *
 * 职责：
 * - 从多个源收集上下文
 * - 智能组装提示词
 * - 管理 Token 预算
 */
interface DynamicPromptBuilder {

    /**
     * 构建完整提示
     *
     * @param request 用户请求
     * @param context 构建上下文
     * @return 构建结果
     */
    suspend fun buildPrompt(
        request: UserRequest,
        context: BuildContext
    ): Result<PromptBuildResult>

    /**
     * 估算 Token 使用
     */
    fun estimateTokens(content: String): Int

    /**
     * 获取提示模板
     */
    fun getPromptTemplate(templateName: String): PromptTemplate?

    /**
     * 注册提示片段
     */
    fun registerPromptFragment(fragment: PromptFragment): Result<Unit>
}
```

### 实现类

```kotlin
/**
 * 动态提示构建器实现
 */
@Singleton
internal class DynamicPromptBuilderImpl @Inject constructor(
    private val contextManager: SessionContextManager,
    private val memoryManager: MemoryContextManager,
    private val windowManager: ContextWindowManager,
    private val configManager: KernelConfigManager,
    private val pluginRegistry: PluginRegistry
) : DynamicPromptBuilder {

    // 提示片段注册表
    private val promptFragments = mutableMapOf<String, PromptFragment>()

    // 提示模板缓存
    private val templateCache = mutableMapOf<String, PromptTemplate>()

    // Token 估算器
    private val tokenEstimator: TokenEstimator = TokenEstimatorImpl()

    override suspend fun buildPrompt(
        request: UserRequest,
        context: BuildContext
    ): Result<PromptBuildResult> = withContext(Dispatchers.Default) {

        // 1. 获取 Token 预算
        val budget = windowManager.calculateTokenBudget(request)

        // 2. 收集系统提示
        val systemPrompt = buildSystemPrompt(context, budget)

        // 3. 收集会话上下文
        val sessionContext = contextManager.getSessionContext(context.sessionId)

        // 4. 检索相关记忆
        val memoryContext = memoryManager.retrieveRelevantMemories(
            query = request.content,
            maxTokens = budget.memoryTokens
        )

        // 5. 收集工具描述
        val toolsContext = buildToolsContext(context, budget)

        // 6. 构建完整提示
        val fullPrompt = assemblePrompt(
            system = systemPrompt,
            session = sessionContext,
            memory = memoryContext,
            tools = toolsContext,
            userRequest = request
        )

        // 7. 验证 Token 限制
        val estimatedTokens = tokenEstimator.estimate(fullPrompt.content)
        if (estimatedTokens > budget.totalTokens) {
            // 尝试压缩
            val compressed = compressPrompt(fullPrompt, budget)
            return@withContext Result.success(compressed)
        }

        Result.success(fullPrompt)
    }

    private suspend fun buildSystemPrompt(
        context: BuildContext,
        budget: TokenBudget
    ): PromptSection {

        // 1. 加载基础系统提示
        val basePrompt = loadBaseSystemPrompt()

        // 2. 加载行为规则（从 AGENTS.md）
        val behaviorRules = loadBehaviorRules(context.agentProfile)

        // 3. 加载插件提供的提示片段
        val pluginFragments = loadPluginFragments(context)

        // 4. 组装系统提示
        return PromptSection(
            name = "system",
            priority = PromptPriority.Highest,
            content = buildString {
                appendLine(basePrompt)
                appendLine()
                appendLine("## Behavior Rules")
                behaviorRules.forEach { rule ->
                    appendLine("- $rule")
                }
                appendLine()
                pluginFragments.forEach { fragment ->
                    if (fragment.priority == PromptPriority.Highest) {
                        appendLine(fragment.content)
                        appendLine()
                    }
                }
            },
            estimatedTokens = tokenEstimator.estimate(basePrompt)
        )
    }

    private fun buildToolsContext(
        context: BuildContext,
        budget: TokenBudget
    ): PromptSection? {

        // 获取可用工具列表
        val availableTools = pluginRegistry.getAvailableTools(context.sessionId)

        if (availableTools.isEmpty()) {
            return null
        }

        // 构建工具描述
        val toolsDescription = buildString {
            appendLine("## Available Tools")
            appendLine()

            availableTools.forEach { tool ->
                appendLine("### ${tool.name}")
                appendLine(tool.description)
                appendLine()
                if (tool.parameters.isNotEmpty()) {
                    appendLine("**Parameters:**")
                    tool.parameters.forEach { (name, param) ->
                        appendLine("- `$name`: ${param.description}")
                    }
                    appendLine()
                }

                // 检查 Token 预算
                if (tokenEstimator.estimate(toString()) > budget.toolTokens) {
                    appendLine("...(truncated)")
                    break
                }
            }
        }

        return PromptSection(
            name = "tools",
            priority = PromptPriority.High,
            content = toolsDescription,
            estimatedTokens = tokenEstimator.estimate(toolsDescription)
        )
    }

    private fun assemblePrompt(
        system: PromptSection,
        session: SessionContext,
        memory: List<Memory>,
        tools: PromptSection?,
        userRequest: UserRequest
    ): PromptBuildResult {

        val sections = mutableListOf<PromptSection>()

        // 按优先级添加片段
        sections.add(system)

        // 添加记忆
        if (memory.isNotEmpty()) {
            sections.add(PromptSection(
                name = "memory",
                priority = PromptPriority.Medium,
                content = buildMemorySection(memory),
                estimatedTokens = memory.sumOf { it.estimatedTokens }
            ))
        }

        // 添加工具
        tools?.let { sections.add(it) }

        // 添加会话历史
        sections.add(PromptSection(
            name = "conversation",
            priority = PromptPriority.Low,
            content = buildConversationSection(session),
            estimatedTokens = session.totalTokens
        ))

        // 添加用户请求
        sections.add(PromptSection(
            name = "user",
            priority = PromptPriority.Highest,
            content = "User: ${userRequest.content}",
            estimatedTokens = tokenEstimator.estimate(userRequest.content)
        ))

        return PromptBuildResult(
            content = sections.joinToString("\n\n") { it.content },
            sections = sections,
            totalTokens = sections.sumOf { it.estimatedTokens },
            metadata = PromptMetadata(
                sections = sections.map { it.name },
                toolsIncluded = tools != null,
                memoryCount = memory.size,
                messageCount = session.messageCount
            )
        )
    }

    private fun compressPrompt(
        prompt: PromptBuildResult,
        budget: TokenBudget
    ): PromptBuildResult {

        var compressed = prompt
        val sections = prompt.sections.toMutableList()

        // 按优先级从低到高删除片段
        while (compressed.totalTokens > budget.totalTokens && sections.isNotEmpty()) {
            val lowestPriority = sections
                .filter { it.priority != PromptPriority.Highest }
                .minByOrNull { it.priority }

            if (lowestPriority != null) {
                sections.remove(lowestPriority)
                compressed = compressed.copy(
                    sections = sections,
                    content = sections.joinToString("\n\n") { it.content },
                    totalTokens = sections.sumOf { it.estimatedTokens }
                )
            } else {
                break
            }
        }

        return compressed
    }

    override fun estimateTokens(content: String): Int {
        return tokenEstimator.estimate(content)
    }

    override fun getPromptTemplate(templateName: String): PromptTemplate? {
        return templateCache[templateName]
            ?: loadTemplateFromFile(templateName)?.also { template ->
                templateCache[templateName] = template
            }
    }

    override fun registerPromptFragment(fragment: PromptFragment): Result<Unit> {
        return Result.success {
            promptFragments[fragment.id] = fragment
        }
    }

    // 辅助方法
    private fun loadBaseSystemPrompt(): String {
        return """
You are MingClaw, an intelligent assistant designed to help users accomplish tasks efficiently.

## Core Principles
- Be helpful and accurate
- Ask clarifying questions when needed
- Use available tools when appropriate
- Learn from conversations to improve
        """.trimIndent()
    }

    private fun loadBehaviorRules(agentProfile: AgentProfile): List<String> {
        // 从 AGENTS.md 加载行为规则
        return listOf(
            "Always prioritize user intent",
            "Verify tool results before presenting",
            "Provide concise and clear responses"
        )
    }

    private suspend fun loadPluginFragments(context: BuildContext): List<PromptFragment> {
        return pluginRegistry.getLoadedPlugins()
            .mapNotNull { plugin ->
                plugin.getPromptFragments(context)
            }
            .flatten()
    }

    private fun buildMemorySection(memories: List<Memory>): String {
        return buildString {
            appendLine("## Relevant Information")
            appendLine()
            memories.forEach { memory ->
                appendLine("- ${memory.content}")
            }
        }
    }

    private fun buildConversationSection(session: SessionContext): String {
        return buildString {
            appendLine("## Conversation History")
            appendLine()
            session.messages.takeLast(10).forEach { message ->
                appendLine("${message.role}: ${message.content}")
            }
        }
    }
}
```

---

## 事件总线

### 核心接口

```kotlin
/**
 * 事件总线接口
 *
 * 职责：
 * - 管理事件订阅
 * - 分发事件到订阅者
 * - 处理事件优先级
 */
interface EventBus {

    /**
     * 订阅事件
     */
    fun subscribe(
        eventType: String,
        subscriber: EventSubscriber
    ): Subscription

    /**
     * 订阅事件（带过滤器）
     */
    fun subscribe(
        eventType: String,
        subscriber: EventSubscriber,
        filter: EventFilter
    ): Subscription

    /**
     * 订阅事件（带优先级）
     */
    fun subscribe(
        eventType: String,
        subscriber: EventSubscriber,
        priority: EventPriority
    ): Subscription

    /**
     * 发布事件（同步）
     */
    fun publish(event: Event): List<EventResult>

    /**
     * 发布事件（异步）
     */
    fun publishAsync(event: Event): Job

    /**
     * 关闭事件总线
     */
    suspend fun shutdown()
}
```

### 实现类

```kotlin
/**
 * 事件总线实现
 */
@Singleton
internal class EventBusImpl @Inject constructor(
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : EventBus {

    // 订阅者存储：事件类型 -> 优先级 -> 订阅者列表
    private val subscribers = mutableMapOf<String,
        Map<EventPriority, MutableList<SubscribedEvent>>>()

    // 互斥锁
    private val mutex = Mutex()

    // 事件历史（用于调试）
    private val eventHistory = CircularBuffer<EventLog>(100)

    // 是否已关闭
    @Volatile
    private var isShutdown = false

    override fun subscribe(
        eventType: String,
        subscriber: EventSubscriber
    ): Subscription {
        return subscribe(eventType, subscriber, EventPriority.Normal)
    }

    override fun subscribe(
        eventType: String,
        subscriber: EventSubscriber,
        filter: EventFilter
    ): Subscription {
        val subscription = subscribe(eventType, subscriber)

        // 包装订阅者以添加过滤器
        val wrappedSubscriber = FilteringEventSubscriber(
            original = subscriber,
            filter = filter
        )

        return subscription
    }

    override fun subscribe(
        eventType: String,
        subscriber: EventSubscriber,
        priority: EventPriority
    ): Subscription {

        val subscriptionId = UUID.randomUUID().toString()
        val subscribedEvent = SubscribedEvent(
            id = subscriptionId,
            subscriber = subscriber,
            priority = priority,
            subscribedAt = Clock.System.now()
        )

        runBlocking {
            mutex.withLock {
                if (isShutdown) {
                    throw IllegalStateException("EventBus is shutdown")
                }

                subscribers.getOrPut(eventType) {
                    EnumMap(EventPriority::class.java).apply {
                        EventPriority.entries.forEach {
                            this[it] = mutableListOf()
                        }
                    }
                }[priority]?.add(subscribedEvent)
            }
        }

        return Subscription(
            id = subscriptionId,
            eventType = eventType,
            unsubscribe = {
                runBlocking {
                    mutex.withLock {
                        subscribers[eventType]?.get(priority)?.removeIf {
                            it.id == subscriptionId
                        }
                    }
                }
            }
        )
    }

    override fun publish(event: Event): List<EventResult> {

        if (isShutdown) {
            return listOf(EventResult.Failure(
                "EventBus is shutdown",
                IllegalStateException()
            ))
        }

        val eventType = event::class.simpleName ?: "Unknown"
        val startTime = Clock.System.now()

        // 记录事件
        eventHistory.add(EventLog(
            event = event,
            timestamp = startTime
        ))

        // 获取订阅者
        val allSubscribers = runBlocking {
            mutex.withLock {
                subscribers[eventType]?.values?.flatten()
                    ?.sortedByDescending { it.priority }
                    ?: emptyList()
            }
        }

        if (allSubscribers.isEmpty()) {
            return emptyList()
        }

        // 分发事件
        return allSubscribers.map { subscribed ->
            try {
                val result = subscribed.subscriber.onEvent(event)
                EventResult.Success(
                    subscriberId = subscribed.id,
                    result = result,
                    duration = Clock.System.now() - startTime
                )
            } catch (e: Exception) {
                EventResult.Failure(
                    subscriberId = subscribed.id,
                    error = e.message ?: "Unknown error",
                    exception = e,
                    duration = Clock.System.now() - startTime
                )
            }
        }
    }

    override fun publishAsync(event: Event): Job {
        return CoroutineScope(ioDispatcher).launch {
            publish(event)
        }
    }

    override suspend fun shutdown() {
        mutex.withLock {
            isShutdown = true
            subscribers.clear()
        }
    }

    /**
     * 获取事件统计
     */
    fun getEventStatistics(): EventStatistics {
        return EventStatistics(
            totalEvents = eventHistory.size,
            subscribersByType = subscribers.mapValues {
                it.value.values.sumOf { list -> list.size }
            },
            recentEvents = eventHistory.toList().takeLast(10)
        )
    }
}

/**
 * 订阅的事件
 */
private data class SubscribedEvent(
    val id: String,
    val subscriber: EventSubscriber,
    val priority: EventPriority,
    val subscribedAt: Instant
)

/**
 * 事件优先级
 */
enum class EventPriority {
    Critical,  // 最高优先级
    High,
    Normal,
    Low
}

/**
 * 事件过滤器
 */
fun interface EventFilter {
    fun shouldAccept(event: Event): Boolean
}

/**
 * 带过滤器的订阅者包装器
 */
private class FilteringEventSubscriber(
    private val original: EventSubscriber,
    private val filter: EventFilter
) : EventSubscriber {

    override val id: String = original.id

    override fun onEvent(event: Event): EventResult {
        return if (filter.shouldAccept(event)) {
            original.onEvent(event)
        } else {
            EventResult.Skipped(id)
        }
    }
}
```

---

## 配置管理

### 核心接口

```kotlin
/**
 * 配置管理器接口
 */
interface ConfigManager {

    /**
     * 获取配置
     */
    fun getConfig(): KernelConfig

    /**
     * 更新配置
     */
    fun updateConfig(updates: ConfigUpdates): Result<KernelConfig>

    /**
     * 重置为默认配置
     */
    fun resetToDefault(): Result<KernelConfig>

    /**
     * 监听配置变化
     */
    fun watchConfigChanges(): Flow<KernelConfig>

    /**
     * 保存配置
     */
    suspend fun saveConfig(): Result<Unit>

    /**
     * 加载配置
     */
    suspend fun loadConfig(): Result<KernelConfig>
}
```

### 实现类

```kotlin
/**
 * 配置管理器实现
 */
@Singleton
internal class ConfigManagerImpl @Inject constructor(
    private val dataStore: DataStore<KernelConfig>,
    private val defaultConfig: DefaultConfigProvider
) : ConfigManager {

    // 配置缓存
    private val configCache = AtomicReference<KernelConfig>()

    // 配置变化流
    private val _configChanges = MutableSharedFlow<KernelConfig>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun getConfig(): KernelConfig {
        return configCache.get() ?: loadDefaultConfig()
    }

    override fun updateConfig(updates: ConfigUpdates): Result<KernelConfig> {
        return try {
            val current = getConfig()
            val updated = updates.apply(current)

            // 验证配置
            validateConfig(updated)
                .onFailure { error ->
                    return Result.failure(error)
                }

            // 更新缓存
            configCache.set(updated)

            // 保存到存储
            CoroutineScope(Dispatchers.IO).launch {
                dataStore.updateData { updated }
            }

            // 发布变化
            CoroutineScope(Dispatchers.Main).launch {
                _configChanges.emit(updated)
            }

            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun resetToDefault(): Result<KernelConfig> {
        val default = defaultConfig.get()
        configCache.set(default)

        CoroutineScope(Dispatchers.IO).launch {
            dataStore.updateData { default }
        }

        return Result.success(default)
    }

    override fun watchConfigChanges(): Flow<KernelConfig> {
        return _configChanges.asSharedFlow()
    }

    override suspend fun saveConfig(): Result<Unit> {
        return try {
            val config = getConfig()
            dataStore.updateData { config }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadConfig(): Result<KernelConfig> {
        return try {
            val config = dataStore.data.first()
            configCache.set(config)
            Result.success(config)
        } catch (e: Exception) {
            // 加载失败，使用默认配置
            val default = loadDefaultConfig()
            configCache.set(default)
            Result.success(default)
        }
    }

    private fun loadDefaultConfig(): KernelConfig {
        return defaultConfig.get()
    }

    private fun validateConfig(config: KernelConfig): Result<Unit> {
        // 验证 Token 限制
        if (config.maxTokens <= 0) {
            return Result.failure(
                ConfigValidationException("maxTokens must be positive")
            )
        }

        // 验证模型配置
        if (config.modelConfig.modelName.isBlank()) {
            return Result.failure(
                ConfigValidationException("modelName cannot be blank")
            )
        }

        return Result.success(Unit)
    }
}
```

---

## 安全模块

### 核心接口

```kotlin
/**
 * 安全管理器接口
 */
interface SecurityManager {

    /**
     * 初始化安全模块
     */
    suspend fun initialize(): Result<Unit>

    /**
     * 检查插件权限
     */
    fun checkPluginPermission(pluginId: String): Boolean

    /**
     * 验证插件安全性
     */
    fun isPluginSafe(plugin: MingClawPlugin): Boolean

    /**
     * 验证任务权限
     */
    fun checkTaskPermission(task: AgentTask): Boolean

    /**
     * 加密数据
     */
    suspend fun encryptData(data: ByteArray): Result<ByteArray>

    /**
     * 解密数据
     */
    suspend fun decryptData(encrypted: ByteArray): Result<ByteArray>

    /**
     * 验证签名
     */
    fun verifySignature(data: ByteArray, signature: ByteArray): Boolean
}
```

---

## 依赖关系

### 模块依赖图

```
┌─────────────────────────────────────────────────────────────────┐
│                        MingClawKernel                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Plugin     │  │    Event     │  │    Task      │         │
│  │  Registry    │◄─┤    Bus       │◄─┤ Dispatcher   │         │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
│         │                  │                  │                  │
│         ▼                  ▼                  ▼                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                   Core Services                           │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │   │
│  │  │   Dynamic    │  │    Config    │  │   Security   │  │   │
│  │  │   Prompt     │  │   Manager    │  │   Manager    │  │   │
│  │  │   Builder    │  │              │  │              │  │   │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │   │
│  └─────────┼──────────────────┼──────────────────┼──────────┘   │
└────────────┼──────────────────┼──────────────────┼──────────────┘
             │                  │                  │
             ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Data & Storage                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  DataStore   │  │  Room DB     │  │  File System │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

### 依赖说明

| 模块 | 依赖 | 说明 |
|------|------|------|
| **MingClawKernel** | PluginRegistry, EventBus, TaskDispatcher, ConfigManager, SecurityManager | 核心依赖 |
| **PluginRegistry** | SecurityManager, PluginLoader | 需要安全检查 |
| **EventBus** | - | 独立模块 |
| **TaskDispatcher** | EventBus | 发布任务事件 |
| **DynamicPromptBuilder** | ContextManager, MemoryManager, WindowManager | 需要上下文数据 |
| **ConfigManager** | DataStore | 持久化配置 |
| **SecurityManager** | DataStore | 存储密钥 |

---

## 附录

### A. 相关数据类

```kotlin
/**
 * 内核配置
 */
@Serializable
data class KernelConfig(
    val maxTokens: Int = 8192,
    val modelConfig: ModelConfig = ModelConfig(),
    val pluginConfig: PluginConfig = PluginConfig(),
    val securityConfig: SecurityConfig = SecurityConfig()
)

/**
 * 模型配置
 */
@Serializable
data class ModelConfig(
    val modelName: String = "claude-opus-4-6",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val timeout: Duration = 120.seconds
)

/**
 * 插件配置
 */
@Serializable
data class PluginConfig(
    val autoLoad: List<String> = emptyList(),
    val disabledPlugins: List<String> = emptyList(),
    val pluginDirectories: List<String> = listOf(
        "/system/plugins",
        "/user/plugins"
    )
)

/**
 * Token 预算
 */
data class TokenBudget(
    val totalTokens: Int,
    val systemTokens: Int,
    val memoryTokens: Int,
    val toolTokens: Int,
    val conversationTokens: Int
) {
    companion object {
        fun calculate(
            maxTokens: Int,
            systemReserved: Int = 1000
        ): TokenBudget {
            val available = maxTokens - systemReserved
            return TokenBudget(
                totalTokens = maxTokens,
                systemTokens = systemReserved,
                memoryTokens = (available * 0.2).toInt(),
                toolTokens = (available * 0.2).toInt(),
                conversationTokens = (available * 0.6).toInt()
            )
        }
    }
}
```

### B. 事件类型定义

```kotlin
/**
 * 事件基类
 */
sealed interface Event {
    val timestamp: Instant

    /**
     * 插件事件
     */
    data class PluginLoaded(
        val pluginId: String,
        override val timestamp: Instant = Clock.System.now()
    ) : Event

    data class PluginUnloaded(
        val pluginId: String,
        override val timestamp: Instant = Clock.System.now()
    ) : Event

    data class PluginError(
        val pluginId: String,
        val error: String,
        override val timestamp: Instant = Clock.System.now()
    ) : Event

    /**
     * 任务事件
     */
    data class TaskStarted(
        val taskId: String,
        override val timestamp: Instant = Clock.System.now()
    ) : Event

    data class TaskCompleted(
        val taskId: String,
        val result: Any?,
        override val timestamp: Instant = Clock.System.now()
    ) : Event

    data class TaskFailed(
        val taskId: String,
        val error: String,
        override val timestamp: Instant = Clock.System.now()
    ) : Event

    /**
     * 配置事件
     */
    data class ConfigUpdated(
        val config: KernelConfig,
        override val timestamp: Instant = Clock.System.now()
    ) : Event
}
```

### C. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构设计
- [02-evolution.md](./02-evolution.md) - 自我进化机制
- [04-context-management.md](./04-context-management.md) - 上下文管理
- [05-plugin-system.md](./05-plugin-system.md) - 插件系统

---

**文档维护**: 本文档应随着核心模块的实现持续更新
**审查周期**: 每两周一次或重大变更时
