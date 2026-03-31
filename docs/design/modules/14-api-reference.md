# MingClaw API参考

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [API概述](#api概述)
2. [核心API](#核心api)
3. [上下文管理API](#上下文管理api)
4. [记忆管理API](#记忆管理api)
5. [任务编排API](#任务编排api)
6. [插件系统API](#插件系统api)
7. [进化系统API](#进化系统api)
8. [数据模型](#数据模型)
9. [事件定义](#事件定义)
10. [错误代码](#错误代码)
11. [附录](#附录)

---

## API概述

### API分类

MingClaw API 按功能模块分类：

| 分类 | 描述 | API数量 |
|------|------|---------|
| **核心API** | 微内核、事件总线、配置管理 | 15 |
| **上下文API** | 会话上下文、记忆上下文、Token管理 | 12 |
| **记忆API** | 记忆存储、检索、管理 | 10 |
| **任务API** | 任务编排、调度、执行 | 8 |
| **插件API** | 插件加载、工具注册、权限管理 | 14 |
| **进化API** | 进化触发、分析、执行 | 6 |

### API版本控制

```
当前版本: v1.0.0
API前缀: /api/v1/
向后兼容: 是
弃用策略: 至少保留2个大版本
```

---

## 核心API

### 微内核API

#### MingClawKernel

```kotlin
/**
 * MingClaw微内核核心接口
 *
 * 负责管理插件生命周期、任务调度和事件分发
 */
interface MingClawKernel {

    /**
     * 加载插件
     *
     * @param pluginId 插件唯一标识符
     * @return Result<PluginContext> 加载结果或错误
     * @throws SecurityException 如果权限检查失败
     * @throws PluginNotFoundException 如果插件不存在
     */
    suspend fun loadPlugin(pluginId: String): Result<PluginContext>

    /**
     * 卸载插件
     *
     * @param pluginId 要卸载的插件ID
     * @return Result<Unit> 卸载结果或错误
     */
    suspend fun unloadPlugin(pluginId: String): Result<Unit>

    /**
     * 重新加载插件
     *
     * @param pluginId 要重新加载的插件ID
     * @return Result<PluginContext> 重新加载结果
     */
    suspend fun reloadPlugin(pluginId: String): Result<PluginContext>

    /**
     * 获取已加载的插件列表
     *
     * @return List<PluginInfo> 插件信息列表
     */
    fun getLoadedPlugins(): List<PluginInfo>

    /**
     * 获取插件信息
     *
     * @param pluginId 插件ID
     * @return PluginInfo? 插件信息，不存在时返回null
     */
    fun getPluginInfo(pluginId: String): PluginInfo?

    /**
     * 分发任务到合适的处理器
     *
     * @param task 要分发的任务
     * @return TaskResult 任务执行结果
     */
    suspend fun dispatchTask(task: AgentTask): TaskResult

    /**
     * 调度定期任务
     *
     * @param task 要调度的定期任务
     * @return CancellableTask 可取消的任务句柄
     */
    fun scheduleRecurringTask(task: ScheduledTask): CancellableTask

    /**
     * 调度延迟任务
     *
     * @param task 要调度的任务
     * @param delay 延迟时间
     * @return CancellableTask 可取消的任务句柄
     */
    fun scheduleDelayedTask(task: AgentTask, delay: Duration): CancellableTask

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     * @return Result<Unit> 取消结果
     */
    fun cancelTask(taskId: String): Result<Unit>

    /**
     * 订阅事件
     *
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @return Subscription 订阅句柄
     */
    fun subscribe(eventType: String, handler: EventHandler): Subscription

    /**
     * 订阅一次性事件
     *
     * @param eventType 事件类型
     * @param handler 事件处理器
     * @return Subscription 订阅句柄
     */
    fun subscribeOnce(eventType: String, handler: EventHandler): Subscription

    /**
     * 取消订阅
     *
     * @param subscriptionId 订阅ID
     * @return Result<Unit> 取消结果
     */
    fun unsubscribe(subscriptionId: String): Result<Unit>

    /**
     * 发布事件
     *
     * @param event 要发布的事件
     * @return List<EventResult> 各处理器的处理结果
     */
    fun publish(event: Event): List<EventResult>

    /**
     * 异步发布事件
     *
     * @param event 要发布的事件
     * @return Job 异步任务句柄
     */
    fun publishAsync(event: Event): Job

    /**
     * 获取配置
     *
     * @return KernelConfig 当前配置
     */
    fun getConfig(): KernelConfig

    /**
     * 更新配置
     *
     * @param updates 配置更新
     * @return Result<Unit> 更新结果
     */
    fun updateConfig(updates: ConfigUpdates): Result<Unit>
}
```

#### 数据类型

```kotlin
/**
 * 插件上下文
 */
@Serializable
data class PluginContext(
    val pluginId: String,
    val kernel: MingClawKernel,
    val lifecycleScope: CoroutineScope,
    val createdAt: Instant = Clock.System.now()
)

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
    val state: PluginState,
    val loadedAt: Instant
)

@Serializable
enum class PluginState {
    LOADED,
    ACTIVE,
    PAUSED,
    ERROR
}

/**
 * 可取消任务
 */
interface CancellableTask {
    val taskId: String
    val isCancelled: Boolean
    fun cancel(): Result<Unit>
}

/**
 * 订阅句柄
 */
@Serializable
data class Subscription(
    val id: String,
    val unsubscribe: () -> Unit
)

/**
 * 事件处理器
 */
fun interface EventHandler {
    suspend fun onEvent(event: Event): EventResult
}

/**
 * 事件结果
 */
sealed class EventResult {
    data class Success(val handlerId: String) : EventResult()
    data class Failure(val handlerId: String, val error: String?) : EventResult()
}

/**
 * 内核配置
 */
@Serializable
data class KernelConfig(
    val maxPlugins: Int = 100,
    val maxTasks: Int = 1000,
    val taskTimeout: Duration = Duration.parse("30s"),
    val eventQueueSize: Int = 1000,
    val debugMode: Boolean = false
)
```

### 事件总线API

#### EventBus

```kotlin
/**
 * 事件总线接口
 *
 * 提供组件间的事件通信机制
 */
interface EventBus {

    /**
     * 订阅事件
     *
     * @param eventType 事件类型
     * @param subscriber 事件订阅者
     * @return Subscription 订阅句柄
     */
    fun subscribe(eventType: String, subscriber: EventSubscriber): Subscription

    /**
     * 发布事件
     *
     * @param event 要发布的事件
     * @return List<EventResult> 各订阅者的处理结果
     */
    fun publish(event: Event): List<EventResult>

    /**
     * 异步发布事件
     *
     * @param event 要发布的事件
     * @return Job 异步任务句柄
     */
    fun publishAsync(event: Event): Job

    /**
     * 广播事件到所有订阅者
     *
     * @param event 要广播的事件
     * @return List<EventResult> 处理结果
     */
    fun broadcast(event: Event): List<EventResult>

    /**
     * 获取订阅者数量
     *
     * @param eventType 事件类型
     * @return Int 订阅者数量
     */
    fun getSubscriberCount(eventType: String): Int

    /**
     * 清除所有订阅
     */
    fun clearAllSubscriptions()
}

/**
 * 事件订阅者
 */
interface EventSubscriber {
    val id: String
    suspend fun onEvent(event: Event)
}
```

---

## 上下文管理API

### 会话上下文API

#### SessionContextManager

```kotlin
/**
 * 会话上下文管理器
 *
 * 负责管理对话会话的上下文信息
 */
interface SessionContextManager {

    /**
     * 创建新会话
     *
     * @param config 会话配置
     * @return Session 新创建的会话
     */
    suspend fun createSession(config: SessionConfig = SessionConfig.default()): Session

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return Session? 会话对象，不存在时返回null
     */
    suspend fun getSession(sessionId: String): Session?

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     * @return Result<Unit> 删除结果
     */
    suspend fun deleteSession(sessionId: String): Result<Unit>

    /**
     * 添加消息到会话
     *
     * @param sessionId 会话ID
     * @param message 要添加的消息
     * @return Result<Unit> 添加结果
     */
    suspend fun addMessage(sessionId: String, message: Message): Result<Unit>

    /**
     * 获取会话消息
     *
     * @param sessionId 会话ID
     * @param limit 返回消息数量限制
     * @return Flow<List<Message>> 消息流
     */
    fun getMessages(sessionId: String, limit: Int = Int.MAX_VALUE): Flow<List<Message>>

    /**
     * 获取会话上下文
     *
     * @param sessionId 会话ID
     * @return ConversationContext 对话上下文
     */
    suspend fun getContext(sessionId: String): ConversationContext

    /**
     * 更新会话元数据
     *
     * @param sessionId 会话ID
     * @param metadata 元数据
     * @return Result<Unit> 更新结果
     */
    suspend fun updateMetadata(
        sessionId: String,
        metadata: Map<String, String>
    ): Result<Unit>

    /**
     * 压缩会话上下文
     *
     * @param sessionId 会话ID
     * @param strategy 压缩策略
     * @return Result<Unit> 压缩结果
     */
    suspend fun compressContext(
        sessionId: String,
        strategy: CompressionStrategy = CompressionStrategy.AUTO
    ): Result<Unit>

    /**
     * 获取所有会话
     *
     * @return Flow<List<Session>> 会话列表流
     */
    fun getAllSessions(): Flow<List<Session>>

    /**
     * 搜索会话
     *
     * @param query 搜索查询
     * @return Flow<List<Session>> 匹配的会话流
     */
    fun searchSessions(query: String): Flow<List<Session>>
}
```

#### 数据类型

```kotlin
/**
 * 会话
 */
@Serializable
data class Session(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val messageCount: Int,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 会话配置
 */
@Serializable
data class SessionConfig(
    val maxMessages: Int = 1000,
    val maxTokens: Int = 4000,
    val compressionThreshold: Double = 0.8,
    val retentionDays: Int = 30
) {
    companion object {
        fun default() = SessionConfig()
    }
}

/**
 * 对话上下文
 */
@Serializable
data class ConversationContext(
    val sessionId: String,
    val systemPrompt: String? = null,
    val messages: List<Message>,
    val memories: List<Memory>,
    val toolDefinitions: List<ToolDefinition>,
    val tokenUsage: TokenUsage
)

/**
 * 消息
 */
@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val role: MessageRole,
    val timestamp: Instant = Clock.System.now(),
    val metadata: Map<String, String> = emptyMap(),
    val toolCalls: List<ToolCall> = emptyList()
)

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

/**
 * 工具调用
 */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, JsonElement>,
    val result: String? = null
)

/**
 * Token使用情况
 */
@Serializable
data class TokenUsage(
    val total: Int,
    val input: Int,
    val output: Int,
    val system: Int
)
```

### 记忆上下文API

#### MemoryContextManager

```kotlin
/**
 * 记忆上下文管理器
 *
 * 负责管理与LLM交互的上下文记忆
 */
interface MemoryContextManager {

    /**
     * 检索相关记忆
     *
     * @param query 查询文本
     * @param limit 返回记忆数量限制
     * @return List<Memory> 相关记忆列表
     */
    suspend fun retrieveMemories(query: String, limit: Int = 10): List<Memory>

    /**
     * 获取永久记忆
     *
     * @return List<Memory> 永久记忆列表
     */
    suspend fun getPermanentMemories(): List<Memory>

    /**
     * 存储记忆
     *
     * @param memory 要存储的记忆
     * @return Result<Unit> 存储结果
     */
    suspend fun storeMemory(memory: Memory): Result<Unit>

    /**
     * 更新记忆
     *
     * @param memoryId 记忆ID
     * @param updates 更新内容
     * @return Result<Unit> 更新结果
     */
    suspend fun updateMemory(
        memoryId: String,
        updates: MemoryUpdate
    ): Result<Unit>

    /**
     * 删除记忆
     *
     * @param memoryId 记忆ID
     * @return Result<Unit> 删除结果
     */
    suspend fun deleteMemory(memoryId: String): Result<Unit>

    /**
     * 计算记忆相关性
     *
     * @param query 查询文本
     * @param memory 记忆
     * @return Float 相关性分数 (0.0 - 1.0)
     */
    suspend fun calculateRelevance(query: String, memory: Memory): Float

    /**
     * 获取记忆统计
     *
     * @return MemoryStats 记忆统计信息
     */
    suspend fun getMemoryStats(): MemoryStats
}
```

#### 数据类型

```kotlin
/**
 * 记忆
 */
@Serializable
data class Memory(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: MemoryType,
    val importance: Float = 0.5f,
    val embedding: FloatArray? = null,
    val createdAt: Instant = Clock.System.now(),
    val lastAccessed: Instant = Clock.System.now(),
    val accessCount: Int = 0,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Memory

        if (id != other.id) return false
        if (content != other.content) return false
        if (type != other.type) return false
        if (importance != other.importance) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + importance.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}

@Serializable
enum class MemoryType {
    FACTUAL,        // 事实性知识
    PROCEDURAL,     // 程序性知识
    SEMANTIC,       // 语义知识
    EPISODIC,       // 情景记忆
    WORKING         // 工作记忆
}

/**
 * 记忆更新
 */
@Serializable
data class MemoryUpdate(
    val content: String? = null,
    val importance: Float? = null,
    val metadata: Map<String, String>? = null
)

/**
 * 记忆统计
 */
@Serializable
data class MemoryStats(
    val totalMemories: Int,
    val memoriesByType: Map<MemoryType, Int>,
    val averageImportance: Float,
    val oldestMemory: Instant?,
    val newestMemory: Instant?
)
```

### Token管理API

#### TokenManager

```kotlin
/**
 * Token管理器
 *
 * 负责Token计数和预算管理
 */
interface TokenManager {

    /**
     * 估算Token数量
     *
     * @param text 文本内容
     * @return Int 估算的Token数量
     */
    fun estimateTokens(text: String): Int

    /**
     * 计算消息Token数量
     *
     * @param message 消息
     * @return Int Token数量
     */
    fun calculateMessageTokens(message: Message): Int

    /**
     * 计算上下文Token数量
     *
     * @param context 对话上下文
     * @return TokenUsage Token使用情况
     */
    fun calculateContextTokens(context: ConversationContext): TokenUsage

    /**
     * 分配Token预算
     *
     * @param totalBudget 总预算
     * @return TokenBudget 分配后的预算
     */
    fun allocateBudget(totalBudget: Int): TokenBudget

    /**
     * 检查Token限制
     *
     * @param usage 当前使用情况
     * @param limit 限制
     * @return TokenLimitResult 检查结果
     */
    fun checkLimit(usage: TokenUsage, limit: Int): TokenLimitResult

    /**
     * 获取Token使用统计
     *
     * @param period 时间范围
     * @return TokenUsageStats 使用统计
     */
    suspend fun getUsageStats(period: TimeRange): TokenUsageStats
}
```

#### 数据类型

```kotlin
/**
 * Token预算
 */
@Serializable
data class TokenBudget(
    val total: Int,
    val system: Int,
    val history: Int,
    val memory: Int,
    val tools: Int,
    val response: Int
)

/**
 * Token限制结果
 */
sealed class TokenLimitResult {
    object WithinLimit : TokenLimitResult()
    data class ApproachingLimit(val remaining: Int) : TokenLimitResult()
    data class ExceededLimit(val overage: Int) : TokenLimitResult()
}

/**
 * Token使用统计
 */
@Serializable
data class TokenUsageStats(
    val totalTokens: Long,
    val averagePerSession: Double,
    val peakUsage: Int,
    val cost: Double,
    val period: TimeRange
)
```

---

## 记忆管理API

### 记忆存储API

#### MemoryRepository

```kotlin
/**
 * 记忆仓库接口
 */
interface MemoryRepository {

    /**
     * 保存记忆
     *
     * @param memory 要保存的记忆
     * @return Result<Unit> 保存结果
     */
    suspend fun save(memory: Memory): Result<Unit>

    /**
     * 批量保存记忆
     *
     * @param memories 要保存的记忆列表
     * @return Result<Unit> 保存结果
     */
    suspend fun saveAll(memories: List<Memory>): Result<Unit>

    /**
     * 获取记忆
     *
     * @param memoryId 记忆ID
     * @return Memory? 记忆对象，不存在时返回null
     */
    suspend fun get(memoryId: String): Memory?

    /**
     * 获取所有记忆
     *
     * @return Flow<List<Memory>> 记忆流
     */
    fun getAll(): Flow<List<Memory>>

    /**
     * 按类型获取记忆
     *
     * @param type 记忆类型
     * @return Flow<List<Memory>> 记忆流
     */
    fun getByType(type: MemoryType): Flow<List<Memory>>

    /**
     * 搜索记忆
     *
     * @param query 搜索查询
     * @param limit 返回数量限制
     * @return List<Memory> 匹配的记忆列表
     */
    suspend fun search(query: String, limit: Int = 10): List<Memory>

    /**
     * 向量搜索记忆
     *
     * @param queryEmbedding 查询向量
     * @param limit 返回数量限制
     * @param threshold 相似度阈值
     * @return List<Memory> 匹配的记忆列表
     */
    suspend fun vectorSearch(
        queryEmbedding: FloatArray,
        limit: Int = 10,
        threshold: Float = 0.0f
    ): List<Memory>

    /**
     * 更新记忆
     *
     * @param memoryId 记忆ID
     * @param updates 更新内容
     * @return Result<Unit> 更新结果
     */
    suspend fun update(memoryId: String, updates: MemoryUpdate): Result<Unit>

    /**
     * 删除记忆
     *
     * @param memoryId 记忆ID
     * @return Result<Unit> 删除结果
     */
    suspend fun delete(memoryId: String): Result<Unit>

    /**
     * 清理旧记忆
     *
     * @param before 时间戳，删除此时间之前的记忆
     * @return Result<Int> 删除的记忆数量
     */
    suspend fun cleanup(before: Instant): Result<Int>
}
```

---

## 任务编排API

### 任务调度API

#### TaskOrchestrator

```kotlin
/**
 * 任务编排器接口
 *
 * 负责任务的解析、编排和执行
 */
interface TaskOrchestrator {

    /**
     * 解析任务
     *
     * @param response LLM响应
     * @return List<ParsedTask> 解析出的任务列表
     */
    suspend fun parseTasks(response: String): List<ParsedTask>

    /**
     * 执行任务
     *
     * @param task 要执行的任务
     * @return TaskResult 任务执行结果
     */
    suspend fun executeTask(task: ParsedTask): TaskResult

    /**
     * 批量执行任务
     *
     * @param tasks 要执行的任务列表
     * @return List<TaskResult> 任务执行结果列表
     */
    suspend fun executeTasks(tasks: List<ParsedTask>): List<TaskResult>

    /**
     * 调度任务
     *
     * @param task 要调度的任务
     * @param config 调度配置
     * @return CancellableTask 可取消的任务句柄
     */
    fun scheduleTask(
        task: ParsedTask,
        config: ScheduleConfig = ScheduleConfig.default()
    ): CancellableTask

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     * @return Result<Unit> 取消结果
     */
    suspend fun cancelTask(taskId: String): Result<Unit>

    /**
     * 获取任务状态
     *
     * @param taskId 任务ID
     * @return TaskStatus? 任务状态，不存在时返回null
     */
    suspend fun getTaskStatus(taskId: String): TaskStatus?

    /**
     * 获取所有任务
     *
     * @return Flow<List<TaskInfo>> 任务列表流
     */
    fun getAllTasks(): Flow<List<TaskInfo>>
}
```

#### 数据类型

```kotlin
/**
 * 解析的任务
 */
@Serializable
data class ParsedTask(
    val id: String = UUID.randomUUID().toString(),
    val type: TaskType,
    val tool: String,
    val parameters: Map<String, JsonElement>,
    val description: String? = null,
    val dependencies: List<String> = emptyList()
)

@Serializable
enum class TaskType {
    TOOL_CALL,
    FUNCTION_CALL,
    QUERY,
    COMPUTATION
}

/**
 * 任务结果
 */
sealed class TaskResult {
    data class Success(val result: Any) : TaskResult()
    data class Failure(val error: String) : TaskResult()
    object Pending : TaskResult()
    data class Partial(val results: List<Any>) : TaskResult()
}

/**
 * 任务状态
 */
@Serializable
enum class TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * 任务信息
 */
@Serializable
data class TaskInfo(
    val id: String,
    val type: TaskType,
    val status: TaskStatus,
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null
)

/**
 * 调度配置
 */
@Serializable
data class ScheduleConfig(
    val priority: TaskPriority = TaskPriority.NORMAL,
    val delay: Duration = Duration.ZERO,
    val timeout: Duration = Duration.parse("30s"),
    val retryPolicy: RetryPolicy = RetryPolicy.NO_RETRY
) {
    companion object {
        fun default() = ScheduleConfig()
    }
}

@Serializable
enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

@Serializable
enum class RetryPolicy {
    NO_RETRY,
    RETRY_ONCE,
    RETRY_THRICE,
    RETRY_UNTIL_SUCCESS
}
```

---

## 插件系统API

### 插件管理API

#### PluginManager

```kotlin
/**
 * 插件管理器接口
 */
interface PluginManager {

    /**
     * 加载插件
     *
     * @param pluginId 插件ID
     * @return Result<PluginContext> 加载结果
     */
    suspend fun loadPlugin(pluginId: String): Result<PluginContext>

    /**
     * 卸载插件
     *
     * @param pluginId 插件ID
     * @return Result<Unit> 卸载结果
     */
    suspend fun unloadPlugin(pluginId: String): Result<Unit>

    /**
     * 重新加载插件
     *
     * @param pluginId 插件ID
     * @return Result<PluginContext> 重新加载结果
     */
    suspend fun reloadPlugin(pluginId: String): Result<PluginContext>

    /**
     * 获取插件信息
     *
     * @param pluginId 插件ID
     * @return PluginInfo? 插件信息
     */
    suspend fun getPluginInfo(pluginId: String): PluginInfo?

    /**
     * 列出所有插件
     *
     * @return List<PluginInfo> 插件列表
     */
    suspend fun listPlugins(): List<PluginInfo>

    /**
     * 启用插件
     *
     * @param pluginId 插件ID
     * @return Result<Unit> 启用结果
     */
    suspend fun enablePlugin(pluginId: String): Result<Unit>

    /**
     * 禁用插件
     *
     * @param pluginId 插件ID
     * @return Result<Unit> 禁用结果
     */
    suspend fun disablePlugin(pluginId: String): Result<Unit>

    /**
     * 检查插件权限
     *
     * @param pluginId 插件ID
     * @param permission 权限
     * @return Boolean 是否有权限
     */
    fun checkPermission(pluginId: String, permission: Permission): Boolean

    /**
     * 请求插件权限
     *
     * @param pluginId 插件ID
     * @param permission 权限
     * @return PermissionResult 权限请求结果
     */
    suspend fun requestPermission(
        pluginId: String,
        permission: Permission
    ): PermissionResult
}
```

### 工具API

#### ToolRegistry

```kotlin
/**
 * 工具注册表接口
 */
interface ToolRegistry {

    /**
     * 注册工具
     *
     * @param tool 工具定义
     * @return Result<Unit> 注册结果
     */
    suspend fun registerTool(tool: ToolDefinition): Result<Unit>

    /**
     * 取消注册工具
     *
     * @param toolId 工具ID
     * @return Result<Unit> 取消注册结果
     */
    suspend fun unregisterTool(toolId: String): Result<Unit>

    /**
     * 获取工具
     *
     * @param toolId 工具ID
     * @return ToolDefinition? 工具定义
     */
    fun getTool(toolId: String): ToolDefinition?

    /**
     * 列出所有工具
     *
     * @return List<ToolDefinition> 工具列表
     */
    fun listTools(): List<ToolDefinition>

    /**
     * 按类别列出工具
     *
     * @param category 工具类别
     * @return List<ToolDefinition> 工具列表
     */
    fun listToolsByCategory(category: ToolCategory): List<ToolDefinition>

    /**
     * 执行工具
     *
     * @param toolId 工具ID
     * @param arguments 参数
     * @return ToolExecutionResult 执行结果
     */
    suspend fun executeTool(
        toolId: String,
        arguments: Map<String, Any>
    ): ToolExecutionResult
}
```

#### 数据类型

```kotlin
/**
 * 工具定义
 */
@Serializable
data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val parameters: Map<String, ParameterDefinition>,
    val handler: ToolHandler,
    val enabled: Boolean = true
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

/**
 * 参数定义
 */
@Serializable
data class ParameterDefinition(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = false,
    val default: String? = null,
    val enum: List<String>? = null
)

@Serializable
enum class ParameterType {
    STRING,
    INTEGER,
    FLOAT,
    BOOLEAN,
    ARRAY,
    OBJECT
}

/**
 * 工具处理器
 */
interface ToolHandler {
    suspend fun execute(arguments: Map<String, Any>): ToolExecutionResult
}

/**
 * 工具执行结果
 */
@Serializable
sealed class ToolExecutionResult {
    data class Success(val result: String) : ToolExecutionResult()
    data class Error(val error: String) : ToolExecutionResult()
}
```

---

## 进化系统API

### 进化触发API

#### EvolutionTrigger

```kotlin
/**
 * 进化触发器接口
 */
interface EvolutionTrigger {

    /**
     * 检查是否需要触发进化
     *
     * @param context 进化上下文
     * @return EvolutionTriggerResult 触发检查结果
     */
    suspend fun shouldTrigger(context: EvolutionContext): EvolutionTriggerResult

    /**
     * 触发进化
     *
     * @param type 进化类型
     * @param reason 触发原因
     * @param context 进化上下文
     * @return Result<EvolutionRequest> 进化请求
     */
    suspend fun trigger(
        type: EvolutionType,
        reason: String,
        context: EvolutionContext
    ): Result<EvolutionRequest>

    /**
     * 手动触发进化
     *
     * @param type 进化类型
     * @param reason 触发原因
     * @return Result<EvolutionRequest> 进化请求
     */
    suspend fun triggerManually(
        type: EvolutionType,
        reason: String
    ): Result<EvolutionRequest>

    /**
     * 获取触发历史
     *
     * @return Flow<EvolutionTriggerRecord> 触发记录流
     */
    fun getTriggerHistory(): Flow<EvolutionTriggerRecord>
}
```

#### 数据类型

```kotlin
/**
 * 进化类型
 */
@Serializable
enum class EvolutionType {
    BEHAVIOR,      // 行为进化
    KNOWLEDGE,     // 知识进化
    CAPABILITY     // 能力进化
}

/**
 * 进化上下文
 */
@Serializable
data class EvolutionContext(
    val sessionId: String,
    val feedbackScore: Float,
    val taskSuccessRate: Float,
    val memoryCount: Int,
    val lastEvolution: Instant?,
    val systemMetrics: SystemMetrics
)

/**
 * 系统指标
 */
@Serializable
data class SystemMetrics(
    val responseTime: Duration,
    val memoryUsage: Long,
    val cpuUsage: Float,
    val errorRate: Float
)

/**
 * 进化触发结果
 */
sealed class EvolutionTriggerResult {
    object NoTriggerNeeded : EvolutionTriggerResult()
    data class ShouldTrigger(
        val type: EvolutionType,
        val reason: String,
        val priority: EvolutionPriority
    ) : EvolutionTriggerResult()
}

@Serializable
enum class EvolutionPriority {
    LOW,
    MEDIUM,
    HIGH,
    IMMEDIATE
}

/**
 * 进化请求
 */
@Serializable
data class EvolutionRequest(
    val id: String,
    val type: EvolutionType,
    val reason: String,
    val context: EvolutionContext,
    val status: EvolutionStatus,
    val createdAt: Instant,
    val completedAt: Instant? = null
)

@Serializable
enum class EvolutionStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * 进化触发记录
 */
@Serializable
data class EvolutionTriggerRecord(
    val id: String,
    val type: EvolutionType,
    val reason: String,
    val timestamp: Instant,
    val status: EvolutionStatus
)
```

---

## 数据模型

### 核心模型

```kotlin
/**
 * 版本
 */
@Serializable
@JvmInline
value class Version(val value: String) : Comparable<Version> {
    companion object {
        val UNKNOWN = Version("0.0.0")
    }

    override fun compareTo(other: Version): Int {
        val parts1 = value.split(".")
        val parts2 = other.value.split(".")

        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val v1 = parts1.getOrNull(i)?.toIntOrNull() ?: 0
            val v2 = parts2.getOrNull(i)?.toIntOrNull() ?: 0
            if (v1 != v2) return v1.compareTo(v2)
        }

        return 0
    }
}

/**
 * 时间范围
 */
@Serializable
data class TimeRange(
    val start: Instant,
    val end: Instant
) {
    companion object {
        fun lastHour() = TimeRange(
            start = Clock.System.now() - Duration.parse("1h"),
            end = Clock.System.now()
        )

        fun lastDay() = TimeRange(
            start = Clock.System.now() - Duration.parse("24h"),
            end = Clock.System.now()
        )

        fun lastWeek() = TimeRange(
            start = Clock.System.now() - Duration.parse("7d"),
            end = Clock.System.now()
        )
    }
}

/**
 * 验证结果
 */
@Serializable
data class ValidationResult(
    val isValid: Boolean,
    val error: String? = null
) {
    companion object {
        fun success() = ValidationResult(true)
        fun failure(error: String) = ValidationResult(false, error)
    }
}
```

---

## 事件定义

### 核心事件

```kotlin
/**
 * 基础事件
 */
@Serializable
sealed class Event {
    abstract val timestamp: Instant

    /**
     * 插件事件
     */
    @Serializable
    sealed class PluginEvent : Event() {
        data class Loaded(
            val pluginId: String,
            override val timestamp: Instant = Clock.System.now()
        ) : PluginEvent()

        data class Unloaded(
            val pluginId: String,
            override val timestamp: Instant = Clock.System.now()
        ) : PluginEvent()

        data class Error(
            val pluginId: String,
            val error: String,
            override val timestamp: Instant = Clock.System.now()
        ) : PluginEvent()
    }

    /**
     * 任务事件
     */
    @Serializable
    sealed class TaskEvent : Event() {
        data class Created(
            val taskId: String,
            val taskType: String,
            override val timestamp: Instant = Clock.System.now()
        ) : TaskEvent()

        data class Completed(
            val taskId: String,
            val result: TaskResult,
            override val timestamp: Instant = Clock.System.now()
        ) : TaskEvent()

        data class Failed(
            val taskId: String,
            val error: String,
            override val timestamp: Instant = Clock.System.now()
        ) : TaskEvent()
    }

    /**
     * 上下文事件
     */
    @Serializable
    sealed class ContextEvent : Event() {
        data class Created(
            val sessionId: String,
            override val timestamp: Instant = Clock.System.now()
        ) : ContextEvent()

        data class Updated(
            val sessionId: String,
            val updateType: String,
            override val timestamp: Instant = Clock.System.now()
        ) : ContextEvent()

        data class Compressed(
            val sessionId: String,
            val strategy: String,
            override val timestamp: Instant = Clock.System.now()
        ) : ContextEvent()
    }

    /**
     * 记忆事件
     */
    @Serializable
    sealed class MemoryEvent : Event() {
        data class Stored(
            val memoryId: String,
            val memoryType: MemoryType,
            override val timestamp: Instant = Clock.System.now()
        ) : MemoryEvent()

        data class Retrieved(
            val query: String,
            val count: Int,
            override val timestamp: Instant = Clock.System.now()
        ) : MemoryEvent()

        data class Updated(
            val memoryId: String,
            override val timestamp: Instant = Clock.System.now()
        ) : MemoryEvent()

        data class Deleted(
            val memoryId: String,
            override val timestamp: Instant = Clock.System.now()
        ) : MemoryEvent()
    }

    /**
     * 进化事件
     */
    @Serializable
    sealed class EvolutionEvent : Event() {
        data class Triggered(
            val evolutionType: EvolutionType,
            val reason: String,
            override val timestamp: Instant = Clock.System.now()
        ) : EvolutionEvent()

        data class Started(
            val evolutionId: String,
            override val timestamp: Instant = Clock.System.now()
        ) : EvolutionEvent()

        data class Completed(
            val evolutionId: String,
            val changes: List<String>,
            override val timestamp: Instant = Clock.System.now()
        ) : EvolutionEvent()

        data class Failed(
            val evolutionId: String,
            val error: String,
            override val timestamp: Instant = Clock.System.now()
        ) : EvolutionEvent()
    }
}
```

---

## 错误代码

### 错误代码定义

```kotlin
/**
 * MingClaw错误代码
 */
object ErrorCodes {
    // 通用错误 (1xxx)
    const val UNKNOWN_ERROR = 1000
    const val INVALID_PARAMETER = 1001
    const val MISSING_PARAMETER = 1002
    const val OPERATION_NOT_SUPPORTED = 1003
    const val OPERATION_TIMEOUT = 1004

    // 插件错误 (2xxx)
    const val PLUGIN_NOT_FOUND = 2000
    const val PLUGIN_LOAD_FAILED = 2001
    const val PLUGIN_ALREADY_LOADED = 2002
    const val PLUGIN_PERMISSION_DENIED = 2003
    const val PLUGIN_DEPENDENCY_MISSING = 2004

    // 任务错误 (3xxx)
    const val TASK_NOT_FOUND = 3000
    const val TASK_EXECUTION_FAILED = 3001
    const val TASK_TIMEOUT = 3002
    const val TASK_CANCELLED = 3003
    const val TASK_DEPENDENCY_FAILED = 3004

    // 上下文错误 (4xxx)
    const val SESSION_NOT_FOUND = 4000
    const val SESSION_EXPIRED = 4001
    const val CONTEXT_TOO_LARGE = 4002
    const val CONTEXT_COMPRESSION_FAILED = 4003

    // 记忆错误 (5xxx)
    const val MEMORY_NOT_FOUND = 5000
    const val MEMORY_STORE_FAILED = 5001
    const val MEMORY_RETRIEVAL_FAILED = 5002
    const val MEMORY_EMBEDDING_FAILED = 5003

    // 进化错误 (6xxx)
    const val EVOLUTION_TRIGGER_FAILED = 6000
    const val EVOLUTION_EXECUTION_FAILED = 6001
    const val EVOLUTION_VALIDATION_FAILED = 6002
    const val EVOLUTION_ROLLBACK_FAILED = 6003

    // 安全错误 (7xxx)
    const val AUTHENTICATION_FAILED = 7000
    const val AUTHORIZATION_FAILED = 7001
    const val ENCRYPTION_FAILED = 7002
    const val DECRYPTION_FAILED = 7003
    const val SANDBOX_VIOLATION = 7004
}
```

---

## 附录

### A. API版本历史

| 版本 | 发布日期 | 主要变更 |
|------|----------|----------|
| v1.0.0 | 2025-03-31 | 初始版本 |

### B. 废弃API

| API | 废弃版本 | 替代方案 | 移除版本 |
|-----|----------|----------|----------|
| - | - | - | - |

### C. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构设计
- [03-core-modules.md](./03-core-modules.md) - 核心模块设计
- [05-plugin-system.md](./05-plugin-system.md) - 插件系统设计

---

**文档维护**: 本文档应随着API演进持续更新
**审查周期**: 每个版本发布时更新
