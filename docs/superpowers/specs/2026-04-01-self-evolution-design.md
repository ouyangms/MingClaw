# Self-Evolution Layer (core:evolution) 设计规格

## 概述

实现 MingClaw 的自进化层 `core:evolution`，提供行为优化、知识积累、能力扩展三条进化路径。进化分析重度依赖 LLM，进化数据以 Markdown + 文件系统存储（AGENTS.md / MEMORY.md / EXPERIENCE/）。所有进化操作需用户确认后执行，支持版本回滚。

## 设计决策

- **单模块 `core:evolution`**：5 个接口紧密共享状态和数据类，不拆分为 api/impl 两个模块
- **Markdown + 文件系统**：行为规则和知识用 Markdown 存储（人类可读可编辑），体验数据用 JSON 文件存储在 EXPERIENCE/ 目录
- **重度依赖 LLM**：进化分析、知识提取、规则建议等核心逻辑通过 `@CloudLlm LlmProvider` 完成
- **MVP 裁剪 CapabilityEvolver**：只实现能力缺口检测，搜索/安装/卸载等延后
- **实现顺序**：FeedbackCollector → KnowledgeEvolver → BehaviorEvolver → EvolutionTriggerManager + EvolutionEngine → CapabilityEvolver (存根)

## 模块依赖

```
core:evolution 依赖:
  ├── core:model         # 领域类型
  ├── core:common        # @CloudLlm、@IODispatcher
  ├── core:kernel        # EventBus（发布进化事件）
  ├── core:memory        # MemoryStorage + EmbeddingService
  └── core:data          # SessionRepository、MemoryRepository
```

---

## 包结构

```
core/evolution/src/main/java/.../core/evolution/
├── model/
│   ├── EvolutionState.kt           # 进化状态机类型
│   ├── EvolutionTypes.kt           # EvolutionType, EvolutionTrigger, Proposal, Result
│   ├── FeedbackTypes.kt            # UserFeedback, ImplicitAction, FeedbackSummary
│   ├── KnowledgeTypes.kt           # KnowledgePoint, ConsolidationResult
│   ├── BehaviorTypes.kt            # AgentDecision, RuleUpdate, BehaviorAnalysis
│   └── CapabilityTypes.kt          # CapabilityGap (MVP)
├── FeedbackCollector.kt            # 公共接口
├── KnowledgeEvolver.kt             # 公共接口
├── BehaviorEvolver.kt              # 公共接口
├── CapabilityEvolver.kt            # 公共接口 (MVP 存根)
├── EvolutionTriggerManager.kt      # 公共接口
├── EvolutionEngine.kt              # 顶层编排接口
├── internal/
│   ├── FeedbackCollectorImpl.kt
│   ├── KnowledgeEvolverImpl.kt
│   ├── BehaviorEvolverImpl.kt
│   ├── CapabilityEvolverImpl.kt
│   ├── EvolutionTriggerManagerImpl.kt
│   ├── EvolutionEngineImpl.kt
│   ├── EvolutionStateMachine.kt
│   ├── EvolutionFileManager.kt
│   └── prompts/
│       ├── BehaviorAnalysisPrompt.kt
│       ├── KnowledgeExtractionPrompt.kt
│       └── CapabilityRecommendationPrompt.kt
└── di/
    └── EvolutionModule.kt
```

---

## 领域类型

### EvolutionState.kt

```kotlin
sealed class EvolutionState {
    data object Idle : EvolutionState()
    data class Analyzing(val trigger: EvolutionTrigger) : EvolutionState()
    data class AwaitingApproval(val proposals: List<EvolutionProposal>) : EvolutionState()
    data class Applying(val proposals: List<EvolutionProposal>) : EvolutionState()
    data class Completed(val results: List<EvolutionResult>) : EvolutionState()
    data class Failed(val error: String) : EvolutionState()
}
```

状态流转：`Idle → Analyzing → AwaitingApproval → Applying → Completed/Failed → Idle`

### EvolutionTypes.kt

