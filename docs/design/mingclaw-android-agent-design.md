# MingClaw Android Agent 设计方案

**创建时间**: 2025-03-31
**最后更新**: 2025-03-31
**状态**: 设计完成 v2.0
**架构**: 插件化微内核架构
**版本**: 2.0

---

## 需求总结

| 维度 | 需求 |
|------|------|
| **进化范围** | 完整进化体系（行为+知识+能力） |
| **应用场景** | 通用自动化平台 |
| **数据策略** | 混合模式（本地处理+云端AI） |
| **架构方向** | 插件化架构 |
| **MVP功能** | 记忆管理、Skills扩展、工作区配置、任务编排 |

---

## 目录

1. [整体架构](#一整体架构)
2. [自我进化机制](#二自我进化机制)
3. [核心模块设计](#三核心模块设计)
4. [上下文管理](#四上下文管理)
5. [插件系统](#五插件系统)
6. [记忆管理](#六记忆管理)
7. [任务编排引擎](#七任务编排引擎)
8. [工作区与配置](#八工作区与配置)
9. [安全策略](#九安全策略)
10. [技术栈选型](#十技术栈选型)
11. [补充设计](#十一补充设计)
12. [系统质量保证](#十二系统质量保证)

---

## 一、整体架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Self-Evolution Layer (自我进化层)                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                       Evolution Engine                                │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐│  │
│  │  │   Behavior   │ │   Knowledge  │ │  Capability  │ │   Feedback   ││  │
│  │  │  Evolver     │ │  Evolver     │ │   Evolver    │ │   Collector  ││  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘│  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                    │                                         │
│  ┌─────────────────────────────────▼─────────────────────────────────────┐  │
│  │                      Evolution Store (进化存储)                        │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐│  │
│  │  │  AGENTS.md   │ │  MEMORY.md   │ │  SKILLS/     │ │  EXPERIENCE/ ││  │
│  │  │ (行为规则)   │ │ (长期记忆)   │ │  (技能包)    │ │  (经验数据)  ││  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘│  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 二、自我进化机制

### 2.1 进化循环流程

```
用户任务 → Agent执行 → 结果监控 → 反馈收集
    ↑                                    │
    │                                    ▼
    │                              进化分析
    │                                    │
    │                                    ▼
    │  ┌────────────────────────────────────────┐
    │  │         三种进化路径                   │
    │  ├────────────────────────────────────────┤
    │  │ 1. 行为进化 → 更新 AGENTS.md           │
    │  │ 2. 知识进化 → 更新 MEMORY.md           │
    │  │ 3. 能力进化 → 安装新 Skills             │
    │  └────────────────────────────────────────┘
    │                                    │
    │                                    ▼
    └────────────── 重新加载系统提示 ──────┘
                   (下次会话生效)
```

### 2.2 行为进化器

```kotlin
interface BehaviorEvolver {
    fun recordDecision(decision: AgentDecision, outcome: Outcome)
    fun processFeedback(feedback: UserFeedback)
    fun suggestRuleUpdates(): List<RuleUpdate>
    fun applyRuleUpdates(updates: List<RuleUpdate>)
}
```

### 2.3 知识进化器

```kotlin
interface KnowledgeEvolver {
    fun extractKnowledge(conversation: Conversation): List<KnowledgePoint>
    fun evaluateImportance(knowledge: KnowledgePoint): Importance
    fun consolidateToMemory(knowledge: List<KnowledgePoint>)
    fun searchMemory(query: String): List<MemoryFragment>
}
```

### 2.4 能力进化器

```kotlin
interface CapabilityEvolver {
    fun identifyCapabilityGaps(): List<CapabilityGap>
    fun searchSkills(capability: String): List<SkillMetadata>
    fun installSkill(skill: SkillMetadata): Result<SkillInstance>
    fun evaluateSkillPerformance(skillId: String): PerformanceReport
}
```

---

## 三、核心模块设计

### 3.1 微内核

```kotlin
interface MingClawKernel {
    suspend fun loadPlugin(pluginId: String): Result<PluginContext>
    suspend fun unloadPlugin(pluginId: String): Result<Unit>
    fun getLoadedPlugins(): List<PluginInfo>
    suspend fun dispatchTask(task: AgentTask): TaskResult
    fun scheduleRecurringTask(task: ScheduledTask): CancellableTask
    fun subscribe(eventType: String, handler: EventHandler): Subscription
    fun publish(event: Event): List<EventResult>
    fun getConfig(): KernelConfig
    fun updateConfig(updates: ConfigUpdates): Result<Unit>
}
```

### 3.2 动态提示构建器

```kotlin
interface DynamicPromptBuilder {
    suspend fun buildSystemPrompt(): SystemPrompt
    suspend fun incrementalUpdate(): SystemPrompt

    data class SystemPrompt(
        val baseInstructions: String,
        val behaviorRules: String,
        val personality: String,
        val relevantMemory: String,
        val availableSkills: String,
        val currentContext: String
    )
}
```

---

## 四、上下文管理

### 4.1 会话上下文管理

```kotlin
interface SessionContextManager {
    suspend fun createSession(
        sessionType: SessionType,
        parentSessionId: String? = null
    ): SessionContext

    suspend fun getSession(sessionId: String): SessionContext?
    suspend fun archiveSession(sessionId: String): Result<ArchivedSession>

    suspend fun appendMessage(
        sessionId: String,
        message: ConversationMessage
    ): Result<Unit>

    suspend fun estimateTokenCount(sessionId: String): TokenUsage
    suspend fun needsCompaction(sessionId: String): Boolean
    suspend fun compactSession(sessionId: String): Result<CompactionResult>

    suspend fun buildContextForLLM(
        sessionId: String,
        maxTokens: Int
    ): LLMContext
}
```

### 4.2 记忆上下文检索

```kotlin
interface MemoryContextManager {
    suspend fun retrieveRelevantMemory(
        query: String,
        maxResults: Int = 10,
        minRelevanceScore: Float = 0.5f
    ): List<MemoryFragment>

    suspend fun hybridSearch(
        semanticQuery: String,
        keywords: List<String>,
        weights: SearchWeights = SearchWeights.DEFAULT
    ): List<MemoryFragment>

    suspend fun getEvergreenMemory(): List<MemoryFragment>
}
```

### 4.3 上下文窗口管理

```kotlin
interface ContextWindowManager {
    fun calculateTokenAllocation(
        contextWindow: Int,
        reserveTokens: Int = 20000
    ): TokenAllocation

    suspend fun trimConversationHistory(
        messages: List<ConversationMessage>,
        targetTokenCount: Int
    ): List<ConversationMessage>

    suspend fun trimMemoryResults(
        memories: List<MemoryFragment>,
        targetTokenCount: Int
    ): List<MemoryFragment>
}
```

---

## 五、插件系统

### 5.1 插件接口

```kotlin
interface MingClawPlugin {
    val pluginId: String
    val version: Version
    val name: String
    val description: String
    val author: String

    suspend fun onInitialize(context: PluginContext): Result<Unit>
    suspend fun onStart(): Result<Unit>
    suspend fun onStop(): Result<Unit>
    suspend fun onUnload(): Result<Unit>

    fun getProvidedTools(): List<ToolDeclaration>
    fun getSubscribedEvents(): List<String>
    fun getDependencies(): List<PluginDependency>
}
```

### 5.2 工具系统

```kotlin
data class ToolDeclaration(
    val toolId: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val parameters: List<ParameterSchema>,
    val handler: ToolHandler,
    val permissions: Set<Permission> = emptySet()
)

interface ToolHandler {
    suspend fun execute(
        context: ToolContext,
        parameters: Map<String, Any>
    ): ToolResult
}
```

---

## 六、记忆管理

### 6.1 记忆存储

```kotlin
interface MemoryStore {
    suspend fun writeLongTermMemory(
        content: String,
        category: MemoryCategory,
        importance: Float = 0.5f
    ): Result<MemoryEntry>

    suspend fun writeDailyLog(
        content: String,
        date: LocalDate = LocalDate.now()
    ): Result<MemoryEntry>

    suspend fun rebuildIndex(): Result<IndexStats>
}
```

### 6.2 向量嵌入

```kotlin
interface EmbeddingService {
    suspend fun embed(text: String): Result<FloatArray>
    suspend fun embedBatch(texts: List<String>): Result<List<FloatArray>>
    fun calculateSimilarity(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float
}
```

### 6.3 混合搜索

```kotlin
interface HybridSearchEngine {
    suspend fun search(
        query: String,
        options: SearchOptions = SearchOptions.DEFAULT
    ): SearchResult
}
```

---

## 七、任务编排引擎

### 7.1 任务执行

```kotlin
interface TaskOrchestrator {
    suspend fun executeTask(task: AgentTask): TaskResult
    suspend fun executeWorkflow(workflow: Workflow): WorkflowResult
    suspend fun executeTaskGraph(graph: TaskGraph): GraphExecutionResult

    suspend fun pauseTask(taskId: String): Result<Unit>
    suspend fun resumeTask(taskId: String): Result<Unit>
    suspend fun cancelTask(taskId: String): Result<Unit>
}
```

### 7.2 工作流定义

```kotlin
data class Workflow(
    val workflowId: String,
    val name: String,
    val description: String,
    val steps: List<WorkflowStep>,
    val errorHandling: ErrorHandlingStrategy
)

data class WorkflowStep(
    val stepId: String,
    val name: String,
    val task: AgentTask,
    val dependencies: Set<String> = emptySet(),
    val condition: StepCondition? = null
)
```

---

## 八、工作区与配置

### 8.1 工作区结构

```
~/.mingclaw/workspace/
├── AGENTS.md           # 行为规则
├── SOUL.md             # 人格设定
├── MEMORY.md           # 长期记忆
├── USER.md             # 用户偏好
├── TOOLS.md            # 工具定义
├── IDENTITY.md         # 身份配置
├── memory/             # 日常记忆目录
├── skills/             # 已安装技能
└── .state/             # 状态文件
```

### 8.2 工作区管理

```kotlin
interface WorkspaceManager {
    suspend fun initializeWorkspace(): Result<Unit>
    fun getWorkspacePath(): File
    fun getWorkspaceStatus(): WorkspaceStatus

    suspend fun readWorkspaceFile(
        fileName: WorkspaceFileName
    ): Result<String>

    suspend fun writeWorkspaceFile(
        fileName: WorkspaceFileName,
        content: String
    ): Result<Unit>
}
```

---

## 九、安全策略

### 9.1 权限管理

```kotlin
interface PermissionManager {
    suspend fun checkPermission(
        subject: Subject,
        permission: Permission,
        resource: Resource? = null
    ): PermissionResult

    suspend fun grantPermission(
        subject: Subject,
        permission: Permission
    ): Result<Unit>
}
```

### 9.2 沙箱隔离

```kotlin
interface SandboxManager {
    suspend fun createSandbox(config: SandboxConfig): Result<Sandbox>
    suspend fun executeInSandbox(
        sandboxId: String,
        code: String,
        timeout: Duration
    ): SandboxExecutionResult
}
```

### 9.3 数据加密

```kotlin
interface EncryptionManager {
    suspend fun encrypt(
        data: ByteArray,
        keyAlias: String
    ): Result<EncryptedData>

    suspend fun decrypt(
        encryptedData: EncryptedData,
        keyAlias: String
    ): Result<ByteArray>
}
```

### 9.4 审计日志

```kotlin
interface AuditLogger {
    suspend fun log(event: AuditEvent): Result<Unit>
    suspend fun query(
        filter: AuditFilter,
        pagination: Pagination
    ): List<AuditEvent>
}
```

---

## 十、技术栈选型

### 10.1 核心技术

| 类别 | 技术 |
|------|------|
| UI | Jetpack Compose, Material Design 3 |
| 架构 | MVVM + Clean Architecture |
| 异步 | Kotlin Coroutines + Flow |
| DI | Hilt |
| 数据库 | Room + SQLite + sqlite-vec |
| 网络 | Retrofit + OkHttp |
| AI API | OpenAI, Gemini |
| 安全 | Android Keystore |

### 10.2 主要依赖

```kotlin
// 核心依赖
implementation("androidx.compose.ui:ui:1.7.0")
implementation("androidx.room:room-ktx:2.6.1")
implementation("com.google.dagger:hilt-android:2.51")
implementation("com.squareup.retrofit2:retrofit:2.11.0")
```

---

## 十一、补充设计

### 11.1 模块间通信协议

插件间通信采用**事件驱动**架构，通过事件总线进行解耦：

```kotlin
/**
 * 事件总线 - 模块间通信的核心
 */
interface EventBus {
    fun subscribe(eventType: String, subscriber: EventSubscriber): Subscription
    fun publish(event: Event): List<EventResult>
    fun publishAsync(event: Event): Job
}

/**
 * 事件定义
 */
sealed class Event {
    val eventId: String = UUID.randomUUID().toString()
    val timestamp: Instant = Instant.now()

    // === 进化事件 ===
    data class BehaviorEvolutionRequested(
        val trigger: EvolutionTrigger
    ) : Event()

    data class KnowledgeConsolidated(
        val knowledgePoints: List<KnowledgePoint>
    ) : Event()

    data class SkillInstalled(
        val skillId: String,
        val version: Version
    ) : Event()

    // === 任务事件 ===
    data class TaskStarted(
        val taskId: String,
        val taskType: TaskType
    ) : Event()

    data class TaskCompleted(
        val taskId: String,
        val result: TaskResult
    ) : Event()

    data class TaskFailed(
        val taskId: String,
        val error: String
    ) : Event()

    // === 记忆事件 ===
    data class MemoryUpdated(
        val entryId: String,
        val changeType: ChangeType
    ) : Event()

    data class MemorySearchRequested(
        val query: String,
        val requester: String
    ) : Event()
}

/**
 * 进化触发条件
 */
enum class EvolutionTrigger {
    USER_FEEDBACK,          // 用户显式反馈
    TASK_FAILURE,           // 任务连续失败
    PERFORMANCE_DEGRADATION, // 性能下降
    SCHEDULED,              // 定期检查
    MANUAL                  // 手动触发
}
```

### 11.2 进化触发机制

进化不会自动触发，而是基于明确的触发条件：

```kotlin
/**
 * 进化触发管理器
 */
interface EvolutionTriggerManager {
    /**
     * 检查是否应该触发进化
     */
    suspend fun shouldTriggerEvolution(
        triggerType: EvolutionTrigger,
        context: EvolutionContext
    ): Boolean

    /**
     * 执行进化分析
     */
    suspend fun performEvolutionAnalysis(): EvolutionAnalysis

    /**
     * 执行进化更新
     */
    suspend fun executeEvolution(
        analysis: EvolutionAnalysis,
        requireApproval: Boolean = true
    ): EvolutionResult
}

/**
 * 进化上下文
 */
data class EvolutionContext(
    val recentTasks: List<TaskResult>,
    val userFeedback: List<UserFeedback>,
    val performanceMetrics: PerformanceMetrics,
    val timeSinceLastEvolution: Duration
)

/**
 * 进化分析结果
 */
data class EvolutionAnalysis(
    val behaviorRecommendations: List<BehaviorRecommendation>,
    val knowledgeRecommendations: List<KnowledgeRecommendation>,
    val capabilityRecommendations: List<CapabilityRecommendation>,
    val estimatedImpact: ImpactLevel
)

enum class ImpactLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}
```

### 11.3 Token计数方法

使用**tiktoken-kotlin**库进行准确的Token计数：

```kotlin
/**
 * Token计数器
 */
interface TokenCounter {
    /**
     * 计算文本的Token数量
     */
    fun countTokens(text: String, model: String = "gpt-4"): Int

    /**
     * 计算消息的Token数量
     */
    fun countMessageTokens(message: ConversationMessage): Int

    /**
     * 估算上下文的Token数量
     */
    fun estimateContextTokens(context: LLMContext): Int

    /**
     * 获取模型的上下文窗口大小
     */
    fun getContextWindow(model: String): Int
}

/**
 * 实现 - 使用tiktoken编码
 */
class TiktokenTokenCounter : TokenCounter {
    private val encodings = mapOf(
        "gpt-4" to TiktokenEncoding.cl100k_base,
        "gpt-3.5-turbo" to TiktokenEncoding.cl100k_base,
        "claude-3" to TiktokenEncoding.cl100k_base
    )

    override fun countTokens(text: String, model: String): Int {
        val encoding = encodings[model] ?: encodings["gpt-4"]!!
        return encoding.encode(text).size
    }
}
```

### 11.4 向量数据库选择

由于Android平台的限制，采用**分层存储策略**：

| 数据类型 | 存储方案 | 说明 |
|---------|---------|------|
| 向量嵌入 | Room BLOB | 存储FloatArray |
| 向量索引 | 内存索引 | 使用Hnswlib |
| 元数据 | Room | 结构化查询 |
| 持久化 | SQLite | 标准Android存储 |

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
        metadata: Map<String, Any>
    ): Result<Unit>

    /**
     * 相似度搜索
     */
    suspend fun searchSimilar(
        queryVector: FloatArray,
        topK: Int = 10,
        threshold: Float = 0.7f
    ): List<VectorSearchResult>
}

/**
 * 实现：内存HNSW索引 + Room持久化
 */
class HybridVectorStore(
    private val database: MingClawDatabase,
    private val hnswIndex: HnswIndex
) : VectorStore {
    // 使用HNSW进行快速近似搜索
    // 使用Room进行持久化和精确查询
}
```

### 11.5 数据迁移策略

```kotlin
/**
 * 版本迁移管理器
 */
interface MigrationManager {
    /**
     * 获取当前版本
     */
    fun getCurrentVersion(): Version

    /**
     * 获取可用迁移
     */
    fun getAvailableMigrations(): List<Migration>

    /**
     * 执行迁移
     */
    suspend fun migrate(
        from: Version,
        to: Version
    ): MigrationResult

    /**
     * 创建备份（迁移前）
     */
    suspend fun createBackup(): BackupInfo
}

/**
 * 迁移定义
 */
data class Migration(
    val fromVersion: Version,
    val toVersion: Version,
    val steps: List<MigrationStep>,
    val estimatedTime: Duration,
    val requiresBackup: Boolean = true
)

interface MigrationStep {
    suspend fun execute(context: MigrationContext): Result<Unit>
    fun rollback(context: MigrationContext): Result<Unit>
}
```

### 11.6 离线模式

```kotlin
/**
 * 离线模式管理器
 */
interface OfflineModeManager {
    /**
     * 检查网络状态
     */
    fun isOnline(): Boolean

    /**
     * 获取当前模式
     */
    fun getMode(): OperationMode

    /**
     * 设置模式
     */
    suspend fun setMode(mode: OperationMode): Result<Unit>

    /**
     * 同步待处理数据（恢复在线时）
     */
    suspend fun syncPendingData(): SyncResult
}

/**
 * 运行模式
 */
enum class OperationMode {
    ONLINE,             // 完全在线
    OFFLINE,            // 完全离线
    HYBRID              // 混合模式（默认）
}

/**
 * 离线能力降级策略
 */
interface OfflineCapability {
    /**
     * 离线时能否执行
     */
    fun canExecuteOffline(): Boolean

    /**
     * 获取离线替代实现
     */
    fun getOfflineFallback(): suspend () -> Any?
}
```

---

## 十二、系统质量保证

### 12.1 性能优化策略

```kotlin
/**
 * 性能优化配置
 */
data class PerformanceConfig(
    // 向量搜索配置
    val vectorSearchIndexSize: Int = 10000,      // HNSW索引大小
    val vectorSearchEfConstruction: Int = 200,  // 构建时精度参数
    val vectorSearchEfSearch: Int = 50,         // 搜索时精度参数

    // 数据库优化
    val databaseWalMode: Boolean = true,         // WAL模式
    val databasePageSize: Int = 4096,            // 页大小
    val databaseCacheSize: Long = 50 * 1024 * 1024, // 缓存大小50MB

    // 内存管理
    val maxMemoryCacheSize: Long = 100 * 1024 * 1024, // 最大内存缓存100MB
    val embeddingBatchSize: Int = 32,            // 批量嵌入大小
    val maxConcurrentEmbeddings: Int = 4,        // 最大并发嵌入数

    // 任务执行优化
    val maxParallelTasks: Int = 3,               // 最大并行任务数
    val taskTimeout: Duration = 5.minutes,       // 任务超时时间
    val taskQueueSize: Int = 100                 // 任务队列大小
)

/**
 * 性能监控接口
 */
interface PerformanceMonitor {
    fun recordOperation(operation: String, duration: Duration)
    fun recordMetric(metric: PerformanceMetric)
    fun getMetrics(): PerformanceMetrics
    fun alertOnThreshold(threshold: PerformanceThreshold)
    fun resetMetrics()
}

/**
 * 性能指标
 */
data class PerformanceMetrics(
    val averageResponseTime: Duration,
    val p95ResponseTime: Duration,
    val p99ResponseTime: Duration,
    val throughput: Double,                      // 每秒处理请求数
    val errorRate: Double,                       // 错误率
    val memoryUsage: MemoryUsage,
    val cpuUsage: Double,
    val diskUsage: DiskUsage
)

/**
 * 性能阈值告警
 */
data class PerformanceThreshold(
    val maxResponseTime: Duration,
    val maxErrorRate: Double,
    val maxMemoryUsage: Long,
    val maxCpuUsage: Double
)

/**
 * 性能指标数据
 */
sealed class PerformanceMetric {
    data class ResponseTime(val operation: String, val duration: Duration) : PerformanceMetric()
    data class Throughput(val operationsPerSecond: Double) : PerformanceMetric()
    data class ErrorRate(val errorCount: Int, val totalOperations: Int) : PerformanceMetric()
    data class MemoryUsage(val used: Long, val max: Long) : PerformanceMetric()
    data class CpuUsage(val percentage: Double) : PerformanceMetric()
}

/**
 * 性能优化建议
 */
interface PerformanceOptimizer {
    suspend fun analyzePerformance(): PerformanceAnalysis
    suspend fun suggestOptimizations(): List<OptimizationSuggestion>
    suspend fun applyOptimization(suggestion: OptimizationSuggestion): Result<Unit>
}

/**
 * 性能分析结果
 */
data class PerformanceAnalysis(
    val bottlenecks: List<Bottleneck>,
    val recommendations: List<OptimizationSuggestion>,
    val estimatedImprovement: EstimatedImprovement
)

data class Bottleneck(
    val component: String,
    val severity: Severity,
    val description: String,
    val impact: String
)

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

data class OptimizationSuggestion(
    val suggestionId: String,
    val title: String,
    val description: String,
    val estimatedImpact: String,
    val implementationComplexity: Complexity,
    val priority: Priority
)

enum class Complexity { LOW, MEDIUM, HIGH }

enum class Priority { LOW, MEDIUM, HIGH, URGENT }

data class EstimatedImprovement(
    val responseTimeImprovement: Percentage,
    val memoryReduction: Percentage,
    val cpuReduction: Percentage
)

@JvmInline
value class Percentage(val value: Double)
```

### 12.2 错误处理与恢复策略

```kotlin
/**
 * 错误分类与处理策略
 */
sealed class MingClawError : Exception {
    // 可恢复错误
    data class NetworkError(
        override val message: String,
        val httpCode: Int? = null
    ) : MingClawError()

    data class TemporaryStorageError(
        override val message: String,
        val retryAfter: Duration? = null
    ) : MingClawError()

    data class RateLimitError(
        override val message: String,
        val retryAfter: Duration,
        val limitType: String
    ) : MingClawError()

    data class QuotaExceededError(
        override val message: String,
        val quotaType: String,
        val resetTime: Instant
    ) : MingClawError()

    data class PluginLoadError(
        override val message: String,
        val pluginId: String,
        val errorDetails: String?
    ) : MingClawError()

    // 不可恢复错误
    data class CorruptedDataError(
        override val message: String,
        val dataPath: String,
        val checksumMismatch: String? = null
    ) : MingClawError()

    data class SecurityViolation(
        override val message: String,
        val violationType: ViolationType,
        val source: String?
    ) : MingClawError()

    data class ConfigurationError(
        override val message: String,
        val configKey: String,
        val invalidValue: String?
    ) : MingClawError()

    enum class ViolationType {
        PERMISSION_DENIED,
        SANDBOX_VIOLATION,
        ENCRYPTION_FAILURE,
        AUTHENTICATION_FAILED
    }
}

/**
 * 错误恢复策略
 */
interface ErrorRecoveryStrategy {
    suspend fun recover(error: MingClawError): RecoveryResult
    fun canRecover(error: MingClawError): Boolean
    fun getRecoveryPlan(error: MingClawError): RecoveryPlan
}

/**
 * 恢复结果
 */
sealed class RecoveryResult {
    data object Success : RecoveryResult()
    data class PartialSuccess(val message: String) : RecoveryResult()
    data class Failed(val reason: String, val fallbackSuggestion: String?) : RecoveryResult()
}

/**
 * 恢复计划
 */
data class RecoveryPlan(
    val steps: List<RecoveryStep>,
    val estimatedDuration: Duration,
    val requiresUserAction: Boolean,
    val dataLossRisk: RiskLevel
)

enum class RiskLevel { NONE, LOW, MEDIUM, HIGH }

/**
 * 数据一致性检查
 */
interface DataConsistencyChecker {
    suspend fun checkMemoryConsistency(): ConsistencyReport
    suspend fun checkPluginIntegrity(): ConsistencyReport
    suspend fun checkWorkspaceIntegrity(): ConsistencyReport
    suspend fun repairInconsistencies(repairMode: RepairMode): RepairResult
}

/**
 * 一致性报告
 */
data class ConsistencyReport(
    val isConsistent: Boolean,
    val issues: List<ConsistencyIssue>,
    val checkedItems: Int,
    val timestamp: Instant
)

data class ConsistencyIssue(
    val severity: Severity,
    val itemType: String,
    val itemId: String,
    val description: String,
    val suggestedAction: String
)

enum class RepairMode {
    AUTO_SAFE,       // 仅自动修复安全的问题
    AUTO_AGGRESSIVE, // 自动修复所有问题
    MANUAL           // 需要手动确认
}

/**
 * 修复结果
 */
data class RepairResult(
    val fixedIssues: Int,
    val failedIssues: Int,
    val skippedIssues: Int,
    val details: List<RepairDetail>
)

data class RepairDetail(
    val itemId: String,
    val status: RepairStatus,
    val message: String
)

enum class RepairStatus { FIXED, FAILED, SKIPPED }

/**
 * 错误日志与分析
 */
interface ErrorAnalytics {
    suspend fun logError(error: MingClawError, context: ErrorContext)
    suspend fun getErrorPatterns(timeRange: TimeRange): List<ErrorPattern>
    suspend fun getSuggestedFixes(errorType: String): List<SuggestedFix>
}

data class ErrorContext(
    val sessionId: String?,
    val task: AgentTask?,
    val timestamp: Instant,
    val deviceInfo: DeviceInfo,
    val additionalInfo: Map<String, Any>
)

data class ErrorPattern(
    val errorType: String,
    val frequency: Int,
    val lastOccurrence: Instant,
    val commonContext: Map<String, Any>,
    val suggestedAction: String
)

data class SuggestedFix(
    val fixId: String,
    val description: String,
    val automated: Boolean,
    val confidence: Double
)
```

### 12.3 测试策略

```kotlin
/**
 * 测试配置
 */
data class TestConfig(
    val unitTestCoverage: Double = 0.80,         // 单元测试覆盖率目标80%
    val integrationTestCoverage: Double = 0.60,  // 集成测试覆盖率目标60%
    val enableUiTests: Boolean = true,
    val enablePerformanceTests: Boolean = true,
    val testTimeout: Duration = 30.seconds
)

/**
 * 测试夹具与工具
 */
interface TestFixture {
    fun createFakeWorkspace(): TestWorkspace
    fun createTestMemoryEntries(count: Int): List<MemoryEntry>
    fun createMockPlugin(): TestPlugin
    fun createTestSession(): TestSession
    fun cleanupTestData()
}

/**
 * 测试工作区
 */
data class TestWorkspace(
    val path: String,
    val isTemporary: Boolean = true,
    val cleanupAfterUse: Boolean = true
)

/**
 * 测试插件
 */
data class TestPlugin(
    val pluginId: String,
    val capabilities: List<String>,
    val testBehavior: TestPluginBehavior
)

enum class TestPluginBehavior {
    NORMAL,              // 正常行为
    SLOW_RESPONSE,       // 慢响应（测试超时）
    THROW_ERROR,         // 抛出错误（测试错误处理）
    RETURN_INVALID_DATA  // 返回无效数据（测试数据验证）
}

/**
 * 进化系统测试工具
 */
interface EvolutionTestHelper {
    suspend fun simulateEvolutionCycle(): EvolutionTestResult
    suspend fun verifyMemoryConsistency(): Boolean
    suspend fun verifyPluginIsolation(): Boolean
    suspend fun testEvolutionTrigger(trigger: EvolutionTrigger): Boolean
    suspend fun measureEvolutionImpact(): EvolutionImpact
}

/**
 * 进化测试结果
 */
data class EvolutionTestResult(
    val behaviorChanges: List<BehaviorChange>,
    val knowledgeAdded: List<KnowledgePoint>,
    val capabilitiesAdded: List<CapabilityGap>,
    val duration: Duration,
    val success: Boolean
)

data class EvolutionImpact(
    val performanceChange: Percentage,
    val memoryUsageChange: Percentage,
    val accuracyChange: Percentage
)

/**
 * 性能测试工具
 */
interface PerformanceTestHelper {
    suspend fun measureResponseTime(operation: suspend () -> Unit): Duration
    suspend fun measureThroughput(operations: Int): Double
    suspend fun stressTest(concurrentUsers: Int): StressTestResult
    suspend fun memoryLeakTest(): MemoryLeakReport
}

data class StressTestResult(
    val successRate: Double,
    val averageResponseTime: Duration,
    val p95ResponseTime: Duration,
    val maxConcurrentOperations: Int,
    val failures: List<FailureDetail>
)

data class FailureDetail(
    val operation: String,
    val error: String,
    val timestamp: Instant
)

data class MemoryLeakReport(
    val hasLeak: Boolean,
    val leakedObjects: Int,
    val leakedMemory: Long,
    val suspectedSources: List<String>
)

/**
 * 集成测试场景
 */
interface IntegrationTestScenarios {
    suspend fun testFullConversationFlow(): TestResult
    suspend fun testPluginInstallationFlow(): TestResult
    suspend fun testMemoryEvolutionFlow(): TestResult
    suspend fun testTaskExecutionFlow(): TestResult
    suspend fun testOfflineModeSwitching(): TestResult
}

data class TestResult(
    val passed: Boolean,
    val duration: Duration,
    val steps: List<TestStep>,
    val failures: List<TestFailure>
)

data class TestStep(
    val stepName: String,
    val passed: Boolean,
    val duration: Duration,
    val details: String
)

data class TestFailure(
    val step: String,
    val expected: String,
    val actual: String,
    val stackTrace: String?
)

/**
 * UI测试工具
 */
interface UiTestHelper {
    suspend fun testChatScreen(): UiTestResult
    suspend fun testSettingsScreen(): UiTestResult
    suspend fun testPluginManagementScreen(): UiTestResult
    suspend fun testTaskMonitorScreen(): UiTestResult
}

data class UiTestResult(
    val passed: Boolean,
    val screenshotPath: String?,
    val interactions: List<UiInteraction>,
    val errors: List<UiError>
)

data class UiInteraction(
    val element: String,
    val action: String,
    val result: String
)

data class UiError(
    val element: String,
    val error: String,
    val screenshotPath: String
)
```

### 12.4 可观测性与监控

```kotlin
/**
 * 遥测数据收集
 */
interface TelemetryCollector {
    suspend fun recordEvent(event: TelemetryEvent)
    suspend fun recordMetric(metric: TelemetryMetric)
    suspend fun recordCrash(crash: CrashReport)
    suspend fun recordUserAction(action: UserAction)
    suspend fun flushEvents(): Result<Unit>
}

/**
 * 遥测事件
 */
sealed class TelemetryEvent {
    val eventId: String = UUID.randomUUID().toString()
    val timestamp: Instant = Instant.now()

    data class AppStarted(
        val version: String,
        val deviceId: String
    ) : TelemetryEvent()

    data class TaskExecuted(
        val taskId: String,
        val taskType: String,
        val duration: Duration,
        val success: Boolean
    ) : TelemetryEvent()

    data class EvolutionTriggered(
        val triggerType: String,
        val evolutionCount: Int
    ) : TelemetryEvent()

    data class PluginLoaded(
        val pluginId: String,
        val loadTime: Duration,
        val success: Boolean
    ) : TelemetryEvent()

    data class MemorySearched(
        val queryLength: Int,
        val resultsCount: Int,
        val searchTime: Duration
    ) : TelemetryEvent()
}

/**
 * 遥测指标
 */
sealed class TelemetryMetric {
    val timestamp: Instant = Instant.now()

    data class PerformanceMetric(
        val name: String,
        val value: Double,
        val unit: String
    ) : TelemetryMetric()

    data class MemoryMetric(
        val used: Long,
        val total: Long,
        val percentage: Double
    ) : TelemetryMetric()

    data class StorageMetric(
        val used: Long,
        val total: Long,
        val breakdown: StorageBreakdown
    ) : TelemetryMetric()

    data class StorageBreakdown(
        val memories: Long,
        val plugins: Long,
        val cache: Long,
        val logs: Long
    )
}

/**
 * 崩溃报告
 */
data class CrashReport(
    val crashId: String,
    val exception: Throwable,
    val stackTrace: String,
    val deviceInfo: DeviceInfo,
    val appState: AppState,
    val timestamp: Instant,
    val reproducible: Boolean?
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val totalMemory: Long,
    val availableMemory: Long,
    val cpuInfo: String
)

data class AppState(
    val sessionId: String?,
    val currentTask: String?,
    val loadedPlugins: List<String>,
    val memoryUsage: Long
)

/**
 * 用户操作记录
 */
data class UserAction(
    val actionId: String,
    val actionType: ActionType,
    val target: String,
    val context: Map<String, Any>,
    val timestamp: Instant
)

enum class ActionType {
    CLICK, SCROLL, INPUT, SWIPE, PINCH, VOICE_COMMAND
}

/**
 * 调试接口
 */
interface DebugInterface {
    fun dumpSessionState(): SessionDump
    fun dumpMemoryIndex(): MemoryIndexDump
    fun dumpPluginStatus(): PluginStatusDump
    fun dumpTaskQueue(): TaskQueueDump
    fun enableVerboseLogging(enabled: Boolean)
    fun setDebugLevel(level: DebugLevel)
    fun exportDebugLogs(): File
}

data class SessionDump(
    val sessionId: String,
    val messageCount: Int,
    val tokenUsage: TokenUsage,
    val contextWindow: Int,
    val timestamp: Instant
)

data class MemoryIndexDump(
    val totalMemories: Int,
    val indexedMemories: Int,
    val indexSize: Long,
    val lastIndexTime: Instant,
    val indexStats: IndexStats
)

data class PluginStatusDump(
    val loadedPlugins: List<PluginStatus>,
    val totalPlugins: Int,
    val activePlugins: Int,
    val failedPlugins: Int
)

data class PluginStatus(
    val pluginId: String,
    val version: String,
    val status: PluginStatusType,
    val loadTime: Duration?,
    val memoryUsage: Long?
)

enum class PluginStatusType {
    LOADED, LOADING, UNLOADED, FAILED, DISABLED
}

data class TaskQueueDump(
    val pendingTasks: Int,
    val runningTasks: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val tasks: List<TaskDump>
)

data class TaskDump(
    val taskId: String,
    val type: String,
    val status: String,
    val created: Instant,
    val duration: Duration?
)

enum class DebugLevel { NONE, ERROR, WARN, INFO, DEBUG, VERBOSE }

/**
 * 日志管理
 */
interface LogManager {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)
    fun getLogs(filter: LogFilter): List<LogEntry>
    fun clearLogs()
    fun exportLogs(format: ExportFormat): File
}

enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Instant,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: String?
)

data class LogFilter(
    val minLevel: LogLevel = LogLevel.VERBOSE,
    val startTime: Instant? = null,
    val endTime: Instant? = null,
    val tag: String? = null,
    val limit: Int = 1000
)
```

### 12.5 Android特定优化

```kotlin
/**
 * 电池优化策略
 */
interface BatteryOptimizationManager {
    fun scheduleWorkDuringCharging(task: Task)
    fun pauseNonCriticalTasks(batteryLevel: Int)
    fun getOptimalSchedule(): Schedule
    fun enablePowerSavingMode(enabled: Boolean)
    fun getBatteryOptimizationSuggestions(): List<BatterySuggestion>
}

/**
 * 电池建议
 */
data class BatterySuggestion(
    val suggestionId: String,
    val title: String,
    val description: String,
    val estimatedSavings: Percentage,
    val impact: String
)

/**
 * 后台任务调度
 */
interface BackgroundTaskScheduler {
    fun scheduleSync(config: SyncConfig)
    fun scheduleMemoryIndexing(config: IndexingConfig)
    fun scheduleEvolutionCheck(config: EvolutionCheckConfig)
    fun scheduleDataCleanup(config: CleanupConfig)
    fun cancelScheduledWork(workId: String)
    fun getScheduledWork(): List<ScheduledWorkInfo>
}

/**
 * 同步配置
 */
data class SyncConfig(
    val workId: String = "sync_work",
    val interval: Duration = 6.hours,
    val requiresCharging: Boolean = false,
    val requiresNetworkType: NetworkType = NetworkType.CONNECTED,
    val constraints: WorkConstraints = WorkConstraints.DEFAULT
)

enum class NetworkType {
    NOT_REQUIRED, CONNECTED, UNMETERED, NOT_ROAMING, METERED
}

data class WorkConstraints(
    val requiresDeviceIdle: Boolean = false,
    val requiresCharging: Boolean = false,
    val batteryNotLow: Boolean = false,
    val storageNotLow: Boolean = true
) {
    companion object {
        val DEFAULT = WorkConstraints()
    }
}

/**
 * 索引配置
 */
data class IndexingConfig(
    val workId: String = "indexing_work",
    val interval: Duration = 24.hours,
    val requiresCharging: Boolean = true,
    val requiresDeviceIdle: Boolean = true
)

/**
 * 进化检查配置
 */
data class EvolutionCheckConfig(
    val workId: String = "evolution_check_work",
    val interval: Duration = 12.hours,
    val requiresCharging: Boolean = false,
    val requiresNetworkType: NetworkType = NetworkType.CONNECTED
)

/**
 * 清理配置
 */
data class CleanupConfig(
    val workId: String = "cleanup_work",
    val interval: Duration = 7.days,
    val requiresCharging: Boolean = false
)

/**
 * 计划工作信息
 */
data class ScheduledWorkInfo(
    val workId: String,
    val state: WorkState,
    val nextRunTime: Instant?,
    val lastRunTime: Instant?,
    val constraints: WorkConstraints
)

enum class WorkState {
    ENQUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED, BLOCKED
}

/**
 * 生命周期感知组件
 */
interface LifecycleAwareComponent {
    fun onAppForeground()
    fun onAppBackground()
    fun onLowMemory()
    fun onTrimMemory(level: TrimMemoryLevel)
}

enum class TrimMemoryLevel {
    UI_HIDDEN,
    BACKGROUND,
    MODERATE,
    CRITICAL,
    COMPLETE
}

/**
 * Doze模式适配
 */
interface DozeModeAdapter {
    fun isDeviceIdle(): Boolean
    fun isAppInIdleMode(): Boolean
    fun requestIdleModeWhitelist(): Boolean
    fun scheduleWorkAroundDoze(task: Task)
}

/**
 * App Standby适配
 */
interface AppStandbyAdapter {
    fun isAppStandbyEnabled(): Boolean
    fun requestStandbyExemption(): Boolean
    fun adjustBehaviorForStandby(isStandby: Boolean)
}
```

### 12.6 版本兼容性

```kotlin
/**
 * 版本兼容性管理
 */
interface VersionCompatibilityManager {
    fun checkPluginCompatibility(plugin: PluginMetadata): CompatibilityResult
    fun checkDataSchemaVersion(schemaVersion: Version): CompatibilityResult
    fun checkWorkspaceCompatibility(workspacePath: String): CompatibilityResult
    suspend fun migrateUserDataIfNeeded(): MigrationResult
    fun getRequiredMigration(): Migration?
}

/**
 * 兼容性结果
 */
sealed class CompatibilityResult {
    data object Compatible : CompatibilityResult()
    data class Incompatible(
        val reason: String,
        val requiredVersion: Version?,
        val suggestedAction: String
    ) : CompatibilityResult()
    data class RequiresMigration(
        val migration: Migration,
        val estimatedTime: Duration
    ) : CompatibilityResult()
}

/**
 * 向后兼容适配器
 */
interface BackwardCompatibilityAdapter {
    fun adaptOldMemoryFormat(oldData: OldMemoryFormat): MemoryEntry
    fun adaptOldPluginFormat(oldData: OldPluginFormat): PluginMetadata
    fun adaptOldWorkspaceFormat(oldData: OldWorkspaceFormat): WorkspaceConfig
    fun getSupportedVersions(): List<Version>
    fun getLatestVersion(): Version
}

/**
 * 旧数据格式（示例）
 */
data class OldMemoryFormat(
    val content: String,
    val timestamp: Long,
    val tags: List<String>?,
    val importance: Float?
)

data class OldPluginFormat(
    val id: String,
    val name: String,
    val version: String,
    val capabilities: List<String>?
)

data class OldWorkspaceFormat(
    val agents: String?,
    val memory: String?,
    val settings: Map<String, Any>?
)

/**
 * 数据迁移器
 */
interface DataMigrator {
    suspend fun migrateFromV1ToV2(): MigrationResult
    suspend fun migrateFromV2ToV3(): MigrationResult
    suspend fun getMigrationPath(from: Version, to: Version): MigrationPath
    suspend fun executeMigration(path: MigrationPath): MigrationResult
}

/**
 * 迁移路径
 */
data class MigrationPath(
    val steps: List<MigrationStep>,
    val estimatedTime: Duration,
    val requiresBackup: Boolean,
    val rollbackSupported: Boolean
)

/**
 * 迁移步骤
 */
interface MigrationStep {
    val stepId: String
    val description: String
    suspend fun execute(): Result<Unit>
    suspend fun rollback(): Result<Unit>
    fun canRollback(): Boolean
}

/**
 * 迁移结果
 */
data class MigrationResult(
    val success: Boolean,
    val migratedItems: Int,
    val failedItems: Int,
    val duration: Duration,
    val errors: List<MigrationError>
)

data class MigrationError(
    val step: String,
    val item: String,
    val error: String
)
```

### 12.7 导入/导出功能

```kotlin
/**
 * 数据导出接口
 */
interface DataExporter {
    suspend fun exportMemory(format: ExportFormat, filter: ExportFilter? = null): ExportResult
    suspend fun exportWorkspace(format: ExportFormat): ExportResult
    suspend fun exportEvolutionHistory(format: ExportFormat): ExportResult
    suspend fun exportPluginConfigurations(format: ExportFormat): ExportResult
    suspend fun exportFullBackup(format: ExportFormat): ExportResult
}

/**
 * 导出格式
 */
enum class ExportFormat {
    JSON,           // 结构化JSON
    MARKDOWN,       // Markdown文档
    CSV,            // CSV表格
    SQLITE,         // SQLite数据库
    TAR_GZ          // 压缩包
}

/**
 * 导出过滤器
 */
data class ExportFilter(
    val dateRange: DateRange? = null,
    val categories: Set<MemoryCategory>? = null,
    val minImportance: Float? = null,
    val tags: Set<String>? = null,
    val maxItems: Int? = null
)

data class DateRange(
    val startDate: LocalDate,
    val endDate: LocalDate
)

/**
 * 导出结果
 */
data class ExportResult(
    val success: Boolean,
    val file: File?,
    val itemCount: Int,
    val fileSize: Long,
    val format: ExportFormat,
    val duration: Duration,
    val errors: List<ExportError> = emptyList()
)

data class ExportError(
    val itemId: String,
    val error: String
)

/**
 * 数据导入接口
 */
interface DataImporter {
    suspend fun importMemory(data: ByteArray, format: ExportFormat): ImportResult
    suspend fun importWorkspace(data: ByteArray, format: ExportFormat): ImportResult
    suspend fun importEvolutionHistory(data: ByteArray, format: ExportFormat): ImportResult
    suspend fun importFullBackup(data: ByteArray, format: ExportFormat): ImportResult
    suspend fun validateImportData(data: ByteArray, format: ExportFormat): ValidationResult
    suspend fun previewImportData(data: ByteArray, format: ExportFormat): ImportPreview
}

/**
 * 导入结果
 */
data class ImportResult(
    val success: Boolean,
    val importedItems: Int,
    val skippedItems: Int,
    val failedItems: Int,
    val duration: Duration,
    val conflicts: List<ImportConflict>,
    val warnings: List<ImportWarning>
)

/**
 * 导入冲突
 */
data class ImportConflict(
    val itemType: String,
    val itemId: String,
    val conflictType: ConflictType,
    val description: String,
    val resolutionOptions: List<ConflictResolution>
)

enum class ConflictType {
    DUPLICATE_ID,           // ID重复
    VERSION_MISMATCH,       // 版本不匹配
    SCHEMA_INCOMPATIBLE,    // 结构不兼容
    DEPENDENCY_MISSING,     // 缺少依赖
    DATA_CORRUPTED          // 数据损坏
}

data class ConflictResolution(
    val resolutionType: ResolutionType,
    val description: String
)

enum class ResolutionType {
    SKIP,              // 跳过
    OVERWRITE,         // 覆盖
    MERGE,             // 合并
    RENAME,            // 重命名
    CREATE_NEW         // 创建新项
}

/**
 * 导入警告
 */
data class ImportWarning(
    val itemType: String,
    val itemId: String,
    val warning: String,
    val severity: Severity
)

/**
 * 验证结果
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<ValidationWarning>
)

data class ValidationError(
    val field: String,
    val error: String,
    val location: String?
)

data class ValidationWarning(
    val field: String,
    val warning: String
)

/**
 * 导入预览
 */
data class ImportPreview(
    val totalItems: Int,
    val itemTypeCounts: Map<String, Int>,
    val estimatedSize: Long,
    val estimatedDuration: Duration,
    val potentialConflicts: List<ImportConflict>,
    val requirements: ImportRequirements
)

data class ImportRequirements(
    val requiredStorage: Long,
    val requiredPlugins: List<String>,
    val requiredPermissions: List<String>,
    val compatibilityVersion: Version?
)
```

### 12.8 本地LLM集成（可选功能）

```kotlin
/**
 * 本地LLM支持
 */
interface LocalLlmService {
    suspend fun initialize(modelPath: String, config: LlmConfig): Result<Unit>
    suspend fun generate(prompt: String, options: GenerationOptions): LlmResponse
    suspend fun generateStream(prompt: String, options: GenerationOptions): Flow<LlmChunk>
    fun getCapabilities(): LlmCapabilities
    suspend fun estimateResourceUsage(prompt: String): ResourceEstimate
    suspend fun unloadModel()
    fun isModelLoaded(): Boolean
}

/**
 * LLM配置
 */
data class LlmConfig(
    val modelType: ModelType,
    val contextLength: Int = 2048,
    val threads: Int = 4,
    val gpuLayers: Int = 0,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 512
)

enum class ModelType {
    LLAMA_3_8B,           // Llama 3 8B
    LLAMA_3_70B,          // Llama 3 70B
    MISTRAL_7B,           // Mistral 7B
    GEMMA_2B,             // Gemma 2B
    PHI_3,                // Phi-3
    QWEN_7B,              // 通义千问 7B
    CUSTOM                // 自定义模型
}

/**
 * 生成选项
 */
data class GenerationOptions(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxTokens: Int = 512,
    val stopTokens: List<String> = emptyList(),
    val presencePenalty: Float = 0f,
    val frequencyPenalty: Float = 0f
)

/**
 * LLM响应
 */
data class LlmResponse(
    val text: String,
    val tokensGenerated: Int,
    val duration: Duration,
    val resourceUsage: ResourceUsage
)

/**
 * LLM流式输出
 */
data class LlmChunk(
    val text: String,
    val isComplete: Boolean,
    val tokensSoFar: Int
)

/**
 * LLM能力
 */
data class LlmCapabilities(
    val maxContextLength: Int,
    val supportedLanguages: List<String>,
    val hasStreamingSupport: Boolean,
    val recommendedBatchSize: Int,
    val estimatedMemoryUsage: Long
)

/**
 * 资源估算
 */
data class ResourceEstimate(
    val memoryUsage: Long,
    val estimatedDuration: Duration,
    val batteryImpact: BatteryImpact
)

enum class BatteryImpact {
    NEGLIGIBLE, LOW, MEDIUM, HIGH
)

/**
 * 资源使用情况
 */
data class ResourceUsage(
    val memoryUsed: Long,
    val peakMemory: Long,
    val cpuUsage: Double,
    val gpuUsage: Double,
    val duration: Duration
)

/**
 * 混合LLM策略
 */
interface HybridLlmStrategy {
    suspend fun selectBestLlmForTask(task: AgentTask): LlmProvider
    suspend fun fallbackToCloud(localError: Error): LlmResponse
    suspend fun estimateCloudCost(task: AgentTask): CostEstimate
    fun getCurrentStrategy(): StrategyMode
    fun setStrategyMode(mode: StrategyMode)
}

/**
 * LLM提供者
 */
sealed class LlmProvider {
    data class Local(val modelType: ModelType) : LlmProvider()
    data class Cloud(val provider: String, val model: String) : LlmProvider()
}

/**
 * 策略模式
 */
enum class StrategyMode {
    LOCAL_ONLY,           // 仅使用本地
    CLOUD_ONLY,           // 仅使用云端
    LOCAL_PREFERRED,      // 优先本地，回退云端
    CLOUD_PREFERRED,      // 优先云端，回退本地
    AUTO                  // 自动选择
}

/**
 * 成本估算
 */
data class CostEstimate(
    val estimatedTokens: Int,
    val estimatedCost: Double,
    val currency: String = "USD"
)
```

### 12.9 性能评估体系

```kotlin
/**
 * 性能评估管理器
 */
interface PerformanceEvaluationManager {
    suspend fun evaluateOverallPerformance(): OverallPerformanceReport
    suspend fun evaluateEvolutionEffectiveness(): EvolutionEffectivenessReport
    suspend fun evaluateTaskPerformance(): TaskPerformanceReport
    suspend fun evaluateMemoryRetrieval(): MemoryRetrievalReport
    suspend fun evaluateSystemResources(): SystemResourceReport
    suspend fun evaluateUserExperience(): UserExperienceReport
    suspend fun generatePerformanceTrends(timeRange: TimeRange): PerformanceTrends
}

/**
 * 整体性能报告
 */
data class OverallPerformanceReport(
    val timestamp: Instant,
    val overallScore: PerformanceScore,
    val componentScores: Map<String, PerformanceScore>,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val recommendations: List<PerformanceRecommendation>
)

/**
 * 性能评分
 */
data class PerformanceScore(
    val score: Double,           // 0-100
    val grade: Grade,            // A-F
    val trend: Trend
)

enum class Grade {
    A_PLUS, A, A_MINUS,
    B_PLUS, B, B_MINUS,
    C_PLUS, C, C_MINUS,
    D, F
}

enum class Trend { IMPROVING, STABLE, DECLINING }

/**
 * 性能建议
 */
data class PerformanceRecommendation(
    val priority: Priority,
    val category: RecommendationCategory,
    val title: String,
    val description: String,
    val expectedImpact: String,
    val implementationEffort: Complexity
)

enum class RecommendationCategory {
    PERFORMANCE,       // 性能优化
    STABILITY,         // 稳定性改进
    USABILITY,         // 易用性提升
    RESOURCE_USAGE,    // 资源使用
    EVOLUTION          // 进化效果
}

/**
 * 进化效果评估报告
 */
data class EvolutionEffectivenessReport(
    val timestamp: Instant,
    val evolutionCount: Int,
    val behaviorImprovements: BehaviorImprovementMetrics,
    val knowledgeGrowth: KnowledgeGrowthMetrics,
    val capabilityExpansion: CapabilityExpansionMetrics,
    val overallEffectiveness: EffectivenessScore
)

/**
 * 行为改进指标
 */
data class BehaviorImprovementMetrics(
    val ruleUpdates: Int,
    val positiveFeedbacks: Int,
    val negativeFeedbacks: Int,
    val taskSuccessRateChange: Percentage,
    val userSatisfactionChange: Percentage
)

/**
 * 知识增长指标
 */
data class KnowledgeGrowthMetrics(
    val knowledgePointsAdded: Int,
    val totalKnowledgePoints: Int,
    val knowledgeRetrievalSuccessRate: Double,
    val averageRelevanceScore: Double,
    val knowledgeCategories: Map<String, Int>
)

/**
 * 能力扩展指标
 */
data class CapabilityExpansionMetrics(
    val skillsInstalled: Int,
    val skillsUsed: Int,
    val skillSuccessRate: Double,
    val mostUsedSkills: List<SkillUsageStats>,
    val unusedSkills: List<String>
)

data class SkillUsageStats(
    val skillId: String,
    val usageCount: Int,
    val successRate: Double,
    val averageExecutionTime: Duration
)

/**
 * 效果评分
 */
data class EffectivenessScore(
    val behaviorScore: Double,
    val knowledgeScore: Double,
    val capabilityScore: Double,
    val overallScore: Double,
    val rating: Rating
)

enum class Rating {
    EXCELLENT, GOOD, SATISFACTORY, NEEDS_IMPROVEMENT, POOR
}

/**
 * 任务性能报告
 */
data class TaskPerformanceReport(
    val timestamp: Instant,
    val totalTasks: Int,
    val completedTasks: Int,
    val failedTasks: Int,
    val averageCompletionTime: Duration,
    val p95CompletionTime: Duration,
    val p99CompletionTime: Duration,
    val taskTypeBreakdown: Map<String, TaskTypeMetrics>,
    val commonFailures: List<FailureAnalysis>
)

/**
 * 任务类型指标
 */
data class TaskTypeMetrics(
    val taskType: String,
    val count: Int,
    val successRate: Double,
    val averageDuration: Duration,
    val averageTokenUsage: Int,
    val averageCost: Double?
)

/**
 * 失败分析
 */
data class FailureAnalysis(
    val taskType: String,
    val failureCount: Int,
    val failureRate: Double,
    val commonErrors: List<ErrorFrequency>,
    val suggestedFixes: List<String>
)

data class ErrorFrequency(
    val error: String,
    val frequency: Int,
    val percentage: Double
)

/**
 * 记忆检索报告
 */
data class MemoryRetrievalReport(
    val timestamp: Instant,
    val totalSearches: Int,
    val averageSearchTime: Duration,
    val p95SearchTime: Duration,
    val averageResultsCount: Int,
    val averageRelevanceScore: Double,
    val semanticSearchAccuracy: Double,
    val keywordSearchAccuracy: Double,
    val indexSize: Long,
    val indexEfficiency: IndexEfficiency
)

/**
 * 索引效率
 */
data class IndexEfficiency(
    val indexingTime: Duration,
    val searchSpeed: Double,              // 搜索速度（次/秒）
    val recallRate: Double,               // 召回率
    val precisionRate: Double,            // 精确率
    val storageOverhead: Percentage       // 存储开销
)

/**
 * 系统资源报告
 */
data class SystemResourceReport(
    val timestamp: Instant,
    val memoryUsage: MemoryUsageReport,
    val cpuUsage: CpuUsageReport,
    val storageUsage: StorageUsageReport,
    val batteryUsage: BatteryUsageReport,
    val networkUsage: NetworkUsageReport
)

/**
 * 内存使用报告
 */
data class MemoryUsageReport(
    val averageUsage: Long,
    val peakUsage: Long,
    val maxAvailable: Long,
    val usagePercentage: Double,
    val breakdown: MemoryBreakdown
)

data class MemoryBreakdown(
    val appBase: Long,
    val vectorIndex: Long,
    val cache: Long,
    val plugins: Long,
    val other: Long
)

/**
 * CPU使用报告
 */
data class CpuUsageReport(
    val averageUsage: Double,
    val peakUsage: Double,
    val usageByComponent: Map<String, Double>
)

/**
 * 存储使用报告
 */
data class StorageUsageReport(
    val totalUsed: Long,
    val totalAvailable: Long,
    val usagePercentage: Double,
    val breakdown: StorageBreakdown
)

/**
 * 电池使用报告
 */
data class BatteryUsageReport(
    val averageDrain: Double,              // 每小时耗电百分比
    val whileIdleDrain: Double,            // 空闲时耗电
    val whileActiveDrain: Double,          // 活动时耗电
    val estimatedBatteryLife: Duration,    // 估计续航时间
    val majorConsumers: List<BatteryConsumer>
)

data class BatteryConsumer(
    val component: String,
    val consumption: Double,
    val percentage: Double
)

/**
 * 网络使用报告
 */
data class NetworkUsageReport(
    val totalBytesSent: Long,
    val totalBytesReceived: Long,
    val averageRequestSize: Long,
    val averageResponseSize: Long,
    val requestCount: Int,
    val apiCallBreakdown: Map<String, ApiUsageStats>
)

data class ApiUsageStats(
    val endpoint: String,
    val callCount: Int,
    val totalBytes: Long,
    val averageLatency: Duration,
    val errorRate: Double
)

/**
 * 用户体验报告
 */
data class UserExperienceReport(
    val timestamp: Instant,
    val averageSessionDuration: Duration,
    val averageResponseTime: Duration,
    val userSatisfactionScore: Double,     // 1-5星
    val featureUsage: Map<String, FeatureUsage>,
    val userFeedback: List<UserFeedback>,
    val commonPainPoints: List<String>
)

/**
 * 功能使用情况
 */
data class FeatureUsage(
    val featureName: String,
    val usageCount: Int,
    val uniqueUsers: Int,
    val averageSessionTime: Duration,
    val errorRate: Double,
    val userRating: Double
)

/**
 * 用户反馈
 */
data class UserFeedback(
    val feedbackId: String,
    val category: FeedbackCategory,
    val rating: Int,
    val comment: String,
    val timestamp: Instant
)

enum class FeedbackCategory {
    BUG_REPORT,           // 错误报告
    FEATURE_REQUEST,      // 功能请求
    USABILITY,            // 易用性问题
    PERFORMANCE,          // 性能问题
    GENERAL               // 一般反馈
}

/**
 * 性能趋势
 */
data class PerformanceTrends(
    val timeRange: TimeRange,
    val overallTrend: Trend,
    val componentTrends: Map<String, Trend>,
    val significantChanges: List<SignificantChange>,
    val forecast: PerformanceForecast
)

/**
 * 显著变化
 */
data class SignificantChange(
    val component: String,
    val changeType: ChangeType,
    val magnitude: Double,
    val timestamp: Instant,
    val likelyCause: String
)

enum class ChangeType {
    IMPROVEMENT,         // 改进
    DEGRADATION,         // 退化
    ANOMALY              // 异常
}

/**
 * 性能预测
 */
data class PerformanceForecast(
    val expectedTrend: Trend,
    val confidenceLevel: Double,
    val potentialIssues: List<PotentialIssue>,
    val recommendations: List<String>
)

data class PotentialIssue(
    val issue: String,
    val probability: Double,
    val expectedImpact: String,
    val mitigation: String
)

/**
 * 时间范围
 */
data class TimeRange(
    val startTime: Instant,
    val endTime: Instant
)

/**
 * 性能基准测试
 */
interface PerformanceBenchmark {
    suspend fun runBenchmarks(): BenchmarkResults
    suspend fun compareWithBaseline(): BenchmarkComparison
    suspend fun trackPerformanceOverTime(): List<PerformanceSnapshot>
}

/**
 * 基准测试结果
 */
data class BenchmarkResults(
    val timestamp: Instant,
    val benchmarks: Map<String, BenchmarkMetric>,
    val overallScore: Double,
    val deviceInfo: DeviceInfo
)

data class BenchmarkMetric(
    val name: String,
    val value: Double,
    val unit: String,
    val better: Direction
)

enum class Direction { HIGHER_IS_BETTER, LOWER_IS_BETTER }

/**
 * 基准对比
 */
data class BenchmarkComparison(
    val current: BenchmarkResults,
    val baseline: BenchmarkResults,
    val differences: Map<String, MetricDifference>
)

data class MetricDifference(
    val metric: String,
    val absoluteDifference: Double,
    val percentageChange: Percentage,
    val isImprovement: Boolean
)

/**
 * 性能快照
 */
data class PerformanceSnapshot(
    val timestamp: Instant,
    val metrics: Map<String, Double>,
    val annotations: List<String>
)
```

---

---

## 附录

### A. 项目模块结构（符合NowInAndroid规范）

```
E:/ai/superAI/MingClaw/
├── app/                                    # App模块 - 入口点
│   ├── src/main/
│   │   ├── kotlin/com/loy/mingclaw/
│   │   │   ├── MainActivity.kt
│   │   │   ├── MingClawApp.kt           # 应用入口
│   │   │   └── navigation/
│   │   │       └── MingClawNavHost.kt   # 导航设置
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
│
├── build-logic/                            # 构建逻辑
│   └── convention/
│       └── src/main/kotlin/
│           └── com/loy/mingclaw/buildlogic/
│               ├── AndroidApplicationConventionPlugin.kt
│               ├── AndroidLibraryConventionPlugin.kt
│               ├── AndroidFeatureConventionPlugin.kt
│               └── HiltConventionPlugin.kt
│
├── feature/                                # 功能模块
│   ├── chat/                               # 聊天功能
│   │   ├── api/                            # 公共API
│   │   │   ├── build.gradle.kts
│   │   │   └── src/main/kotlin/com/loy/mingclaw/feature/chat/api/
│   │   │       └── ChatNavigation.kt
│   │   └── impl/                           # 内部实现
│   │       ├── build.gradle.kts
│   │       └── src/main/kotlin/com/loy/mingclaw/feature/chat/impl/
│   │           ├── ChatScreen.kt
│   │           ├── ChatViewModel.kt
│   │           ├── ChatUiState.kt
│   │           └── di/ChatModule.kt
│   │
│   ├── task-monitor/                       # 任务监控
│   │   ├── api/
│   │   └── impl/
│   │
│   ├── plugin-manager/                     # 插件管理
│   │   ├── api/
│   │   └── impl/
│   │
│   └── settings/                           # 设置
│       ├── api/
│       └── impl/
│
├── core/                                   # 核心模块
│   ├── model/                              # 领域模型（纯Kotlin）
│   │   ├── AgentTask.kt
│   │   ├── ConversationMessage.kt
│   │   ├── MemoryEntry.kt
│   │   ├── PluginInfo.kt
│   │   └── Workflow.kt
│   │
│   ├── data/                               # 数据层（Repository）
│   │   ├── repository/
│   │   │   ├── SessionRepository.kt
│   │   │   ├── MemoryRepository.kt
│   │   │   ├── PluginRepository.kt
│   │   │   └── TaskRepository.kt
│   │   ├── local/
│   │   │   ├── dao/
│   │   │   │   ├── SessionDao.kt
│   │   │   │   ├── MemoryDao.kt
│   │   │   │   └── PluginDao.kt
│   │   │   ├── entity/
│   │   │   │   ├── SessionEntity.kt
│   │   │   │   ├── MemoryEntity.kt
│   │   │   │   └── PluginEntity.kt
│   │   │   └── MingClawDatabase.kt
│   │   ├── remote/
│   │   │   ├── network/
│   │   │   │   ├── LlmApi.kt
│   │   │   │   └── dto/
│   │   │   └── datasource/
│   │   │       ├── LlmRemoteDataSource.kt
│   │   │       └── PluginRemoteDataSource.kt
│   │   └── di/DataModule.kt
│   │
│   ├── database/                           # 数据库配置
│   │   └── room/
│   │       └── Converters.kt
│   │
│   ├── network/                            # 网络层
│   │   ├── api/
│   │   │   ├── OpenAiApi.kt
│   │   │   └── SkillHubApi.kt
│   │   ├── dto/
│   │   │   ├── ChatCompletionRequest.kt
│   │   │   ├── ChatCompletionResponse.kt
│   │   │   └── SkillMetadataDto.kt
│   │   └── di/NetworkModule.kt
│   │
│   ├── datastore/                          # 数据存储（用户偏好）
│   │   └── MingClawPreferencesDataSource.kt
│   │
│   ├── common/                             # 通用工具
│   │   ├── dispatchers/
│   │   │   └── DispatcherProvider.kt
│   │   ├── network/
│   │   │   └── NetworkMonitor.kt
│   │   └── result/
│   │       └── Result.kt
│   │
│   ├── ui/                                 # 可复用UI组件
│   │   ├── components/
│   │   │   ├── MessageBubble.kt
│   │   │   ├── TaskCard.kt
│   │   │   └── PluginCard.kt
│   │   └── theme/
│   │
│   ├── designsystem/                       # 设计系统
│   │   ├── theme/
│   │   │   ├── Color.kt
│   │   │   ├── Theme.kt
│   │   │   └── Type.kt
│   │   └── icon/
│   │       └── MingClawIcons.kt
│   │
│   ├── kernel/                             # 微内核
│   │   ├── MingClawKernel.kt
│   │   ├── PluginRegistry.kt
│   │   ├── EventBus.kt
│   │   └── di/KernelModule.kt
│   │
│   ├── evolution/                          # 进化引擎
│   │   ├── behavior/
│   │   │   ├── BehaviorEvolver.kt
│   │   │   └── FeedbackCollector.kt
│   │   ├── knowledge/
│   │   │   ├── KnowledgeEvolver.kt
│   │   │   └── MemoryExtractor.kt
│   │   ├── capability/
│   │   │   ├── CapabilityEvolver.kt
│   │   │   └── SkillInstaller.kt
│   │   └── di/EvolutionModule.kt
│   │
│   ├── context/                            # 上下文管理
│   │   ├── session/
│   │   │   ├── SessionContextManager.kt
│   │   │   └── SessionCompactor.kt
│   │   ├── memory/
│   │   │   ├── MemoryContextManager.kt
│   │   │   └── MemoryRetriever.kt
│   │   └── window/
│   │       └── ContextWindowManager.kt
│   │
│   ├── task/                               # 任务编排
│   │   ├── orchestrator/
│   │   │   └── TaskOrchestrator.kt
│   │   ├── workflow/
│   │   │   ├── WorkflowExecutor.kt
│   │   │   └── WorkflowDefinition.kt
│   │   └── executor/
│   │       └── TaskExecutor.kt
│   │
│   ├── workspace/                          # 工作区管理
│   │   ├── WorkspaceManager.kt
│   │   ├── WorkspaceTemplate.kt
│   │   └── FileWatcher.kt
│   │
│   ├── security/                           # 安全模块
│   │   ├── permission/
│   │   │   └── PermissionManager.kt
│   │   ├── sandbox/
│   │   │   └── SandboxManager.kt
│   │   ├── encryption/
│   │   │   └── EncryptionManager.kt
│   │   └── audit/
│   │       └── AuditLogger.kt
│   │
│   └── testing/                            # 测试工具
│       ├── util/
│       │   ├── MainDispatcherRule.kt
│       │   └── TestDispatcherProvider.kt
│       ├── fake/
│       │   ├── FakeSessionRepository.kt
│       │   ├── FakeMemoryRepository.kt
│       │   └── FakeLlmApi.kt
│       └── helpers/
│           ├── EvolutionTestHelper.kt
│           ├── PerformanceTestHelper.kt
│           └── IntegrationTestScenarios.kt
│
├── core/quality/                           # 质量保证（新增）
│   ├── performance/                        # 性能管理
│   │   ├── PerformanceMonitor.kt
│   │   ├── PerformanceOptimizer.kt
│   │   ├── PerformanceConfig.kt
│   │   └── PerformanceBenchmark.kt
│   │
│   ├── evaluation/                         # 性能评估
│   │   ├── PerformanceEvaluationManager.kt
│   │   ├── EvolutionEffectivenessEvaluator.kt
│   │   ├── TaskPerformanceEvaluator.kt
│   │   ├── MemoryRetrievalEvaluator.kt
│   │   ├── SystemResourceEvaluator.kt
│   │   └── UserExperienceEvaluator.kt
│   │
│   ├── error/                              # 错误处理
│   │   ├── ErrorRecoveryStrategy.kt
│   │   ├── DataConsistencyChecker.kt
│   │   └── ErrorAnalytics.kt
│   │
│   ├── observability/                      # 可观测性
│   │   ├── TelemetryCollector.kt
│   │   ├── DebugInterface.kt
│   │   ├── LogManager.kt
│   │   └── CrashReporter.kt
│   │
│   ├── compatibility/                      # 版本兼容
│   │   ├── VersionCompatibilityManager.kt
│   │   ├── BackwardCompatibilityAdapter.kt
│   │   └── DataMigrator.kt
│   │
│   └── import-export/                      # 导入导出
│       ├── DataExporter.kt
│       ├── DataImporter.kt
│       └── DataValidator.kt
│
├── core/android/                           # Android特定（新增）
│   ├── battery/                            # 电池优化
│   │   ├── BatteryOptimizationManager.kt
│   │   └── PowerSavingManager.kt
│   │
│   ├── background/                         # 后台任务
│   │   ├── BackgroundTaskScheduler.kt
│   │   ├── WorkManagerSync.kt
│   │   └── WorkManagerIndexing.kt
│   │
│   ├── lifecycle/                          # 生命周期
│   │   ├── LifecycleAwareComponent.kt
│   │   ├── DozeModeAdapter.kt
│   │   └── AppStandbyAdapter.kt
│   │
│   └── network/                            # 网络管理
│       ├── NetworkMonitor.kt
│       ├── ConnectivityCallback.kt
│       └── DataSaverManager.kt
│
├── core/llm/                               # LLM集成（新增）
│   ├── local/                              # 本地LLM
│   │   ├── LocalLlmService.kt
│   │   ├── ModelLoader.kt
│   │   └── GenerationOptions.kt
│   │
│   ├── cloud/                              // 云端LLM
│   │   ├── LlmApiClient.kt
│   │   ├── OpenAiClient.kt
│   │   ├── AnthropicClient.kt
│   │   └── GeminiClient.kt
│   │
│   └── hybrid/                             // 混合策略
│       ├── HybridLlmStrategy.kt
│       ├── LlmProviderSelector.kt
│       └── CostEstimator.kt
│
├── gradle/
│   └── libs.versions.toml                # 版本目录
│
├── build.gradle.kts                       # 根构建文件
└── settings.gradle.kts                   # 设置文件
```

### A.1 模块依赖规则（符合NowInAndroid规范）

```
app ──────────────► feature:*:impl
                    feature:*:api
                    core:*

feature:*:impl ───► feature:*:api (other features)
                    core:*

feature:*:api ────► core:model (only)

core:data ────────► core:database
                    core:network
                    core:model
                    core:datastore

core:database ────► core:model

core:network ─────► core:model

core:ui ──────────► core:model
                    core:designsystem

core:designsystem ► (no core dependencies)

core:model ───────► (no dependencies - pure Kotlin)
```

### B. 数据层实现示例（符合NowInAndroid规范）

#### B.1 Repository模式

```kotlin
// core/data/repository/MemoryRepository.kt
interface MemoryRepository {
    fun getMemoryEntries(): Flow<List<MemoryEntry>>
    fun getMemoryEntry(id: String): Flow<MemoryEntry>
    suspend fun insertMemory(entry: MemoryEntry)
    suspend fun updateMemory(entry: MemoryEntry)
    suspend fun deleteMemory(id: String)
    suspend fun searchMemory(query: String): List<MemoryEntry>
}

// core/data/repository/OfflineFirstMemoryRepository.kt
internal class OfflineFirstMemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val embeddingService: EmbeddingService,
) : MemoryRepository {

    override fun getMemoryEntries(): Flow<List<MemoryEntry>> =
        memoryDao.getMemoryEntities()
            .map { entities -> entities.map(MemoryEntity::asExternalModel) }

    override fun getMemoryEntry(id: String): Flow<MemoryEntry> =
        memoryDao.getMemoryEntity(id)
            .map(MemoryEntity::asExternalModel)

    override suspend fun insertMemory(entry: MemoryEntry) {
        // 生成嵌入向量
        val embedding = embeddingService.embed(entry.content).getOrThrow()
        memoryDao.upsertMemory(entry.toEntity().copy(embedding = embedding))
    }

    override suspend fun searchMemory(query: String): List<MemoryEntry> {
        val queryEmbedding = embeddingService.embed(query).getOrThrow()
        return memoryDao.searchSimilar(queryEmbedding)
            .map { it.asExternalModel() }
    }
}
```

#### B.2 DAO模式

```kotlin
// core/database/dao/MemoryDao.kt
@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun getMemoryEntities(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :memoryId")
    fun getMemoryEntity(memoryId: String): Flow<MemoryEntity>

    @Upsert
    suspend fun upsertMemory(entity: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :memoryId")
    suspend fun deleteMemory(memoryId: String)

    @Query(
        """
        SELECT *, vec_distance(embedding, :queryEmbedding) AS distance
        FROM memories
        WHERE embedding IS NOT NULL
        ORDER BY distance
        LIMIT :limit
        """
    )
    @MapInfo(columnName = "distance")
    fun searchSimilar(
        queryEmbedding: FloatArray,
        limit: Int = 10
    ): List<MemoryEntity>
}
```

#### B.3 ViewModel模式

```kotlin
// feature/chat/impl/ChatViewModel.kt
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val taskOrchestrator: TaskOrchestrator,
    private val memoryContextManager: MemoryContextManager,
) : ViewModel() {

    val uiState: StateFlow<ChatUiState> = combine(
        sessionRepository.getActiveSessionMessages(),
        sessionRepository.getSessionStatus()
    ) { messages, status ->
        ChatUiState.Success(
            messages = messages,
            isProcessing = status.isProcessing,
            canEvolve = status.canEvolve
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatUiState.Loading,
        )

    fun onSendMessage(content: String) {
        viewModelScope.launch {
            sessionRepository.addMessage(
                ConversationMessage.User(content)
            )
        }
    }

    fun onTriggerEvolution() {
        viewModelScope.launch {
            // 触发进化分析
            val evolutionResult = taskOrchestrator.executeTask(
                AgentTask(
                    taskId = UUID.randomUUID().toString(),
                    type = TaskType.ANALYSIS,
                    description = "分析对话并生成进化建议",
                    input = TaskInput.Text("触发进化分析")
                )
            )
            // 处理进化结果...
        }
    }
}

// feature/chat/impl/ChatUiState.kt
sealed interface ChatUiState {
    data object Loading : ChatUiState
    data class Success(
        val messages: List<ConversationMessage>,
        val isProcessing: Boolean = false,
        val canEvolve: Boolean = false
    ) : ChatUiState
    data class Error(val message: String) : ChatUiState
}
```

#### B.4 Screen模式

```kotlin
// feature/chat/impl/ChatScreen.kt
@Composable
internal fun ChatRoute(
    onNavigateToSettings: () -> Unit,
    onNavigateToTasks: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ChatScreen(
        uiState = uiState,
        onSendMessage = viewModel::onSendMessage,
        onTriggerEvolution = viewModel::onTriggerEvolution,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToTasks = onNavigateToTasks,
    )
}

@Composable
internal fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onTriggerEvolution: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTasks: () -> Unit,
) {
    when (uiState) {
        is ChatUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        is ChatUiState.Success -> {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部栏
                TopAppBar(
                    title = { Text("MingClaw") },
                    actions = {
                        IconButton(onClick = onNavigateToTasks) {
                            Icon(Icons.Default.TaskAlt, "任务")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, "设置")
                        }
                    }
                )

                // 消息列表
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    reverseLayout = true
                ) {
                    items(uiState.messages) { message ->
                        MessageBubble(message = message)
                    }
                }

                // 输入区域
                MessageInput(
                    onSend = onSendMessage,
                    isEnabled = !uiState.isProcessing
                )

                // 进化按钮
                if (uiState.canEvolve) {
                    EvolutionButton(
                        onClick = onTriggerEvolution,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        is ChatUiState.Error -> {
            ErrorMessage(
                message = uiState.message,
                onRetry = { /* 重试逻辑 */ }
            )
        }
    }
}
```

### C. Gradle配置示例（符合NowInAndroid规范）

#### C.1 根设置文件

```kotlin
// settings.gradle.kts
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MingClaw"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":feature:chat:api")
include(":feature:chat:impl")
include(":feature:task-monitor:api")
include(":feature:task-monitor:impl")
include(":feature:plugin-manager:api")
include(":feature:plugin-manager:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
include(":core:model")
include(":core:data")
include(":core:database")
include(":core:network")
include(":core:datastore")
include(":core:common")
include(":core:ui")
include(":core:designsystem")
include(":core:kernel")
include(":core:evolution")
include(":core:context")
include(":core:task")
include(":core:workspace")
include(":core:security")
include(":core:testing")
include(":core:quality:performance")
include(":core:quality:evaluation")
include(":core:quality:error")
include(":core:quality:observability")
include(":core:quality:compatibility")
include(":core:quality:import-export")
include(":core:android:battery")
include(":core:android:background")
include(":core:android:lifecycle")
include(":core:android:network")
include(":core:llm:local")
include(":core:llm:cloud")
include(":core:llm:hybrid")
```

#### C.2 Feature模块配置

```kotlin
// feature/chat/impl/build.gradle.kts
plugins {
    alias(libs.plugins.mingclaw.android.feature)
}

android {
    namespace = "com.loy.mingclaw.feature.chat.impl"
}

dependencies {
    api(projects.feature.chat.api)
    implementation(projects.core.data)
    implementation(projects.core.ui)
    implementation(projects.core.designsystem)
    implementation(projects.core.kernel)
    implementation(projects.core.evolution)
}
```

### D. 实施里程碑

#### Phase 1 - 基础框架（2-3周）
- 微内核搭建
- 插件系统基础
- 工作区管理
- 基础数据层

#### Phase 2 - 记忆系统（2-3周）
- 记忆存储（Room + SQLite）
- 向量嵌入服务
- 混合搜索引擎
- 上下文窗口管理

#### Phase 3 - 进化引擎（3-4周）
- 行为进化器
- 知识进化器
- 能力进化器
- 反馈收集器

#### Phase 4 - 任务编排（2-3周）
- 任务执行器
- 工作流引擎
- 依赖管理
- 错误恢复

#### Phase 5 - 安全加固（2周）
- 权限系统
- 沙箱隔离
- 数据加密
- 审计日志

#### Phase 6 - 质量保证（2-3周）
- 错误处理与恢复
- 数据一致性检查
- 测试框架搭建
- 性能监控系统

#### Phase 7 - Android优化（2周）
- 电池优化
- 后台任务调度
- 生命周期感知
- 网络状态管理

#### Phase 8 - 性能评估（1-2周）
- 性能评估管理器
- 进化效果评估
- 任务性能分析
- 资源使用监控

#### Phase 9 - 可选功能（2-3周）
- 本地LLM集成
- 混合LLM策略
- 导入/导出功能
- 版本兼容性管理

#### Phase 10 - UI完善（2-3周）
- 主界面
- 聊天界面
- 插件管理界面
- 设置界面
- 性能监控界面

---

**总计**: 约 20-28 周（5-7 个月）

---

*设计文档版本: 2.0*
*最后更新: 2025-03-31*
*更新内容: 新增系统质量保证章节，包含性能优化、错误处理、测试策略、可观测性、Android优化、版本兼容性、导入导出、本地LLM和性能评估体系*
