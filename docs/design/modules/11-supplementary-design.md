# MingClaw 补充设计

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [补充设计概述](#补充设计概述)
2. [模块间通信](#模块间通信)
3. [进化触发](#进化触发)
4. [Token计数](#token计数)
5. [向量数据库](#向量数据库)
6. [数据迁移](#数据迁移)
7. [离线模式](#离线模式)
8. [依赖关系](#依赖关系)
9. [附录](#附录)

---

## 补充设计概述

### 设计目标

本文档补充MingClaw系统中的关键功能设计：

| 功能 | 优先级 | 复杂度 | 说明 |
|------|--------|--------|------|
| **模块间通信** | 高 | 中 | 跨模块消息传递机制 |
| **进化触发** | 高 | 高 | 自动/手动触发进化 |
| **Token计数** | 中 | 低 | 精确的Token使用统计 |
| **向量数据库** | 高 | 高 | 向量存储和检索 |
| **数据迁移** | 中 | 中 | 数据版本迁移 |
| **离线模式** | 中 | 中 | 无网络时的工作模式 |

### 架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      Supplementary Systems                             │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                      Module Communication                         │  │
│  │  - Event Bus                                                       │  │
│  │  - Message Queue                                                   │  │
│  │  - IPC Bridge                                                      │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌───────────────┬─────────────────┼─────────────────┬─────────────────┐ │
│  │               │                 │                 │                 │ │
│  ▼               ▼                 ▼                 ▼                 │ │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ │
│  │Evolution  │ │  Token    │ │  Vector   │ │  Data     │ │  Offline  │ │
│  │ Trigger   │ │  Counter  │ │  Database │ │ Migration │ │   Mode    │ │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 模块间通信

### 通信方式

MingClaw 支持多种模块间通信方式：

| 方式 | 用途 | 特点 |
|------|------|------|
| **事件总线** | 同模块内通信 | 异步、解耦 |
| **消息队列** | 跨进程通信 | 可靠、持久化 |
| **IPC Bridge** | 跨模块通信 | 安全、可控 |

### 事件总线

```kotlin
/**
 * 模块间事件接口
 */
sealed class ModuleEvent {
    abstract val sourceModule: String
    abstract val targetModule: String?
    abstract val timestamp: Instant

    /**
     * 上下文相关事件
     */
    data class ContextUpdated(
        override val sourceModule: String,
        val sessionId: String,
        val contextSize: Int,
        override val targetModule: String? = null,
        override val timestamp: Instant = Clock.System.now()
    ) : ModuleEvent()

    /**
     * 记忆相关事件
     */
    data class MemoryStored(
        override val sourceModule: String,
        val memoryId: String,
        val importance: Float,
        override val targetModule: String? = null,
        override val timestamp: Instant = Clock.System.now()
    ) : ModuleEvent()

    /**
     * 任务相关事件
     */
    data class TaskCompleted(
        override val sourceModule: String,
        val taskId: String,
        val result: TaskResult,
        override val targetModule: String? = null,
        override val timestamp: Instant = Clock.System.now()
    ) : ModuleEvent()

    /**
     * 进化相关事件
     */
    data class EvolutionTriggered(
        override val sourceModule: String,
        val evolutionType: EvolutionType,
        val reason: String,
        override val targetModule: String? = null,
        override val timestamp: Instant = Clock.System.now()
    ) : ModuleEvent()
}

/**
 * 模块事件总线
 */
interface ModuleEventBus {
    /**
     * 发布事件
     */
    suspend fun publish(event: ModuleEvent): Result<Unit>

    /**
     * 订阅事件
     */
    fun subscribe(
        module: String,
        eventType: KClass<out ModuleEvent>,
        handler: suspend (ModuleEvent) -> Unit
    ): Subscription

    /**
     * 取消订阅
     */
    fun unsubscribe(subscription: Subscription)

    /**
     * 广播事件到所有模块
     */
    suspend fun broadcast(event: ModuleEvent): Result<Unit>
}

/**
 * 模块事件总线实现
 */
internal class ModuleEventBusImpl @Inject constructor(
    private val auditLogger: AuditLogger,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : ModuleEventBus {

    private val subscribers = mutableMapOf<String, MutableList<EventHandler>>()
    private val mutex = Mutex()

    override suspend fun publish(event: ModuleEvent): Result<Unit> = withContext(ioDispatcher) {
        try {
            val targetModule = event.targetModule
            val eventType = event::class

            // 记录事件
            auditLogger.logModuleEvent(event)

            // 查找订阅者
            val handlers = mutex.withLock {
                if (targetModule != null) {
                    // 发送到特定模块
                    subscribers[targetModule]?.filter { it.eventType == eventType }
                } else {
                    // 广播到所有订阅者
                    subscribers.values.flatten().filter { it.eventType == eventType }
                }
            } ?: emptyList()

            // 执行处理
            handlers.forEach { handler ->
                try {
                    handler.handler(event)
                } catch (e: Exception) {
                    auditLogger.logEventProcessingError(event, e)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun subscribe(
        module: String,
        eventType: KClass<out ModuleEvent>,
        handler: suspend (ModuleEvent) -> Unit
    ): Subscription {
        val eventHandler = EventHandler(eventType, handler)

        runBlocking {
            mutex.withLock {
                subscribers.getOrPut(module) { mutableListOf() }.add(eventHandler)
            }
        }

        return Subscription(
            id = UUID.randomUUID().toString(),
            unsubscribe = {
                runBlocking {
                    mutex.withLock {
                        subscribers[module]?.remove(eventHandler)
                    }
                }
            }
        )
    }

    override fun unsubscribe(subscription: Subscription) {
        subscription.unsubscribe()
    }

    override suspend fun broadcast(event: ModuleEvent): Result<Unit> {
        return publish(event.copy(targetModule = null as? String))
    }

    private data class EventHandler(
        val eventType: KClass<out ModuleEvent>,
        val handler: suspend (ModuleEvent) -> Unit
    )
}
```

### 消息队列

```kotlin
/**
 * 模块间消息
 */
@Serializable
data class ModuleMessage(
    val id: String = UUID.randomUUID().toString(),
    val source: String,
    val target: String,
    val type: String,
    val payload: ByteArray,
    val priority: MessagePriority = MessagePriority.NORMAL,
    val timestamp: Instant = Clock.System.now(),
    val expiresAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModuleMessage

        if (id != other.id) return false
        if (source != other.source) return false
        if (target != other.target) return false
        if (type != other.type) return false
        if (!payload.contentEquals(other.payload)) return false
        if (priority != other.priority) return false
        if (timestamp != other.timestamp) return false
        if (expiresAt != other.expiresAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + target.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + priority.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (expiresAt?.hashCode() ?: 0)
        return result
    }
}

@Serializable
enum class MessagePriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * 消息队列接口
 */
interface MessageQueue {
    /**
     * 发送消息
     */
    suspend fun send(message: ModuleMessage): Result<Unit>

    /**
     * 接收消息
     */
    suspend fun receive(module: String, timeout: Duration? = null): ModuleMessage?

    /**
     * 确认消息
     */
    suspend fun ack(messageId: String): Result<Unit>

    /**
     * 拒绝消息
     */
    suspend fun reject(messageId: String, requeue: Boolean = false): Result<Unit>

    /**
     * 获取队列大小
     */
    suspend fun getQueueSize(module: String): Int
}
```

---

## 进化触发

### 触发机制

```kotlin
/**
 * 进化触发器接口
 */
interface EvolutionTrigger {

    /**
     * 检查是否需要触发进化
     */
    suspend fun shouldTrigger(context: EvolutionContext): EvolutionTriggerResult

    /**
     * 触发进化
     */
    suspend fun trigger(
        type: EvolutionType,
        reason: String,
        context: EvolutionContext
    ): Result<EvolutionRequest>

    /**
     * 手动触发
     */
    suspend fun triggerManually(
        type: EvolutionType,
        reason: String
    ): Result<EvolutionRequest>

    /**
     * 获取触发历史
     */
    fun getTriggerHistory(): Flow<EvolutionTriggerRecord>
}

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
```

### 触发器实现

```kotlin
/**
 * 进化触发器实现
 */
internal class EvolutionTriggerImpl @Inject constructor(
    private val feedbackCollector: FeedbackCollector,
    private val systemMonitor: SystemMonitor,
    private val evolutionStore: EvolutionStore,
    private val auditLogger: AuditLogger,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : EvolutionTrigger {

    override suspend fun shouldTrigger(
        context: EvolutionContext
    ): EvolutionTriggerResult = withContext(ioDispatcher) {
        // 检查反馈分数
        if (context.feedbackScore < FEEDBACK_THRESHOLD) {
            return@withContext EvolutionTriggerResult.ShouldTrigger(
                type = EvolutionType.BEHAVIOR,
                reason = "Feedback score below threshold: ${context.feedbackScore}",
                priority = EvolutionPriority.HIGH
            )
        }

        // 检查任务成功率
        if (context.taskSuccessRate < SUCCESS_RATE_THRESHOLD) {
            return@withContext EvolutionTriggerResult.ShouldTrigger(
                type = EvolutionType.BEHAVIOR,
                reason = "Task success rate below threshold: ${context.taskSuccessRate}",
                priority = EvolutionPriority.MEDIUM
            )
        }

        // 检查记忆数量
        if (context.memoryCount > MEMORY_THRESHOLD) {
            return@withContext EvolutionTriggerResult.ShouldTrigger(
                type = EvolutionType.KNOWLEDGE,
                reason = "Memory count exceeds threshold: ${context.memoryCount}",
                priority = EvolutionPriority.LOW
            )
        }

        // 检查系统指标
        if (context.systemMetrics.responseTime > RESPONSE_TIME_THRESHOLD) {
            return@withContext EvolutionTriggerResult.ShouldTrigger(
                type = EvolutionType.BEHAVIOR,
                reason = "Response time exceeds threshold: ${context.systemMetrics.responseTime}",
                priority = EvolutionPriority.IMMEDIATE
            )
        }

        // 检查距离上次进化的时间
        val timeSinceLastEvolution = context.lastEvolution?.let {
            Clock.System.now() - it
        } ?: Duration.INFINITE

        if (timeSinceLastEvolution > REGULAR_EVOLUTION_INTERVAL) {
            return@withContext EvolutionTriggerResult.ShouldTrigger(
                type = EvolutionType.KNOWLEDGE,
                reason = "Regular evolution check",
                priority = EvolutionPriority.LOW
            )
        }

        EvolutionTriggerResult.NoTriggerNeeded
    }

    override suspend fun trigger(
        type: EvolutionType,
        reason: String,
        context: EvolutionContext
    ): Result<EvolutionRequest> = withContext(ioDispatcher) {
        try {
            // 创建进化请求
            val request = EvolutionRequest(
                id = generateEvolutionId(),
                type = type,
                reason = reason,
                context = context,
                status = EvolutionStatus.PENDING,
                createdAt = Clock.System.now()
            )

            // 保存请求
            evolutionStore.saveEvolutionRequest(request)

            // 记录审计日志
            auditLogger.logEvolutionTrigger(request)

            // 发布事件
            eventBus.publish(ModuleEvent.EvolutionTriggered(
                sourceModule = "evolution",
                evolutionType = type,
                reason = reason
            ))

            Result.success(request)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun triggerManually(
        type: EvolutionType,
        reason: String
    ): Result<EvolutionRequest> = withContext(ioDispatcher) {
        try {
            // 创建上下文
            val context = EvolutionContext(
                sessionId = "manual",
                feedbackScore = 1.0f,
                taskSuccessRate = 1.0f,
                memoryCount = 0,
                lastEvolution = null,
                systemMetrics = SystemMetrics(
                    responseTime = Duration.ZERO,
                    memoryUsage = 0,
                    cpuUsage = 0f,
                    errorRate = 0f
                )
            )

            // 触发进化
            trigger(type, reason, context)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getTriggerHistory(): Flow<EvolutionTriggerRecord> {
        return evolutionStore.getEvolutionHistory()
            .map { request ->
                EvolutionTriggerRecord(
                    id = request.id,
                    type = request.type,
                    reason = request.reason,
                    timestamp = request.createdAt,
                    status = request.status
                )
            }
    }

    private fun generateEvolutionId(): String {
        return "ev_${Clock.System.now().toEpochMilliseconds()}_${UUID.randomUUID()}"
    }

    companion object {
        const val FEEDBACK_THRESHOLD = 0.6f
        const val SUCCESS_RATE_THRESHOLD = 0.7f
        const val MEMORY_THRESHOLD = 1000
        val RESPONSE_TIME_THRESHOLD = Duration.parse("5s")
        val REGULAR_EVOLUTION_INTERVAL = Duration.parse("7d")
    }
}
```

---

## Token计数

### Token计数器

```kotlin
/**
 * Token计数器接口
 */
interface TokenCounter {

    /**
     * 计算文本的Token数量
     */
    fun countTokens(text: String): Int

    /**
     * 估算消息的Token数量
     */
    fun estimateMessageTokens(message: Message): Int

    /**
     * 计算对话上下文的Token数量
     */
    fun calculateContextTokens(context: ConversationContext): TokenUsage

    /**
     * 获取Token使用统计
     */
    fun getTokenUsageStats(period: TimeRange): TokenUsageStats

    /**
     * 检查Token限制
     */
    fun checkTokenLimit(usage: TokenUsage, limit: Int): TokenLimitResult
}

/**
 * Token使用情况
 */
@Serializable
data class TokenUsage(
    val total: Int,
    val input: Int,
    val output: Int,
    val system: Int,
    val breakdown: Map<String, Int> = emptyMap()
)

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

/**
 * Token限制结果
 */
sealed class TokenLimitResult {
    object WithinLimit : TokenLimitResult()
    data class ApproachingLimit(val remaining: Int) : TokenLimitResult()
    data class ExceededLimit(val overage: Int) : TokenLimitResult()
}
```

### Token计数器实现

```kotlin
/**
 * Token计数器实现
 *
 * 使用tiktoken算法进行精确计数
 */
internal class TokenCounterImpl @Inject constructor(
    private val tokenEncoder: TokenEncoder,
    private val usageStore: TokenUsageStore,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : TokenCounter {

    override fun countTokens(text: String): Int {
        return tokenEncoder.encode(text).size
    }

    override fun estimateMessageTokens(message: Message): Int {
        val contentTokens = countTokens(message.content)
        val metadataTokens = countTokens(buildMessageMetadata(message))
        return contentTokens + metadataTokens
    }

    override fun calculateContextTokens(context: ConversationContext): TokenUsage {
        var inputTokens = 0
        var outputTokens = 0
        var systemTokens = 0
        val breakdown = mutableMapOf<String, Int>()

        // 系统提示
        context.systemPrompt?.let {
            systemTokens += countTokens(it)
            breakdown["system_prompt"] = countTokens(it)
        }

        // 对话历史
        context.messages.forEach { message ->
            val tokens = estimateMessageTokens(message)
            breakdown["message_${message.id}"] = tokens

            when (message.role) {
                MessageRole.USER -> inputTokens += tokens
                MessageRole.ASSISTANT -> outputTokens += tokens
                MessageRole.SYSTEM -> systemTokens += tokens
            }
        }

        // 记忆内容
        context.memories.forEach { memory ->
            val tokens = countTokens(memory.content)
            inputTokens += tokens
            breakdown["memory_${memory.id}"] = tokens
        }

        // 工具定义
        context.toolDefinitions.forEach { tool ->
            val tokens = countTokens(tool.description)
            systemTokens += tokens
            breakdown["tool_${tool.name}"] = tokens
        }

        return TokenUsage(
            total = inputTokens + outputTokens + systemTokens,
            input = inputTokens,
            output = outputTokens,
            system = systemTokens,
            breakdown = breakdown
        )
    }

    override fun getTokenUsageStats(period: TimeRange): TokenUsageStats {
        val usage = runBlocking {
            usageStore.getUsage(period)
        }

        val totalTokens = usage.sumOf { it.total }
        val sessionCount = usage.size
        val averagePerSession = if (sessionCount > 0) {
            totalTokens.toDouble() / sessionCount
        } else {
            0.0
        }
        val peakUsage = usage.maxOfOrNull { it.total } ?: 0
        val cost = calculateCost(totalTokens)

        return TokenUsageStats(
            totalTokens = totalTokens,
            averagePerSession = averagePerSession,
            peakUsage = peakUsage,
            cost = cost,
            period = period
        )
    }

    override fun checkTokenLimit(usage: TokenUsage, limit: Int): TokenLimitResult {
        val remaining = limit - usage.total

        return when {
            remaining < 0 -> TokenLimitResult.ExceededLimit(abs(remaining))
            remaining < WARNING_THRESHOLD -> TokenLimitResult.ApproachingLimit(remaining)
            else -> TokenLimitResult.WithinLimit
        }
    }

    private fun buildMessageMetadata(message: Message): String {
        return buildString {
            append("Role: ${message.role}\n")
            append("Timestamp: ${message.timestamp}\n")
            if (message.metadata.isNotEmpty()) {
                append("Metadata: ${message.metadata}\n")
            }
        }
    }

    private fun calculateCost(tokens: Long): Double {
        // 假设使用GPT-4的价格
        val pricePerMillion = 30.0 // $30 per million tokens
        return (tokens.toDouble() / 1_000_000) * pricePerMillion
    }

    companion object {
        const val WARNING_THRESHOLD = 500
    }
}

/**
 * Token编码器接口
 */
interface TokenEncoder {
    fun encode(text: String): List<Int>
    fun decode(tokens: List<Int>): String
}

/**
 * Cl100k_base编码器实现 (GPT-4使用的编码器)
 */
internal class Cl100kBaseEncoder : TokenEncoder {

    private val encoder: MutableMap<String, Int> = loadEncoder()
    private val decoder: MutableMap<Int, String> = encoder.entries.associate { it.value to it.key }.toMutableMap()

    override fun encode(text: String): List<Int> {
        val tokens = mutableListOf<Int>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            // 查找最长的匹配token
            var matchFound = false
            for (i in minOf(remaining.length, MAX_TOKEN_LENGTH) downTo 1) {
                val substring = remaining.substring(0, i)
                if (encoder.containsKey(substring)) {
                    tokens.add(encoder[substring]!!)
                    remaining = remaining.substring(i)
                    matchFound = true
                    break
                }
            }

            if (!matchFound) {
                // 处理未知字符
                val byteRepresentation = remaining.first().toString().toByteArray()
                byteRepresentation.forEach { byte ->
                    tokens.add(byte.toInt())
                }
                remaining = remaining.substring(1)
            }
        }

        return tokens
    }

    override fun decode(tokens: List<Int>): String {
        return tokens.joinToString("") { token ->
            decoder[token] ?: ""
        }
    }

    private fun loadEncoder(): MutableMap<String, Int> {
        // 从资源文件加载编码器
        // 这里简化实现，实际应该从文件加载
        return mutableMapOf()
    }

    companion object {
        const val MAX_TOKEN_LENGTH = 8
    }
}
```

---

## 向量数据库

### 向量存储接口

```kotlin
/**
 * 向量存储接口
 */
interface VectorStore {

    /**
     * 添加向量
     */
    suspend fun addVector(
        id: String,
        vector: FloatArray,
        metadata: Map<String, Any> = emptyMap()
    ): Result<Unit>

    /**
     * 批量添加向量
     */
    suspend fun addVectors(vectors: List<VectorData>): Result<Unit>

    /**
     * 搜索相似向量
     */
    suspend fun searchSimilar(
        queryVector: FloatArray,
        limit: Int = 10,
        threshold: Float = 0.0f
    ): Result<List<SearchResult>>

    /**
     * 删除向量
     */
    suspend fun deleteVector(id: String): Result<Unit>

    /**
     * 更新向量
     */
    suspend fun updateVector(
        id: String,
        vector: FloatArray,
        metadata: Map<String, Any> = emptyMap()
    ): Result<Unit>

    /**
     * 获取向量
     */
    suspend fun getVector(id: String): Result<VectorData?>

    /**
     * 获取统计信息
     */
    suspend fun getStats(): VectorStoreStats
}

/**
 * 向量数据
 */
@Serializable
data class VectorData(
    val id: String,
    val vector: FloatArray,
    val metadata: Map<String, Any> = emptyMap(),
    val createdAt: Instant = Clock.System.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VectorData

        if (id != other.id) return false
        if (!vector.contentEquals(other.vector)) return false
        if (metadata != other.metadata) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + metadata.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

/**
 * 搜索结果
 */
@Serializable
data class SearchResult(
    val id: String,
    val score: Float,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 向量存储统计
 */
@Serializable
data class VectorStoreStats(
    val totalVectors: Int,
    val dimension: Int,
    val indexSize: Long,
    val lastUpdated: Instant
)
```

### 向量存储实现

```kotlin
/**
 * 基于Room的向量存储实现
 */
internal class RoomVectorStore @Inject constructor(
    private val database: AppDatabase,
    private val embeddingService: EmbeddingService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : VectorStore {

    override suspend fun addVector(
        id: String,
        vector: FloatArray,
        metadata: Map<String, Any>
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val entity = VectorEntity(
                id = id,
                embedding = vector,
                metadata = Json.encodeToString(metadata),
                createdAt = Clock.System.now()
            )

            database.vectorDao().insert(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addVectors(vectors: List<VectorData>): Result<Unit> = withContext(ioDispatcher) {
        try {
            val entities = vectors.map { data ->
                VectorEntity(
                    id = data.id,
                    embedding = data.vector,
                    metadata = Json.encodeToString(data.metadata),
                    createdAt = data.createdAt
                )
            }

            database.vectorDao().insertAll(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchSimilar(
        queryVector: FloatArray,
        limit: Int,
        threshold: Float
    ): Result<List<SearchResult>> = withContext(ioDispatcher) {
        try {
            val results = database.vectorDao().searchNearest(
                embedding = queryVector,
                limit = limit
            )

            val filteredResults = results
                .filter { it.distance >= threshold }
                .map { entity ->
                    SearchResult(
                        id = entity.id,
                        score = entity.distance,
                        metadata = Json.decodeFromString(entity.metadata)
                    )
                }

            Result.success(filteredResults)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteVector(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            database.vectorDao().deleteById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateVector(
        id: String,
        vector: FloatArray,
        metadata: Map<String, Any>
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val entity = VectorEntity(
                id = id,
                embedding = vector,
                metadata = Json.encodeToString(metadata),
                createdAt = Clock.System.now()
            )

            database.vectorDao().update(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getVector(id: String): Result<VectorData?> = withContext(ioDispatcher) {
        try {
            val entity = database.vectorDao().findById(id)
            Result.success(
                entity?.let {
                    VectorData(
                        id = it.id,
                        vector = it.embedding,
                        metadata = Json.decodeFromString(it.metadata),
                        createdAt = it.createdAt
                    )
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStats(): VectorStoreStats = withContext(ioDispatcher) {
        val count = database.vectorDao().count()
        val dimension = database.vectorDao().getDimension()
        val indexSize = database.vectorDao().getIndexSize()
        val lastUpdated = database.vectorDao().getLastUpdated()

        VectorStoreStats(
            totalVectors = count,
            dimension = dimension,
            indexSize = indexSize,
            lastUpdated = lastUpdated
        )
    }
}

/**
 * 向量DAO
 */
@Dao
interface VectorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vector: VectorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vectors: List<VectorEntity>)

    @Update
    suspend fun update(vector: VectorEntity)

    @Query("DELETE FROM vectors WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM vectors WHERE id = :id")
    suspend fun findById(id: String): VectorEntity?

    @Query("""
        SELECT id, embedding, metadata, distance(embedding, :embedding) as distance
        FROM vectors
        ORDER BY distance DESC
        LIMIT :limit
    """)
    suspend fun searchNearest(
        embedding: FloatArray,
        limit: Int
    ): List<VectorEntity>

    @Query("SELECT COUNT(*) FROM vectors")
    suspend fun count(): Int

    @Query("SELECT LENGTH(embedding) / 4 FROM vectors LIMIT 1")
    suspend fun getDimension(): Int

    @Query("SELECT SUM(LENGTH(embedding)) FROM vectors")
    suspend fun getIndexSize(): Long

    @Query("SELECT MAX(created_at) FROM vectors")
    suspend fun getLastUpdated(): Instant
}

/**
 * 向量实体
 */
@Entity(tableName = "vectors")
data class VectorEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "embedding") val embedding: FloatArray,
    @ColumnInfo(name = "metadata") val metadata: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant
)
```

---

## 数据迁移

### 迁移接口

```kotlin
/**
 * 数据迁移管理器
 */
interface MigrationManager {

    /**
     * 执行迁移
     */
    suspend fun migrate(from: Version, to: Version): Result<MigrationResult>

    /**
     * 获取待执行的迁移
     */
    suspend fun getPendingMigrations(): List<Migration>

    /**
     * 回滚迁移
     */
    suspend fun rollback(version: Version): Result<Unit>

    /**
     * 获取迁移历史
     */
    fun getMigrationHistory(): Flow<MigrationRecord>
}

/**
 * 迁移接口
 */
interface Migration {
    val version: Version
    val description: String
    val isBreaking: Boolean

    suspend fun migrate(context: MigrationContext): Result<Unit>
    suspend fun rollback(context: MigrationContext): Result<Unit>
}

/**
 * 迁移上下文
 */
@Serializable
data class MigrationContext(
    val database: SupportSQLiteDatabase,
    val preferences: SharedPreferences,
    val fileSystem: FileSystem
)

/**
 * 迁移结果
 */
@Serializable
data class MigrationResult(
    val success: Boolean,
    val fromVersion: Version,
    val toVersion: Version,
    val steps: List<MigrationStep>,
    val duration: Duration
)

/**
 * 迁移步骤
 */
@Serializable
data class MigrationStep(
    val name: String,
    val status: StepStatus,
    val duration: Duration,
    val error: String? = null
)

@Serializable
enum class StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
```

---

## 离线模式

### 离线模式管理

```kotlin
/**
 * 离线模式管理器
 */
interface OfflineModeManager {

    /**
     * 检查是否离线
     */
    fun isOffline(): Flow<Boolean>

    /**
     * 启用离线模式
     */
    suspend fun enableOfflineMode(): Result<Unit>

    /**
     * 禁用离线模式
     */
    suspend fun disableOfflineMode(): Result<Unit>

    /**
     * 同步数据
     */
    suspend fun syncData(): Result<SyncResult>

    /**
     * 获取离线数据
     */
    suspend fun getOfflineData(): OfflineDataStatus
}

/**
 * 同步结果
 */
@Serializable
data class SyncResult(
    val success: Boolean,
    val uploaded: Int,
    val downloaded: Int,
    val failed: Int,
    val duration: Duration
)

/**
 * 离线数据状态
 */
@Serializable
data class OfflineDataStatus(
    val pendingUploads: Int,
    val pendingDownloads: Int,
    val localDataSize: Long,
    val lastSyncTime: Instant?
)
```

---

## 依赖关系

### 模块依赖

```
Supplementary Systems
    ├─→ Module Communication
    │   ├─→ Event Bus
    │   └─→ Message Queue
    ├─→ Evolution Trigger
    │   ├─→ Feedback Collector
    │   └─→ System Monitor
    ├─→ Token Counter
    │   ├─→ Token Encoder
    │   └─→ Usage Store
    ├─→ Vector Store
    │   ├─→ Room Database
    │   └─→ Embedding Service
    ├─→ Data Migration
    │   └─→ Migration Store
    └─→ Offline Mode
        ├─→ Network Monitor
        └─→ Sync Manager
```

---

## 附录

### A. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构设计
- [02-evolution.md](./02-evolution.md) - 自我进化机制设计
- [06-memory-management.md](./06-memory-management.md) - 记忆管理设计

### B. 参考资料

- [Tiktoken](https://github.com/openai/tiktoken) - Token计数算法
- [sqlite-vec](https://github.com/asg0r/sqlite-vec) - SQLite向量扩展
- [Vector Database](https://www.pinecone.io/learn/what-is-a-vector-database/) - 向量数据库概念

---

**文档维护**: 本文档应随着补充功能演进持续更新
**审查周期**: 每月一次或重大功能变更时