```kotlin
enum class EvolutionType { BEHAVIOR, KNOWLEDGE, CAPABILITY }

enum class EvolutionTrigger {
    USER_FEEDBACK, TASK_FAILURE, PERFORMANCE_DEGRADATION,
    SCHEDULED, MANUAL, KNOWLEDGE_THRESHOLD, CAPABILITY_GAP
}

enum class EvolutionPriority { LOW, MEDIUM, HIGH, IMMEDIATE }

data class EvolutionContext(
    val sessionId: String,
    val feedbackScore: Float,
    val taskSuccessRate: Float,
    val memoryCount: Int,
    val lastEvolution: Instant?,
)

data class EvolutionProposal(
    val id: String,
    val type: EvolutionType,
    val description: String,
    val reason: String,
    val expectedImpact: String,
    val priority: EvolutionPriority,
    val confidence: Float,
)

data class EvolutionResult(
    val proposalId: String,
    val success: Boolean,
    val changes: List<String>,
    val error: String? = null,
)
```

### FeedbackTypes.kt

```kotlin
enum class ExplicitFeedbackType { THUMBS_UP, THUMBS_DOWN, RATING, CORRECTION, SUGGESTION }
enum class FeedbackAspect { ACCURACY, RELEVANCE, COMPLETENESS, CLARITY, TIMELINESS, TONE, OVERALL }
enum class ImplicitAction { REGENERATED, EDITED, COPIED, IGNORED, FOLLOWED_UP, ABANDONED }
enum class Trend { IMPROVING, STABLE, DECLINING }

sealed class UserFeedback {
    abstract val feedbackId: String
    abstract val timestamp: Instant
    abstract val sessionId: String

    data class Explicit(
        override val feedbackId: String,
        override val timestamp: Instant,
        override val sessionId: String,
        val type: ExplicitFeedbackType,
        val rating: Int,
        val comment: String,
        val aspect: FeedbackAspect,
    ) : UserFeedback()

    data class Implicit(
        override val feedbackId: String,
        override val timestamp: Instant,
        override val sessionId: String,
        val action: ImplicitAction,
        val confidence: Float,
    ) : UserFeedback()
}

data class FeedbackSummary(
    val periodStart: Instant,
    val periodEnd: Instant,
    val totalFeedbacks: Int,
    val explicitCount: Int,
    val implicitCount: Int,
    val averageRating: Float,
    val ratingDistribution: Map<Int, Int>,
)

data class FeedbackTrend(
    val aspect: FeedbackAspect,
    val trend: Trend,
    val changeRate: Float,
    val confidence: Float,
)
```

### KnowledgeTypes.kt

```kotlin
enum class KnowledgeType { FACT, CONCEPT, PROCEDURE, PRINCIPLE, PREFERENCE, PATTERN, EXPERIENCE }
enum class KnowledgeCategory { USER_PROFILE, TASK_PATTERN, DOMAIN_KNOWLEDGE, COMMON_SENSE, PREFERENCE, CONTEXT }
enum class Importance { CRITICAL, HIGH, MEDIUM, LOW, TRIVIAL }

data class KnowledgePoint(
    val id: String,
    val type: KnowledgeType,
    val content: String,
    val confidence: Float,
    val importance: Float,
    val categories: Set<KnowledgeCategory>,
    val tags: Set<String>,
    val extractedAt: Instant,
)

data class ConsolidationResult(
    val added: Int,
    val updated: Int,
    val merged: Int,
    val skipped: Int,
)
```

### BehaviorTypes.kt

```kotlin
enum class RuleUpdateType { ADD, MODIFY, DELETE, REORDER, MERGE }
enum class DecisionOutcome { SUCCESS, FAILURE, PARTIAL }

data class AgentDecision(
    val decisionId: String,
    val timestamp: Instant,
    val ruleApplied: String,
    val reasoning: String,
    val outcome: DecisionOutcome,
)

data class RuleUpdate(
    val id: String,
    val ruleId: String,
    val updateType: RuleUpdateType,
    val currentRule: String,
    val proposedRule: String,
    val reason: String,
    val confidence: Float,
)

data class BehaviorAnalysis(
    val decisionCount: Int,
    val successRate: Float,
    val improvementAreas: List<String>,
    val suggestedRules: List<String>,
)
```

