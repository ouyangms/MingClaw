# MingClaw 自我进化机制设计

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [进化机制概述](#进化机制概述)
2. [进化循环流程](#进化循环流程)
3. [行为进化器](#行为进化器)
4. [知识进化器](#知识进化器)
5. [能力进化器](#能力进化器)
6. [反馈收集器](#反馈收集器)
7. [进化触发机制](#进化触发机制)
8. [进化数据存储](#进化数据存储)

---

## 进化机制概述

### 设计目标

MingClaw 的自我进化机制实现系统在三个维度的持续改进：

| 维度 | 目标 | 存储位置 | 更新频率 |
|------|------|----------|----------|
| **行为进化** | 优化决策规则和行为模式 | AGENTS.md | 按需 |
| **知识进化** | 积累和提炼知识 | MEMORY.md | 每次对话 |
| **能力进化** | 扩展技能和工具 | SKILLS/ | 按需 |

### 核心原则

1. **用户控制**: 所有进化都需要用户确认
2. **渐进式**: 小步快跑，避免剧烈变化
3. **可回滚**: 保留历史版本，支持回退
4. **可解释**: 每次进化都有明确的理由和影响说明
5. **安全优先**: 不会降低系统性能或安全性

### 进化机制架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Evolution Engine                              │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                      Evolution Orchestrator                        │  │
│  │  - 协调三种进化路径                                                │  │
│  │  - 管理进化依赖                                                    │  │
│  │  - 验证进化结果                                                    │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌───────────────┬───────────────┬───────────────┬───────────────────┐  │
│  │               │               │               │                   │  │
│  ▼               ▼               ▼               ▼                   │  │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────────────┐    │  │
│  │ Behavior  │ │ Knowledge │ │Capability │ │  Feedback         │    │  │
│  │ Evolver   │ │ Evolver   │ │ Evolver   │ │  Collector        │    │  │
│  └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └─────────┬─────────┘    │  │
│        │             │             │                 │               │  │
│        ▼             ▼             ▼                 ▼               │  │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌─────────────────────┐  │
│  │ AGENTS.md │ │ MEMORY.md │ │ SKILLS/   │ │ EXPERIENCE/         │  │
│  └───────────┘ └───────────┘ └───────────┘ └─────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 进化循环流程

### 完整进化流程图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              进化循环                                    │
└─────────────────────────────────────────────────────────────────────────┘
        │
        ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│   用户任务     │───▶│  Agent执行     │───▶│   结果监控     │
└───────────────┘    └───────────────┘    └───────────────┘
        ▲                                            │
        │                                            ▼
        │                                  ┌───────────────┐
        │                                  │  反馈收集      │
        │                                  └───────┬───────┘
        │                                          │
        │                                          ▼
        │                                  ┌───────────────┐
        │                                  │  进化分析      │
        │                                  └───────┬───────┘
        │                                          │
        │                                          ▼
        │                          ┌───────────────────────────────┐
        │                          │      三种进化路径              │
        │                          ├───────────────────────────────┤
        │                          │ 1. 行为进化 → AGENTS.md       │
        │                          │ 2. 知识进化 → MEMORY.md       │
        │                          │ 3. 能力进化 → SKILLS/         │
        │                          └───────────┬───────────────────┘
        │                                      │
        │                                      ▼
        │                          ┌───────────────────────┐
        │                          │   用户确认            │
        │                          └───────────┬───────────┘
        │                                      │
        │                         ┌────────────┴────────────┐
        │                         ▼                         ▼
        │                  ┌──────────┐              ┌──────────┐
        │                  │  应用更新  │              │  拒绝更新  │
        │                  └─────┬────┘              └─────┬────┘
        │                        │                         │
        └────────────────────────┘                         │
                                                              │
                                                              ▼
                                                    ┌──────────┐
                                                    │ 记录反馈  │
                                                    └──────────┘
```

### 进化状态机

```kotlin
/**
 * 进化状态
 */
sealed class EvolutionState {
    data object Idle : EvolutionState()
    data class Analyzing(val trigger: EvolutionTrigger) : EvolutionState()
    data class AwaitingApproval(val proposals: List<EvolutionProposal>) : EvolutionState()
    data class Applying(val proposals: List<EvolutionProposal>) : EvolutionState()
    data class Completed(val results: List<EvolutionResult>) : EvolutionState()
    data class Failed(val error: String) : EvolutionState()
}

/**
 * 进化状态机
 */
class EvolutionStateMachine {
    private val currentState = MutableStateFlow<EvolutionState>(EvolutionState.Idle)

    fun transitionTo(newState: EvolutionState): Result<EvolutionState> {
        return when (val current = currentState.value) {
            is EvolutionState.Idle -> {
                when (newState) {
                    is EvolutionState.Analyzing -> Result.success(updateState(newState))
                    else -> Result.failure(IllegalStateTransition(current, newState))
                }
            }
            is EvolutionState.Analyzing -> {
                when (newState) {
                    is EvolutionState.AwaitingApproval,
                    is EvolutionState.Failed -> Result.success(updateState(newState))
                    else -> Result.failure(IllegalStateTransition(current, newState))
                }
            }
            is EvolutionState.AwaitingApproval -> {
                when (newState) {
                    is EvolutionState.Applying,
                    is EvolutionState.Idle -> Result.success(updateState(newState))
                    else -> Result.failure(IllegalStateTransition(current, newState))
                }
            }
            is EvolutionState.Applying -> {
                when (newState) {
                    is EvolutionState.Completed,
                    is EvolutionState.Failed -> Result.success(updateState(newState))
                    else -> Result.failure(IllegalStateTransition(current, newState))
                }
            }
            is EvolutionState.Completed,
            is EvolutionState.Failed -> {
                when (newState) {
                    is EvolutionState.Idle,
                    is EvolutionState.Analyzing -> Result.success(updateState(newState))
                    else -> Result.failure(IllegalStateTransition(current, newState))
                }
            }
        }
    }

    private fun updateState(newState: EvolutionState): EvolutionState {
        currentState.value = newState
        return newState
    }
}
```

---

## 行为进化器

### 职责

分析用户反馈和任务执行结果，优化行为规则和决策模式。

### 接口定义

```kotlin
/**
 * 行为进化器接口
 */
interface BehaviorEvolver {
    /**
     * 记录决策和结果
     */
    suspend fun recordDecision(decision: AgentDecision, outcome: Outcome)

    /**
     * 处理用户反馈
     */
    suspend fun processFeedback(feedback: UserFeedback)

    /**
     * 分析行为模式
     */
    suspend fun analyzeBehaviorPatterns(): BehaviorAnalysis

    /**
     * 生成规则更新建议
     */
    suspend fun suggestRuleUpdates(): List<RuleUpdate>

    /**
     * 应用规则更新
     */
    suspend fun applyRuleUpdates(updates: List<RuleUpdate>): BehaviorEvolutionResult

    /**
     * 回滚到之前的版本
     */
    suspend fun rollbackToVersion(version: String): Result<Unit>
}

/**
 * 代理决策
 */
data class AgentDecision(
    val decisionId: String,
    val timestamp: Instant,
    val task: AgentTask,
    val ruleApplied: String,
    val reasoning: String,
    val alternativeRules: List<String> = emptyList()
)

/**
 * 决策结果
 */
sealed class Outcome {
    data class Success(
        val metrics: SuccessMetrics
    ) : Outcome()

    data class Failure(
        val error: String,
        val errorType: ErrorType
    ) : Outcome()

    data class PartialSuccess(
        val successfulParts: List<String>,
        val failedParts: List<String>
    ) : Outcome()
}

/**
 * 成功指标
 */
data class SuccessMetrics(
    val completionTime: Duration,
    val userSatisfaction: Float?,        // 0-1
    val resourceUsage: ResourceUsage,
    val qualityScore: Float              // 0-1
)

/**
 * 错误类型
 */
enum class ErrorType {
    VALIDATION_ERROR,
    EXECUTION_ERROR,
    TIMEOUT,
    RESOURCE_EXHAUSTED,
    PERMISSION_DENIED,
    UNKNOWN
}

/**
 * 用户反馈
 */
sealed class UserFeedback {
    abstract val feedbackId: String
    abstract val timestamp: Instant
    abstract val sessionId: String

    /**
     * 显式反馈 - 用户主动提供
     */
    data class Explicit(
        override val feedbackId: String,
        override val timestamp: Instant,
        override val sessionId: String,
        val type: ExplicitFeedbackType,
        val rating: Int,                    // 1-5星
        val comment: String,
        val aspect: FeedbackAspect
    ) : UserFeedback()

    /**
     * 隐式反馈 - 从行为推断
     */
    data class Implicit(
        override val feedbackId: String,
        override val timestamp: Instant,
        override val sessionId: String,
        val action: ImplicitAction,
        val confidence: Float               // 0-1
    ) : UserFeedback()
}

/**
 * 显式反馈类型
 */
enum class ExplicitFeedbackType {
    THUMBS_UP,           // 赞
    THUMBS_DOWN,         // 踩
    RATING,              // 评分
    CORRECTION,          // 纠正
    SUGGESTION           // 建议
}

/**
 * 反馈方面
 */
enum class FeedbackAspect {
    ACCURACY,            // 准确性
    RELEVANCE,           // 相关性
    COMPLETENESS,        // 完整性
    CLARITY,             // 清晰度
    TIMELINESS,          // 及时性
    TONE,                // 语气
    OVERALL              // 整体
}

/**
 * 隐式动作
 */
enum class ImplicitAction {
    REGENERATED,         // 重新生成
    EDITED,              // 编辑了结果
    COPIED,              // 复制了结果
    IGNORED,             // 忽略了结果
    FOLLOWED_UP,         // 继续追问
    ABANDONED            // 放弃
}

/**
 * 行为分析结果
 */
data class BehaviorAnalysis(
    val timestamp: Instant,
    val decisionCount: Int,
    val successRate: Float,
    val averageCompletionTime: Duration,
    val patternInsights: List<PatternInsight>,
    val improvementAreas: List<ImprovementArea>
)

/**
 * 模式洞察
 */
data class PatternInsight(
    val pattern: String,
    val frequency: Int,
    val successRate: Float,
    val description: String
)

/**
 * 改进领域
 */
data class ImprovementArea(
    val area: String,
    val currentPerformance: Float,
    val targetPerformance: Float,
    val suggestedActions: List<String>
)

/**
 * 规则更新
 */
data class RuleUpdate(
    val updateId: String,
    val ruleId: String,
    val updateType: RuleUpdateType,
    val currentRule: String,
    val proposedRule: String,
    val reason: String,
    val expectedImpact: String,
    val confidence: Float               // 0-1
)

/**
 * 规则更新类型
 */
enum class RuleUpdateType {
    ADD,                 // 添加新规则
    MODIFY,              // 修改现有规则
    DELETE,              // 删除规则
    REORDER,             // 调整优先级
    MERGE                // 合并规则
}

/**
 * 行为进化结果
 */
data class BehaviorEvolutionResult(
    val timestamp: Instant,
    val appliedUpdates: Int,
    val skippedUpdates: Int,
    val failedUpdates: Int,
    val newVersion: String,
    val summary: String
)
```

### 实现示例

```kotlin
/**
 * 行为进化器实现
 */
internal class BehaviorEvolverImpl @Inject constructor(
    private val workspaceManager: WorkspaceManager,
    private val feedbackAnalyzer: FeedbackAnalyzer,
    private val patternExtractor: PatternExtractor,
    private val ruleGenerator: RuleGenerator
) : BehaviorEvolver {

    private val decisionHistory = mutableListOf<AgentDecision>()
    private val outcomeHistory = mutableListOf<Outcome>()

    override suspend fun recordDecision(decision: AgentDecision, outcome: Outcome) {
        decisionHistory.add(decision)
        outcomeHistory.add(outcome)

        // 持久化到经验数据
        workspaceManager.writeWorkspaceFile(
            WorkspaceFileName.EXPERIENCE,
            formatDecisionRecord(decision, outcome)
        )
    }

    override suspend fun processFeedback(feedback: UserFeedback) {
        when (feedback) {
            is UserFeedback.Explicit -> {
                // 显式反馈需要深入分析
                val analysis = feedbackAnalyzer.analyzeExplicitFeedback(feedback)

                // 如果是负面反馈，触发规则审查
                if (feedback.rating <= 2) {
                    triggerRuleReview(feedback)
                }
            }
            is UserFeedback.Implicit -> {
                // 隐式反馈积累到一定阈值后分析
                if (shouldAnalyzeImplicitFeedback()) {
                    analyzeImplicitFeedbackPatterns()
                }
            }
        }
    }

    override suspend fun analyzeBehaviorPatterns(): BehaviorAnalysis {
        // 1. 提取成功/失败模式
        val successPatterns = patternExtractor.extractSuccessPatterns(
            decisionHistory.zip(outcomeHistory)
        )

        // 2. 计算整体指标
        val successRate = outcomeHistory.count { it is Outcome.Success }
            .toFloat() / outcomeHistory.size

        val avgCompletionTime = outcomeHistory
            .filterIsInstance<Outcome.Success>()
            .map { it.metrics.completionTime }
            .average()
            .let { if (it.isNaN()) Duration.ZERO else it.toLong().milliseconds }

        // 3. 识别改进领域
        val improvementAreas = identifyImprovementAreas(successPatterns)

        return BehaviorAnalysis(
            timestamp = Instant.now(),
            decisionCount = decisionHistory.size,
            successRate = successRate,
            averageCompletionTime = avgCompletionTime,
            patternInsights = successPatterns,
            improvementAreas = improvementAreas
        )
    }

    override suspend fun suggestRuleUpdates(): List<RuleUpdate> {
        val analysis = analyzeBehaviorPatterns()

        return buildList {
            // 基于模式洞察生成规则更新
            for (insight in analysis.patternInsights) {
                if (insight.successRate < 0.7f) {
                    val update = ruleGenerator.generateUpdateForPattern(insight)
                    add(update)
                }
            }

            // 基于改进领域生成规则更新
            for (area in analysis.improvementAreas) {
                if (area.currentPerformance < area.targetPerformance * 0.8f) {
                    val updates = ruleGenerator.generateUpdatesForArea(area)
                    addAll(updates)
                }
            }
        }
    }

    override suspend fun applyRuleUpdates(updates: List<RuleUpdate>): BehaviorEvolutionResult {
        // 1. 备份当前规则
        val backup = backupCurrentRules()

        // 2. 读取 AGENTS.md
        val currentContent = workspaceManager.readWorkspaceFile(
            WorkspaceFileName.AGENTS
        ).getOrDefault("")

        // 3. 应用更新
        var newContent = currentContent
        val appliedUpdates = mutableListOf<RuleUpdate>()
        val skippedUpdates = mutableListOf<RuleUpdate>()
        val failedUpdates = mutableListOf<RuleUpdate>()

        for (update in updates) {
            try {
                newContent = applySingleUpdate(newContent, update)
                appliedUpdates.add(update)
            } catch (e: ConflictException) {
                skippedUpdates.add(update)
            } catch (e: Exception) {
                failedUpdates.add(update)
            }
        }

        // 4. 写入新内容
        val newVersion = generateNewVersion()
        val updatedContent = addVersionHeader(newContent, newVersion)

        workspaceManager.writeWorkspaceFile(
            WorkspaceFileName.AGENTS,
            updatedContent
        )

        // 5. 保存备份
        saveBackup(backup, newVersion)

        return BehaviorEvolutionResult(
            timestamp = Instant.now(),
            appliedUpdates = appliedUpdates.size,
            skippedUpdates = skippedUpdates.size,
            failedUpdates = failedUpdates.size,
            newVersion = newVersion,
            summary = generateEvolutionSummary(appliedUpdates)
        )
    }

    private fun applySingleUpdate(content: String, update: RuleUpdate): String {
        return when (update.updateType) {
            RuleUpdateType.ADD -> addRule(content, update)
            RuleUpdateType.MODIFY -> modifyRule(content, update)
            RuleUpdateType.DELETE -> deleteRule(content, update)
            RuleUpdateType.REORDER -> reorderRule(content, update)
            RuleUpdateType.MERGE -> mergeRule(content, update)
        }
    }
}
```

---

## 知识进化器

### 职责

从对话和交互中提取有价值的知识点，整合到长期记忆中。

### 接口定义

```kotlin
/**
 * 知识进化器接口
 */
interface KnowledgeEvolver {
    /**
     * 从对话中提取知识
     */
    suspend fun extractKnowledge(conversation: Conversation): List<KnowledgePoint>

    /**
     * 评估知识重要性
     */
    suspend fun evaluateImportance(knowledge: KnowledgePoint): Importance

    /**
     * 整合到长期记忆
     */
    suspend fun consolidateToMemory(knowledge: List<KnowledgePoint>): ConsolidationResult

    /**
     * 搜索记忆
     */
    suspend fun searchMemory(query: String): List<MemoryFragment>

    /**
     * 更新知识关联
     */
    suspend fun updateKnowledgeRelations(knowledgeId: String): Result<Unit>

    /**
     * 清理过期知识
     */
    suspend fun cleanupStaleKnowledge(): CleanupResult
}

/**
 * 对话
 */
data class Conversation(
    val conversationId: String,
    val startTime: Instant,
    val endTime: Instant,
    val messages: List<ConversationMessage>,
    val metadata: ConversationMetadata
)

/**
 * 对话元数据
 */
data class ConversationMetadata(
    val sessionId: String,
    val taskType: String?,
    val userIntent: String?,
    val outcome: Outcome?,
    val userSatisfaction: Float?
)

/**
 * 知识点
 */
data class KnowledgePoint(
    val knowledgeId: String = UUID.randomUUID().toString(),
    val type: KnowledgeType,
    val content: String,
    val source: KnowledgeSource,
    val confidence: Float,              // 0-1
    val importance: Float,              // 0-1
    val categories: Set<KnowledgeCategory>,
    val tags: Set<String>,
    val relations: Set<KnowledgeRelation>,
    val extractedAt: Instant,
    val validUntil: Instant? = null
)

/**
 * 知识类型
 */
enum class KnowledgeType {
    FACT,                // 事实
    CONCEPT,             // 概念
    PROCEDURE,           // 过程
    PRINCIPLE,           // 原理
    PREFERENCE,          // 偏好
    PATTERN,             // 模式
    EXPERIENCE           // 经验
}

/**
 * 知识来源
 */
sealed class KnowledgeSource {
    data class UserInput(val messageId: String) : KnowledgeSource()
    data class AgentResponse(val messageId: String) : KnowledgeSource()
    data class UserFeedback(val feedbackId: String) : KnowledgeSource()
    data class ExternalDocument(val documentId: String) : KnowledgeSource()
}

/**
 * 知识类别
 */
enum class KnowledgeCategory {
    USER_PROFILE,        // 用户画像
    TASK_PATTERN,        // 任务模式
    DOMAIN_KNOWLEDGE,    // 领域知识
    COMMON_SENSE,        // 常识
    PREFERENCE,          // 偏好设置
    CONTEXT,             // 上下文信息
    TRIGGER_PATTERN      // 触发模式
}

/**
 * 知识关联
 */
data class KnowledgeRelation(
    val targetId: String,
    val relationType: RelationType,
    val strength: Float               // 0-1
)

/**
 * 关联类型
 */
enum class RelationType {
    IS_RELATED_TO,
    IS_CAUSE_OF,
    IS_EFFECT_OF,
    IS_SIMILAR_TO,
    IS_OPPOSITE_OF,
    IS_PART_OF,
    CONTAINS,
    PRECEDES,
    FOLLOWS
}

/**
 * 重要性等级
 */
enum class Importance {
    CRITICAL,            // 关键，必须保留
    HIGH,                // 高，尽量保留
    MEDIUM,              // 中等，定期评估
    LOW,                 // 低，可能清理
    TRIVIAL              // 微不足道，可能清理
}

/**
 * 整合结果
 */
data class ConsolidationResult(
    val added: Int,
    val updated: Int,
    val merged: Int,
    val skipped: Int,
    val errors: List<ConsolidationError>
)

/**
 * 整合错误
 */
data class ConsolidationError(
    val knowledgeId: String,
    val error: String
)

/**
 * 记忆片段
 */
data class MemoryFragment(
    val fragmentId: String,
    val content: String,
    val relevanceScore: Float,
    val source: KnowledgeSource,
    val lastAccessed: Instant
)

/**
 * 清理结果
 */
data class CleanupResult(
    val removedCount: Int,
    val archivedCount: Int,
    val keptCount: Int
)
```

### 实现示例

```kotlin
/**
 * 知识进化器实现
 */
internal class KnowledgeEvolverImpl @Inject constructor(
    private val workspaceManager: WorkspaceManager,
    private val knowledgeExtractor: KnowledgeExtractor,
    private val importanceEvaluator: ImportanceEvaluator,
    private val memoryRepository: MemoryRepository,
    private val embeddingService: EmbeddingService
) : KnowledgeEvolver {

    override suspend fun extractKnowledge(conversation: Conversation): List<KnowledgePoint> {
        val knowledgePoints = mutableListOf<KnowledgePoint>()

        // 1. 分析每条消息
        for (message in conversation.messages) {
            when (message) {
                is ConversationMessage.User -> {
                    // 从用户输入提取知识
                    val userKnowledge = knowledgeExtractor.extractFromUserMessage(message)
                    knowledgePoints.addAll(userKnowledge)
                }
                is ConversationMessage.Assistant -> {
                    // 从AI响应提取知识（主要是确认的信息）
                    val assistantKnowledge = knowledgeExtractor.extractFromAssistantMessage(
                        message,
                        conversation.metadata
                    )
                    knowledgePoints.addAll(assistantKnowledge)
                }
            }
        }

        // 2. 跨消息分析（识别对话中的模式和关系）
        val crossMessageKnowledge = knowledgeExtractor.extractCrossMessagePatterns(conversation)
        knowledgePoints.addAll(crossMessageKnowledge)

        // 3. 去重
        return deduplicateKnowledge(knowledgePoints)
    }

    override suspend fun evaluateImportance(knowledge: KnowledgePoint): Importance {
        return importanceEvaluator.evaluate(knowledge)
    }

    override suspend fun consolidateToMemory(knowledge: List<KnowledgePoint>): ConsolidationResult {
        val added = mutableListOf<KnowledgePoint>()
        val updated = mutableListOf<KnowledgePoint>()
        val merged = mutableListOf<KnowledgePoint>()
        val skipped = mutableListOf<KnowledgePoint>()
        val errors = mutableListOf<ConsolidationError>()

        for (point in knowledge) {
            try {
                // 1. 检查是否已存在相似知识
                val existing = findSimilarKnowledge(point.content)

                if (existing != null) {
                    // 合并知识
                    val mergedPoint = mergeKnowledgePoints(existing, point)
                    memoryRepository.updateMemory(mergedPoint.toMemoryEntry())
                    merged.add(mergedPoint)
                } else {
                    // 评估重要性
                    val importance = evaluateImportance(point)

                    if (importance == Importance.TRIVIAL) {
                        // 跳过不重要的知识
                        skipped.add(point)
                    } else {
                        // 添加新知识
                        memoryRepository.insertMemory(point.toMemoryEntry())
                        added.add(point)
                    }
                }
            } catch (e: Exception) {
                errors.add(ConsolidationError(point.knowledgeId, e.message ?: "Unknown error"))
            }
        }

        // 更新 MEMORY.md
        updateMemoryDocument()

        return ConsolidationResult(
            added = added.size,
            updated = updated.size,
            merged = merged.size,
            skipped = skipped.size,
            errors = errors
        )
    }

    override suspend fun searchMemory(query: String): List<MemoryFragment> {
        // 1. 生成查询向量
        val queryEmbedding = embeddingService.embed(query).getOrThrow()

        // 2. 向量搜索
        val vectorResults = memoryRepository.searchByVector(queryEmbedding)

        // 3. 关键词搜索（补充）
        val keywordResults = memoryRepository.searchByKeyword(query)

        // 4. 合并和排序
        return mergeSearchResults(vectorResults, keywordResults)
    }

    private suspend fun updateMemoryDocument() {
        // 1. 读取当前 MEMORY.md
        val currentContent = workspaceManager.readWorkspaceFile(
            WorkspaceFileName.MEMORY
        ).getOrDefault("# Memory\n\n")

        // 2. 获取最新知识
        val recentKnowledge = memoryRepository.getRecentKnowledge(
            since = Instant.now().minus(7, ChronoUnit.DAYS)
        )

        // 3. 格式化为 Markdown
        val markdownContent = formatKnowledgeAsMarkdown(recentKnowledge)

        // 4. 更新文档
        workspaceManager.writeWorkspaceFile(
            WorkspaceFileName.MEMORY,
            currentContent + "\n\n" + markdownContent
        )
    }
}
```

---

## 能力进化器

### 职责

识别能力缺口，自动搜索、安装和管理技能包。

### 接口定义

```kotlin
/**
 * 能力进化器接口
 */
interface CapabilityEvolver {
    /**
     * 识别能力缺口
     */
    suspend fun identifyCapabilityGaps(): List<CapabilityGap>

    /**
     * 搜索技能
     */
    suspend fun searchSkills(capability: String): List<SkillMetadata>

    /**
     * 安装技能
     */
    suspend fun installSkill(skill: SkillMetadata): Result<SkillInstance>

    /**
     * 卸载技能
     */
    suspend fun uninstallSkill(skillId: String): Result<Unit>

    /**
     * 评估技能性能
     */
    suspend fun evaluateSkillPerformance(skillId: String): PerformanceReport

    /**
     * 更新技能
     */
    suspend fun updateSkill(skillId: String): Result<SkillVersion>

    /**
     * 获取技能推荐
     */
    suspend fun getSkillRecommendations(): List<SkillRecommendation>
}

/**
 * 能力缺口
 */
data class CapabilityGap(
    val gapId: String,
    val capability: String,
    val currentLevel: SkillLevel,
    val desiredLevel: SkillLevel,
    val priority: Priority,
    val useCases: List<String>,
    val detectedAt: Instant
)

/**
 * 技能等级
 */
enum class SkillLevel {
    NONE,                // 无此能力
    BASIC,               // 基础
    INTERMEDIATE,        // 中级
    ADVANCED,            // 高级
    EXPERT               // 专家
}

/**
 * 技能元数据
 */
data class SkillMetadata(
    val skillId: String,
    val name: String,
    val description: String,
    val version: SkillVersion,
    val author: String,
    val capabilities: List<String>,
    val requirements: SkillRequirements,
    val ratings: SkillRatings,
    val downloadCount: Int,
    val lastUpdated: Instant,
    val source: SkillSource
)

/**
 * 技能版本
 */
@JvmInline
value class SkillVersion(val value: String) {
    fun isNewerThan(other: SkillVersion): Boolean {
        return compareVersions(this.value, other.value) > 0
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1 - p2
        }
        return 0
    }
}

/**
 * 技能要求
 */
data class SkillRequirements(
    val minAndroidVersion: Int,
    val requiredPermissions: List<String>,
    val requiredSkills: List<String>,
    val diskSpace: Long,
    val memory: Long
)

/**
 * 技能评分
 */
data class SkillRatings(
    val average: Float,
    val count: Int,
    val distribution: Map<Int, Int>   // 1-5星分布
)

/**
 * 技能来源
 */
enum class SkillSource {
    OFFICIAL_MARKETPLACE,    // 官方市场
    COMMUNITY,              // 社区贡献
    CUSTOM,                 // 自定义
    BUNDLED                 // 内置
}

/**
 * 技能实例
 */
data class SkillInstance(
    val skillId: String,
    val version: SkillVersion,
    val installedAt: Instant,
    val status: SkillStatus,
    val configuration: Map<String, Any>
)

/**
 * 技能状态
 */
enum class SkillStatus {
    INSTALLED,
    LOADED,
    ACTIVE,
    INACTIVE,
    ERROR,
    UPDATING
}

/**
 * 性能报告
 */
data class PerformanceReport(
    val skillId: String,
    val evaluationPeriod: Period,
    val usageCount: Int,
    val successRate: Float,
    val averageExecutionTime: Duration,
    val averageMemoryUsage: Long,
    val errorCount: Int,
    val userSatisfaction: Float,
    val overallScore: Float
)

/**
 * 技能推荐
 */
data class SkillRecommendation(
    val skill: SkillMetadata,
    val reason: RecommendationReason,
    val confidence: Float,
    val expectedBenefit: String,
    val installationComplexity: Complexity
)

/**
 * 推荐理由
 */
enum class RecommendationReason {
    FILLS_CAPABILITY_GAP,      // 填补能力缺口
    IMPROVES_PERFORMANCE,       // 提升性能
    ADDS_FEATURE,              // 添加功能
    REPLACES_OUTDATED,         // 替换过时技能
    COMMUNITY_FAVORITE         // 社区热门
}
```

### 实现示例

```kotlin
/**
 * 能力进化器实现
 */
internal class CapabilityEvolverImpl @Inject constructor(
    private val skillMarketplaceClient: SkillMarketplaceClient,
    private val skillManager: SkillManager,
    private val taskAnalyzer: TaskAnalyzer,
    private val kernel: MingClawKernel
) : CapabilityEvolver {

    override suspend fun identifyCapabilityGaps(): List<CapabilityGap> {
        // 1. 分析历史任务
        val taskHistory = taskAnalyzer.getRecentTaskHistory(days = 30)

        // 2. 识别失败或低效的任务
        val problematicTasks = taskHistory.filter { task ->
            task.result is TaskResult.Failure ||
            (task.result is TaskResult.Success && task.metrics.qualityScore < 0.6f)
        }

        // 3. 分析所需能力
        val requiredCapabilities = problematicTasks.mapNotNull { task ->
            taskAnalyzer.getRequiredCapabilitiesForTask(task)
        }.flatten()

        // 4. 评估当前能力水平
        val currentCapabilities = kernel.getLoadedPlugins()
            .flatMap { it.providedCapabilities }

        // 5. 识别缺口
        val gaps = requiredCapabilities.filter { capability ->
            !currentCapabilities.contains(capability)
        }.map { capability ->
            CapabilityGap(
                gapId = UUID.randomUUID().toString(),
                capability = capability,
                currentLevel = SkillLevel.NONE,
                desiredLevel = SkillLevel.INTERMEDIATE,
                priority = Priority.MEDIUM,
                useCases = getUseCasesForCapability(capability),
                detectedAt = Instant.now()
            )
        }

        return gaps
    }

    override suspend fun searchSkills(capability: String): List<SkillMetadata> {
        return skillMarketplaceClient.searchByCapability(capability)
            .sortedByDescending { it.ratings.average }
    }

    override suspend fun installSkill(skill: SkillMetadata): Result<SkillInstance> {
        // 1. 检查要求
        val requirementsCheck = checkRequirements(skill.requirements)
        if (!requirementsCheck.meetsRequirements) {
            return Result.failure(
                SkillRequirementException(
                    skill.skillId,
                    requirementsCheck.missingRequirements
                )
            )
        }

        // 2. 下载技能包
        val downloadResult = skillMarketplaceClient.downloadSkill(skill.skillId, skill.version)
        if (downloadResult.isFailure) {
            return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
        }

        val skillPackage = downloadResult.getOrThrow()

        // 3. 验证签名
        if (!verifySkillSignature(skillPackage, skill.author)) {
            return Result.failure(SecurityException("Skill signature verification failed"))
        }

        // 4. 安装技能
        val installResult = skillManager.installSkill(skillPackage)
        if (installResult.isFailure) {
            return Result.failure(installResult.exceptionOrNull() ?: Exception("Installation failed"))
        }

        // 5. 加载插件
        val loadResult = kernel.loadPlugin(skill.skillId)
        if (loadResult.isFailure) {
            // 回滚安装
            skillManager.uninstallSkill(skill.skillId)
            return Result.failure(loadResult.exceptionOrNull() ?: Exception("Plugin load failed"))
        }

        return Result.success(
            SkillInstance(
                skillId = skill.skillId,
                version = skill.version,
                installedAt = Instant.now(),
                status = SkillStatus.INSTALLED,
                configuration = emptyMap()
            )
        )
    }

    override suspend fun getSkillRecommendations(): List<SkillRecommendation> {
        val recommendations = mutableListOf<SkillRecommendation>()

        // 1. 基于能力缺口的推荐
        val gaps = identifyCapabilityGaps()
        for (gap in gaps) {
            val skills = searchSkills(gap.capability)
            val topSkill = skills.firstOrNull()

            if (topSkill != null) {
                recommendations.add(
                    SkillRecommendation(
                        skill = topSkill,
                        reason = RecommendationReason.FILLS_CAPABILITY_GAP,
                        confidence = 0.9f,
                        expectedBenefit = "填补 ${gap.capability} 能力缺口",
                        installationComplexity = Complexity.LOW
                    )
                )
            }
        }

        // 2. 社区热门技能
        val trendingSkills = skillMarketplaceClient.getTrendingSkills(limit = 5)
        for (skill in trendingSkills) {
            if (!recommendations.any { it.skill.skillId == skill.skillId }) {
                recommendations.add(
                    SkillRecommendation(
                        skill = skill,
                        reason = RecommendationReason.COMMUNITY_FAVORITE,
                        confidence = 0.7f,
                        expectedBenefit = "社区热门 (${skill.downloadCount} 次下载)",
                        installationComplexity = Complexity.MEDIUM
                    )
                )
            }
        }

        return recommendations.sortedByDescending { it.confidence }
    }
}
```

---

## 反馈收集器

### 职责

收集显式和隐式用户反馈，为进化提供数据支持。

### 接口定义

```kotlin
/**
 * 反馈收集器接口
 */
interface FeedbackCollector {
    /**
     * 收集显式反馈
     */
    suspend fun collectExplicitFeedback(feedback: UserFeedback.Explicit): Result<Unit>

    /**
     * 收集隐式反馈
     */
    suspend fun collectImplicitFeedback(action: UserAction): Result<UserFeedback.Implicit>

    /**
     * 获取反馈摘要
     */
    suspend fun getFeedbackSummary(period: Period): FeedbackSummary

    /**
     * 生成进化报告
     */
    suspend fun generateEvolutionReport(): EvolutionReport

    /**
     * 分析反馈趋势
     */
    suspend fun analyzeFeedbackTrends(): List<FeedbackTrend>
}

/**
 * 用户动作
 */
sealed class UserAction {
    data class Regenerate(
        val originalResponseId: String,
        val timestamp: Instant
    ) : UserAction()

    data class Edit(
        val originalResponseId: String,
        val editedContent: String,
        val timestamp: Instant
    ) : UserAction()

    data class Copy(
        val content: String,
        val timestamp: Instant
    ) : UserAction()

    data class FollowUp(
        val originalResponseId: String,
        val followUpQuestion: String,
        val timestamp: Instant
    ) : UserAction()

    data class Abandon(
        val sessionId: String,
        val lastMessageId: String,
        val timestamp: Instant
    ) : UserAction()
}

/**
 * 反馈摘要
 */
data class FeedbackSummary(
    val period: Period,
    val totalFeedbacks: Int,
    val explicitCount: Int,
    val implicitCount: Int,
    val averageRating: Float,
    val ratingDistribution: Map<Int, Int>,
    val commonIssues: List<CommonIssue>,
    val positiveHighlights: List<String>
)

/**
 * 常见问题
 */
data class CommonIssue(
    val issue: String,
    val frequency: Int,
    val percentage: Float,
    val examples: List<String>
)

/**
 * 进化报告
 */
data class EvolutionReport(
    val reportId: String,
    val generatedAt: Instant,
    val period: Period,
    val behaviorEvolution: BehaviorEvolutionSection,
    val knowledgeEvolution: KnowledgeEvolutionSection,
    val capabilityEvolution: CapabilityEvolutionSection,
    val recommendations: List<EvolutionRecommendation>
)

/**
 * 行为进化部分
 */
data class BehaviorEvolutionSection(
    val ruleUpdates: Int,
    val performanceChange: Percentage,
    val topImprovements: List<String>
)

/**
 * 知识进化部分
 */
data class KnowledgeEvolutionSection(
    val knowledgePointsAdded: Int,
    val categories: Map<KnowledgeCategory, Int>,
    val retentionRate: Float
)

/**
 * 能力进化部分
 */
data class CapabilityEvolutionSection(
    val skillsInstalled: Int,
    val skillsRemoved: Int,
    val newCapabilities: List<String>
)

/**
 * 进化建议
 */
data class EvolutionRecommendation(
    val type: EvolutionType,
    val priority: Priority,
    val description: String,
    val expectedImpact: String,
    val estimatedEffort: Complexity
)

/**
 * 进化类型
 */
enum class EvolutionType {
    BEHAVIOR_CHANGE,
    KNOWLEDGE_CONSOLIDATION,
    CAPABILITY_ACQUISITION,
    CONFIGURATION_UPDATE
}

/**
 * 反馈趋势
 */
data class FeedbackTrend(
    val aspect: FeedbackAspect,
    val trend: Trend,
    val changeRate: Percentage,
    val confidence: Float
)
```

---

## 进化触发机制

### 触发条件

```kotlin
/**
 * 进化触发条件
 */
enum class EvolutionTrigger {
    USER_FEEDBACK,              // 用户显式反馈触发
    TASK_FAILURE,               // 任务连续失败
    PERFORMANCE_DEGRADATION,    // 性能下降
    SCHEDULED,                  // 定期检查
    MANUAL,                     // 手动触发
    KNOWGE_THRESHOLD,           // 知识积累达到阈值
    CAPABILITY_GAP              // 发现能力缺口
}

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
    val timeSinceLastEvolution: Duration,
    val knowledgeCount: Int,
    val activeSkills: Int
)

/**
 * 进化分析
 */
data class EvolutionAnalysis(
    val behaviorRecommendations: List<BehaviorRecommendation>,
    val knowledgeRecommendations: List<KnowledgeRecommendation>,
    val capabilityRecommendations: List<CapabilityRecommendation>,
    val estimatedImpact: ImpactLevel,
    val estimatedTime: Duration
)

/**
 * 行为建议
 */
data class BehaviorRecommendation(
    val ruleUpdate: RuleUpdate,
    val reason: String,
    val priority: Priority
)

/**
 * 知识建议
 */
data class KnowledgeRecommendation(
    val knowledgePoint: KnowledgePoint,
    val action: KnowledgeAction,
    val reason: String
)

/**
 * 知识操作
 */
enum class KnowledgeAction {
    ADD,
    UPDATE,
    MERGE,
    ARCHIVE,
    DELETE
}

/**
 * 能力建议
 */
data class CapabilityRecommendation(
    val skill: SkillMetadata,
    val action: CapabilityAction,
    val reason: String
)

/**
 * 能力操作
 */
enum class CapabilityAction {
    INSTALL,
    UPDATE,
    REMOVE,
    CONFIGURE
}
```

---

## 进化数据存储

### 存储结构

```
~/.mingclaw/workspace/
├── AGENTS.md                   # 行为规则（可读性高）
├── MEMORY.md                   # 长期记忆（可读性高）
├── SKILLS/                     # 技能目录
│   ├── installed/              # 已安装技能
│   │   ├── skill-name/
│   │   │   ├── skill.json      # 技能元数据
│   │   │   ├── manifest.yml    # 技能清单
│   │   │   └── content/        # 技能内容
│   └── cache/                  # 技能缓存
├── EXPERIENCE/                 # 经验数据
│   ├── decisions/              # 决策历史
│   ├── outcomes/               # 结果记录
│   └── feedback/               # 反馈数据
└── .state/                     # 状态文件
    ├── evolution.json          # 进化状态
    ├── version.json            # 当前版本
    └── rollback/               # 回滚备份
        ├── v1.0/
        ├── v1.1/
        └── ...
```

### AGENTS.md 格式

```markdown
# Agent Behavior Rules

**Version**: 1.2.0
**Last Updated**: 2025-03-31
**Previous Version**: 1.1.0

## Core Principles

1. Always prioritize user intent
2. Be concise and accurate
3. Ask for clarification when uncertain

## Communication Rules

### Response Style
- Use clear, simple language
- Avoid jargon unless necessary
- Provide examples when helpful

### Information Handling
- Verify facts before stating
- Acknowledge uncertainty
- Cite sources when available

## Decision Rules

### Task Prioritization
1. Safety > Efficiency > Convenience
2. User-specified > Default ordering
3. Recent > Old tasks

### Tool Selection
- Use the most specific tool available
- Prefer faster tools for simple tasks
- Consider tool reliability

## Version History

### v1.2.0 (2025-03-31)
- Added: Verification rule for factual statements
- Modified: Response style to be more concise
- Reason: User feedback indicated responses were too verbose

### v1.1.0 (2025-03-15)
- Added: Task prioritization rules
- Reason: Improve handling of concurrent tasks

### v1.0.0 (2025-03-01)
- Initial version
```

### MEMORY.md 格式

```markdown
# Long-term Memory

**Last Updated**: 2025-03-31
**Total Entries**: 156

## User Profile

### Preferences
- Programming: Kotlin, Python
- Communication Style: Concise, technical
- Time Zone: UTC+8

### Patterns
- Prefers code examples over explanations
- Likes to see multiple approaches
- Values performance optimization

## Task Patterns

### Common Workflows
1. Code Review → Analyze → Suggest → Validate
2. Debugging → Identify → Diagnose → Fix → Test
3. Feature Design → Requirements → Architecture → Implementation

### Success Factors
- Clear requirements upfront
- Early validation
- Iterative refinement

## Domain Knowledge

### Android Development
- Jetpack Compose preferred
- Clean Architecture pattern
- Room + Retrofit for data

### API Design
- RESTful principles
- Versioning strategy
- Error handling patterns

## Recent Interactions

### 2025-03-31
- Discussed plugin architecture
- Reviewed evolution mechanism design
- User expressed interest in performance evaluation

---

*Entries are automatically consolidated from conversations and validated periodically*
```

---

## 附录

### A. 进化示例

#### 示例 1：行为进化

**场景**: 用户连续反馈 AI 回复过于冗长

**触发**: 用户多次点击"重新生成"并手动缩短回复

**分析**:
```
- 隐式反馈: 5次重新生成
- 编辑模式: 3次缩短回复
- 满意度评分: 平均2.5/5
```

**进化建议**:
```yaml
type: BEHAVIOR_MODIFICATION
rule_update:
  rule_id: "response_style"
  current: "Provide detailed explanations"
  proposed: "Be concise, elaborate only when asked"
  confidence: 0.85
  reason: "User consistently prefers shorter responses"
```

**结果**: AGENTS.md 更新，后续回复更简洁

#### 示例 2：知识进化

**场景**: 对话中多次提到用户偏好 Kotlin

**提取**:
```kotlin
KnowledgePoint(
    type = KnowledgeType.PREFERENCE,
    content = "User prefers Kotlin over Java for Android development",
    source = KnowledgeSource.UserInput("msg-123"),
    confidence = 0.9f,
    importance = Importance.HIGH,
    categories = setOf(KnowledgeCategory.USER_PROFILE, KnowledgeCategory.PREFERENCE)
)
```

**整合**: 添加到 MEMORY.md 的 User Profile 部分

#### 示例 3：能力进化

**场景**: 任务失败 - 需要 Git 操作能力

**缺口识别**:
```kotlin
CapabilityGap(
    capability = "git_operations",
    currentLevel = SkillLevel.NONE,
    desiredLevel = SkillLevel.INTERMEDIATE,
    priority = Priority.HIGH
)
```

**搜索结果**: 找到 "git-helper" 技能包

**安装**: 自动下载并安装技能

---

### B. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构
- [03-core-modules.md](./03-core-modules.md) - 核心模块设计
- [05-plugin-system.md](./05-plugin-system.md) - 插件系统

---

**文档维护**: 本文档应随着进化机制实现持续更新