### CapabilityTypes.kt (MVP)

```kotlin
enum class SkillLevel { NONE, BASIC, INTERMEDIATE, ADVANCED, EXPERT }
enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }

data class CapabilityGap(
    val id: String,
    val capability: String,
    val currentLevel: SkillLevel,
    val desiredLevel: SkillLevel,
    val priority: Priority,
    val detectedAt: Instant,
)
```

---

## 公共接口

### FeedbackCollector

```kotlin
interface FeedbackCollector {
    suspend fun collectExplicitFeedback(feedback: UserFeedback.Explicit): Result<Unit>
    suspend fun collectImplicitFeedback(action: ImplicitAction, sessionId: String): Result<Unit>
    suspend fun getFeedbackSummary(since: Instant): FeedbackSummary
    fun observeFeedback(): Flow<UserFeedback>
}
```

- `collectExplicitFeedback`：写入 `EXPERIENCE/feedbacks/{feedbackId}.json`
- `collectImplicitFeedback`：根据 action 推断 confidence，写入 JSON 文件
- `getFeedbackSummary`：扫描反馈文件聚合统计
- `observeFeedback`：监听新反馈的 Flow

### KnowledgeEvolver

```kotlin
interface KnowledgeEvolver {
    suspend fun extractKnowledge(sessionId: String): Result<List<KnowledgePoint>>
    suspend fun consolidateToMemory(knowledge: List<KnowledgePoint>): Result<ConsolidationResult>
    suspend fun searchMemory(query: String): Result<List<KnowledgePoint>>
}
```

- `extractKnowledge`：从 SessionRepository.getMessages() 获取对话，通过 LLM 提取知识
- `consolidateToMemory`：去重（EmbeddingService.similarity()），写入 MEMORY.md + MemoryRepository.save()
- `searchMemory`：委托 MemoryRepository.vectorSearch()

### BehaviorEvolver

```kotlin
interface BehaviorEvolver {
    suspend fun recordDecision(decision: AgentDecision): Result<Unit>
    suspend fun analyzePatterns(): Result<BehaviorAnalysis>
    suspend fun suggestRuleUpdates(): Result<List<RuleUpdate>>
    suspend fun applyRuleUpdates(updates: List<RuleUpdate>): Result<Unit>
    suspend fun rollbackToVersion(version: String): Result<Unit>
}
```

- `recordDecision`：写入 `EXPERIENCE/decisions/{decisionId}.json`
- `analyzePatterns`：LLM 分析决策历史（BehaviorAnalysisPrompt）
- `suggestRuleUpdates`：LLM 基于分析 + 当前 AGENTS.md 生成规则建议
- `applyRuleUpdates`：备份 AGENTS.md → 修改内容
- `rollbackToVersion`：从 `.evolution/rollback/` 恢复

### CapabilityEvolver (MVP 存根)

```kotlin
interface CapabilityEvolver {
    suspend fun identifyCapabilityGaps(): Result<List<CapabilityGap>>
}
```

MVP 只做 gap 检测：分析失败任务推断缺失能力。

### EvolutionTriggerManager

```kotlin
interface EvolutionTriggerManager {
    suspend fun shouldTrigger(trigger: EvolutionTrigger, context: EvolutionContext): Boolean
    suspend fun performAnalysis(): Result<List<EvolutionProposal>>
}
```

- `shouldTrigger`：规则判断（连续负面反馈 → 行为进化，知识阈值 → 知识进化等）
- `performAnalysis`：调用三个 Evolver 分析方法，汇总为 EvolutionProposal 列表

### EvolutionEngine (顶层编排)

```kotlin
interface EvolutionEngine {
    fun observeState(): Flow<EvolutionState>
    suspend fun triggerEvolution(trigger: EvolutionTrigger, context: EvolutionContext): Result<List<EvolutionResult>>
    suspend fun approveAndApply(proposals: List<EvolutionProposal>): Result<List<EvolutionResult>>
    suspend fun rejectProposals()
}
```

唯一暴露给上层的入口。组合 StateMachine + TriggerManager + 三个 Evolver，通过 EventBus 发布进化事件。

---

## 内部组件

### EvolutionStateMachine

```kotlin
internal class EvolutionStateMachine {
    private val state = MutableStateFlow<EvolutionState>(EvolutionState.Idle)
    fun currentState(): EvolutionState = state.value
    fun observeState(): Flow<EvolutionState> = state
    fun transitionTo(newState: EvolutionState): Result<EvolutionState>
}
```

合法转换矩阵：
- `Idle` → `Analyzing`
- `Analyzing` → `AwaitingApproval` | `Failed`
- `AwaitingApproval` → `Applying` | `Idle`（拒绝）
- `Applying` → `Completed` | `Failed`
- `Completed` | `Failed` → `Idle` | `Analyzing`

### EvolutionFileManager

```kotlin
internal class EvolutionFileManager {
    suspend fun readAgentRules(): String
    suspend fun writeAgentRules(content: String)
    suspend fun readKnowledgeMemory(): String
    suspend fun writeKnowledgeMemory(content: String)
    suspend fun writeDecision(decision: AgentDecision)
    suspend fun writeFeedback(feedback: UserFeedback)
    suspend fun readDecisions(since: Instant): List<AgentDecision>
    suspend fun readFeedbacks(since: Instant): List<UserFeedback>
    suspend fun backupCurrent(version: String)
    suspend fun restoreVersion(version: String)
}
```

存储路径（基于 WorkspaceManager 工作区路径）：
```
{workspace}/
  AGENTS.md
  MEMORY.md
  EXPERIENCE/
    decisions/{id}.json
    feedbacks/{id}.json
  .evolution/
    state.json
    rollback/
      v{timestamp}/
        AGENTS.md
        MEMORY.md
```

### LLM 提示词模板

三个 prompt 以 Kotlin 常量对象组织：

- `BehaviorAnalysisPrompt.build(decisionHistory, currentRules)` → system + user 消息对
- `KnowledgeExtractionPrompt.build(conversationContent)` → system + user 消息对
- `CapabilityRecommendationPrompt.build(failedTasks, currentCapabilities)` → system + user 消息对

### EvolutionEngineImpl 编排流程

```
triggerEvolution(trigger, context)
  ├─ stateMachine.transitionTo(Analyzing)
  ├─ triggerManager.shouldTrigger() → false? → Idle, return
  ├─ triggerManager.performAnalysis() → proposals
  ├─ stateMachine.transitionTo(AwaitingApproval)
  └─ return proposals

approveAndApply(proposals)
  ├─ stateMachine.transitionTo(Applying)
  ├─ for each proposal:
  │   ├─ BEHAVIOR → behaviorEvolver.suggestRuleUpdates() → applyRuleUpdates()
  │   ├─ KNOWLEDGE → knowledgeEvolver.extractKnowledge() → consolidateToMemory()
  │   └─ CAPABILITY → capabilityEvolver.identifyCapabilityGaps()
  ├─ eventBus.publish(EvolutionEvent.Completed)
  ├─ stateMachine.transitionTo(Completed)
  └─ return results
```

---

## DI 绑定

```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class EvolutionModule {
    @Binds @Singleton abstract fun bindFeedbackCollector(impl: FeedbackCollectorImpl): FeedbackCollector
    @Binds @Singleton abstract fun bindKnowledgeEvolver(impl: KnowledgeEvolverImpl): KnowledgeEvolver
    @Binds @Singleton abstract fun bindBehaviorEvolver(impl: BehaviorEvolverImpl): BehaviorEvolver
    @Binds @Singleton abstract fun bindCapabilityEvolver(impl: CapabilityEvolverImpl): CapabilityEvolver
    @Binds @Singleton abstract fun bindEvolutionTriggerManager(impl: EvolutionTriggerManagerImpl): EvolutionTriggerManager
    @Binds @Singleton abstract fun bindEvolutionEngine(impl: EvolutionEngineImpl): EvolutionEngine
}
```

---

## 测试策略

| 测试文件 | 覆盖内容 |
|----------|----------|
| EvolutionStateMachineTest | 合法/非法状态转换 |
| FeedbackCollectorImplTest | 显式/隐式反馈持久化 + 统计聚合 |
| KnowledgeEvolverImplTest | 知识提取（mock LLM）、去重、MEMORY.md 写入 |
| BehaviorEvolverImplTest | 决策记录、LLM 分析、规则更新 + 回滚 |
| CapabilityEvolverImplTest | gap 检测（mock LLM） |
| EvolutionEngineImplTest | 完整编排流程（触发→分析→审批→应用） |
| EvolutionFileManagerTest | 文件读写、备份/恢复 |

---

## 实现阶段

| 阶段 | 模块 | 核心交付 |
|------|------|----------|
| **E1** | model/ + EvolutionFileManager + FeedbackCollector | 反馈收集与持久化 |
| **E2** | KnowledgeEvolver | 对话→知识提取→MEMORY.md |
| **E3** | BehaviorEvolver | 决策记录→模式分析→AGENTS.md |
| **E4** | EvolutionTriggerManager + EvolutionStateMachine + EvolutionEngine | 完整进化编排流程 |
| **E5** | CapabilityEvolver (MVP 存根) | 仅 gap 检测 |

---

## MVP 裁剪项（延后实现）

### 领域类型延后

| 类型 | 裁剪内容 | 建议阶段 |
|------|----------|----------|
| KnowledgePoint | KnowledgeRelation、KnowledgeSource、validUntil | E2 增强 |
| FeedbackCollector | FeedbackTrend、EvolutionReport | E4 增强 |
| BehaviorEvolver | PatternInsight、ImprovementArea 结构化 | E3 增强 |
| CapabilityEvolver | SkillMetadata、SkillInstance、PerformanceReport、SkillRecommendation | 独立阶段 |

### 接口延后

| 接口 | 裁剪方法 | 原因 |
|------|----------|------|
| FeedbackCollector | analyzeFeedbackTrends()、generateEvolutionReport() | 需要历史数据积累 |
| KnowledgeEvolver | updateKnowledgeRelations()、cleanupStaleKnowledge() | 知识图谱延后 |
| CapabilityEvolver | searchSkills()、installSkill()、uninstallSkill()、evaluateSkillPerformance()、updateSkill()、getSkillRecommendations() | 技能市场不存在 |

### 基础设施延后

| 项目 | 说明 |
|------|------|
| CapabilityRecommendationPrompt | 等 CapabilityEvolver 完整实现 |
| 技能签名验证 | 等技能安装流程设计 |
| 进化定时调度 (SCHEDULED 触发器) | 需要 WorkManager，等 Application 层 |
| 进化事件 → DynamicPromptBuilder | 需要 Context 层集成，等 UI 层 |

---

## 与现有模块集成点

| 集成方 | 方式 | 说明 |
|--------|------|------|
| core:data SessionRepository | FeedbackCollector 读取对话历史 | 知识提取用 getMessages() |
| core:data MemoryRepository | KnowledgeEvolver 读写结构化记忆 | save() / vectorSearch() |
| core:memory EmbeddingService | KnowledgeEvolver 去重比较 | similarity() |
| core:common @CloudLlm | 三个 Evolver 的 LLM 调用 | 分析、提取、建议 |
| core:kernel EventBus | EvolutionEngine 发布事件 | EvolutionEvent.Triggered/Completed/Failed |
| core:workspace WorkspaceManager | EvolutionFileManager 获取工作区路径 | AGENTS.md 等文件位置 |
