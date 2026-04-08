# Context 集成层 (core:context) 设计规格

## 概述

重构 core:context 模块，使其从孤立的内存实现变为真正的"上下文编排层"。委托 core:data 的 Repository 做持久化，委托 core:memory 做向量搜索，在"数据层"和"LLM 调用"之间插入智能上下文管理：token 预算分配、相关记忆注入、LLM 摘要压缩、完整请求组装。

## 设计决策

- **委托而非持有**：SessionContextManager 委托 SessionRepository（Room），不自己管理 ConcurrentHashMap
- **ChatRepository 集成**：OfflineFirstChatRepository 通过 ContextOrchestrator 发送消息，不再直接构造 ChatMessage 列表
- **LLM 摘要压缩**：超出 token 预算时用 LLM 生成对话摘要替换旧消息
- **自动记忆注入**：通过 EmbeddingService 向量搜索检索相关记忆，注入 system prompt
- **5 组件全实现**：SessionContextManager、MemoryContextManager、ContextWindowManager、ContextCompressionManager、ContextOrchestrator

## 模块依赖

```
core:context 依赖:
  ├── core:model         # 领域类型 (Session, Message, TokenBudget, SessionEvent)
  ├── core:common        # @CloudLlm, @IODispatcher
  ├── core:kernel        # ConfigManager (读取 maxTokens 配置)
  ├── core:data          # SessionRepository, MemoryRepository
  └── core:memory        # EmbeddingService (向量搜索)
```

---

## 包结构

```
core/context/src/main/java/.../core/context/
├── model/
│   ├── ConversationContext.kt      # 新增：完整 LLM 请求上下文
│   ├── CompressedContext.kt        # 新增：压缩结果
│   ├── TokenUsage.kt               # 新增：token 使用统计
│   └── ContextStats.kt             # 新增：上下文统计
├── SessionContextManager.kt        # 重写接口
├── MemoryContextManager.kt         # 新增接口
├── ContextWindowManager.kt         # 完善接口
├── ContextCompressionManager.kt    # 新增接口
├── ContextOrchestrator.kt          # 新增接口
├── internal/
│   ├── SessionContextManagerImpl.kt      # 重写：委托 SessionRepository
│   ├── MemoryContextManagerImpl.kt       # 新增
│   ├── ContextWindowManagerImpl.kt      # 完善
│   ├── ContextCompressionManagerImpl.kt # 新增
│   ├── ContextOrchestratorImpl.kt       # 新增
│   ├── TokenEstimatorImpl.kt            # 保留现有
│   └── prompts/
│       └── CompressionPrompt.kt         # 新增：LLM 摘要提示词
└── di/
    └── ContextModule.kt            # 扩展为 5 个 @Binds
```

---

## 数据流

```
User Message
    │
    ▼
ContextOrchestrator.buildContext(sessionId, userMessage)
    │
    ├─ 1. SessionContextManager.getConversationHistory(sessionId)
    │       └─ SessionRepository.getMessages() → List<Message>
    │
    ├─ 2. ContextWindowManager.calculateTokenBudget()
    │       └─ ConfigManager → TokenBudget
    │
    ├─ 3. MemoryContextManager.retrieveRelevantMemories(query, budget.memoryTokens)
    │       └─ EmbeddingService → MemoryRepository.vectorSearch() → List<Memory>
    │
    ├─ 4. if shouldCompress(messages, budget):
    │       ContextCompressionManager.compressHistory(messages, maxTokens)
    │           └─ LLM chat → summary → CompressedContext
    │
    ├─ 5. Allocate budgets, trim components
    │
    └─ 6. Return ConversationContext(
               systemPrompt, messages, tokenUsage, memories
           )
```

---

## 新增领域类型

### ConversationContext.kt

```kotlin
package com.loy.mingclaw.core.context.model

import com.loy.mingclaw.core.model.llm.ChatMessage
import com.loy.mingclaw.core.model.memory.Memory

data class ConversationContext(
    val systemPrompt: String,
    val messages: List<ChatMessage>,
    val tokenUsage: TokenUsage,
    val memories: List<Memory>,
)
```

### CompressedContext.kt

```kotlin
package com.loy.mingclaw.core.context.model

import com.loy.mingclaw.core.model.context.Message

data class CompressedContext(
    val summary: String,
    val summaryTokenCount: Int,
    val retainedMessages: List<Message>,
)
```

### TokenUsage.kt

```kotlin
package com.loy.mingclaw.core.context.model

import com.loy.mingclaw.core.model.TokenBudget

data class TokenUsage(
    val systemTokens: Int,
    val memoryTokens: Int,
    val conversationTokens: Int,
    val totalTokens: Int,
    val budget: TokenBudget,
)
```

### ContextStats.kt

```kotlin
package com.loy.mingclaw.core.context.model

data class ContextStats(
    val sessionId: String,
    val totalTokensUsed: Int,
    val compressionCount: Int,
    val memoriesInjected: Int,
    val budgetUtilization: Float,
)
```

---

## 公共接口

### SessionContextManager（重写）

```kotlin
interface SessionContextManager {
    suspend fun createSession(title: String? = null): Result<Session>
    suspend fun getSession(sessionId: String): Result<Session>
    suspend fun addMessage(sessionId: String, message: Message): Result<Message>
    suspend fun getConversationHistory(sessionId: String, limit: Int? = null): Result<List<Message>>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    fun observeSessionEvents(sessionId: String): Flow<SessionEvent>
}
```

- 委托 `SessionRepository`（core:data）做持久化
- `observeSessionEvents` 基于 SessionRepository 的 observe 转换为 SessionEvent Flow

### MemoryContextManager（新增）

```kotlin
interface MemoryContextManager {
    suspend fun retrieveRelevantMemories(query: String, maxTokens: Int): Result<List<Memory>>
}
```

- 委托 `EmbeddingService.generateEmbedding()` + `MemoryRepository.vectorSearch()`
- 按 maxTokens 逐条添加，超出预算截断

### ContextWindowManager（完善）

```kotlin
interface ContextWindowManager {
    fun calculateTokenBudget(): TokenBudget
    fun allocateTokenBudget(budget: TokenBudget, components: List<ContextComponent>): Map<String, Int>
    fun estimateTokens(content: String): Int
    fun shouldCompress(messages: List<Message>, budget: TokenBudget): Boolean
    fun getWindowStatistics(): WindowStatistics
}
```

- `shouldCompress` 检查消息 token 数是否超过预算的 80%
- 分配优先级：system(固定) > memory > conversation > tools

### ContextCompressionManager（新增）

```kotlin
interface ContextCompressionManager {
    suspend fun compressHistory(messages: List<Message>, maxTokens: Int): Result<CompressedContext>
}
```

- 保留最近 N 条消息，对旧消息用 LLM 生成摘要
- 摘要作为 system 消息插入，替换旧消息

### ContextOrchestrator（新增）

```kotlin
interface ContextOrchestrator {
    suspend fun buildContext(sessionId: String, userMessage: String): Result<ConversationContext>
    fun observeContextStats(): Flow<ContextStats>
}
```

- 顶层编排：历史 -> 预算 -> 记忆 -> 压缩 -> 组装
- 唯一暴露给 ChatRepository 的入口

---

## 内部组件

### SessionContextManagerImpl（重写）

- 委托 `SessionRepository`，删除 `ConcurrentHashMap`
- 保留 `MutableSharedFlow<SessionEvent>` 作为事件广播

### MemoryContextManagerImpl

```kotlin
@Singleton
internal class MemoryContextManagerImpl @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val embeddingService: EmbeddingService,
    private val tokenEstimator: TokenEstimator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : MemoryContextManager
```

- `retrieveRelevantMemories(query, maxTokens)`:
  1. 生成 query embedding
  2. vectorSearch(limit=10, threshold=0.5f)
  3. 按 maxTokens 截断

### ContextCompressionManagerImpl

```kotlin
@Singleton
internal class ContextCompressionManagerImpl @Inject constructor(
    @CloudLlm private val llmProvider: LlmProvider,
    private val tokenEstimator: TokenEstimator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContextCompressionManager
```

- `compressHistory(messages, maxTokens)`:
  1. 保留最近 N 条消息（N 可配置，默认保留最近 6 条）
  2. 旧消息拼接为文本，调用 `CompressionPrompt.build()` 生成 LLM 摘要
  3. 摘要 + 保留消息组成 CompressedContext
  4. 验证总 token 数不超过 maxTokens

### CompressionPrompt

```kotlin
internal object CompressionPrompt {
    fun build(conversationHistory: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = "你是一个对话摘要专家。请将以下对话历史总结为简洁的摘要，保留关键信息、用户偏好和重要决策。只返回摘要文本，不要其他文字。",
        ),
        ChatMessage(
            role = "user",
            content = "请总结以下对话：\n\n$conversationHistory",
        ),
    )
}
```

### ContextOrchestratorImpl

```kotlin
@Singleton
internal class ContextOrchestratorImpl @Inject constructor(
    private val sessionContextManager: SessionContextManager,
    private val memoryContextManager: MemoryContextManager,
    private val contextWindowManager: ContextWindowManager,
    private val compressionManager: ContextCompressionManager,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContextOrchestrator
```

- `buildContext(sessionId, userMessage)` 实现上述数据流的完整 6 步
- `observeContextStats()` 通过 `MutableStateFlow` 广播统计

---

## 与 ChatRepository 集成

`OfflineFirstChatRepository` 改造：

```kotlin
// 注入 ContextOrchestrator
@Singleton
internal class OfflineFirstChatRepository @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val contextOrchestrator: ContextOrchestrator,  // 新增
    @CloudLlm private val llmProvider: LlmProvider,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ChatRepository
```

`chatStream` 方法改造：
1. 先持久化用户消息到 SessionRepository
2. 调用 `contextOrchestrator.buildContext(sessionId, userMessage)` 获取 ConversationContext
3. 用 `context.messages` 替代手动构造的 ChatMessage 列表
4. 调用 `llmProvider.chatStream(model, context.messages, ...)`
5. 持久化 AI 回复

---

## DI 绑定

```kotlin
@Module @InstallIn(SingletonComponent::class)
internal abstract class ContextModule {
    @Binds @Singleton abstract fun bindTokenEstimator(impl: TokenEstimatorImpl): TokenEstimator
    @Binds @Singleton abstract fun bindSessionContextManager(impl: SessionContextManagerImpl): SessionContextManager
    @Binds @Singleton abstract fun bindContextWindowManager(impl: ContextWindowManagerImpl): ContextWindowManager
    @Binds @Singleton abstract fun bindMemoryContextManager(impl: MemoryContextManagerImpl): MemoryContextManager
    @Binds @Singleton abstract fun bindCompressionManager(impl: ContextCompressionManagerImpl): ContextCompressionManager
    @Binds @Singleton abstract fun bindContextOrchestrator(impl: ContextOrchestratorImpl): ContextOrchestrator
}
```

---

## 测试策略

| 测试文件 | 覆盖内容 |
|----------|----------|
| SessionContextManagerImplTest | 委托 SessionRepository CRUD、SessionEvent Flow |
| MemoryContextManagerImplTest | embedding -> vectorSearch -> token 截断 |
| ContextWindowManagerImplTest | 预算计算、分配、shouldCompress |
| ContextCompressionManagerImplTest | LLM 摘要、消息替换、maxTokens 截断 |
| ContextOrchestratorImplTest | 完整编排 + 边界场景（无记忆/无压缩） |

所有测试使用 MockK + Turbine，外部依赖全 mock。

---

## MVP 裁剪项

> **注意：以下裁剪项在实现中以 `// MVP: 后续增强` 注释标记**

| 组件 | 裁剪内容 | 延后阶段 |
|------|----------|----------|
| TokenEstimator | 保留 chars/4 启发式估算，不引入 BPE/tiktoken tokenizer | 后续接入 tiktoken |
| ContextCompressionManager | 单次摘要策略，不做多策略（滑动窗口、关键信息提取、消息合并） | 后续增强 |
| MemoryContextManager | 不做记忆过期清理、重要性衰减、记忆去重（由 evolution 层处理） | 后续增强 |
| ContextWindowManager | WindowStatistics 返回默认值，不追踪历史 token 使用量，不追踪 peak | 后续增强 |
| ContextOrchestrator | system prompt 基础指令硬编码，不动态模板化 | 后续由 Evolution 层通过 DynamicPromptBuilder 注入 |
| SessionContextManager | observeSessionEvents 基于 SessionRepository.observeMessages 简单映射，不做复杂事件聚合 | 后续增强 |
| ChatRepository | chatStream 不处理压缩后摘要的持久化到 Room | 后续增强 |

---

## 实现阶段

| 阶段 | 组件 | 核心交付 |
|------|------|----------|
| **C1** | 领域类型 + build.gradle.kts 依赖更新 + DI 扩展 | 新增 4 个 model 文件，更新依赖 |
| **C2** | SessionContextManager 重写 | 委托 SessionRepository |
| **C3** | MemoryContextManager + ContextWindowManager 完善 | 记忆检索 + 预算分配 |
| **C4** | ContextCompressionManager | LLM 摘要压缩 |
| **C5** | ContextOrchestrator | 完整编排 + 统计 |
| **C6** | ChatRepository 集成 | OfflineFirstChatRepository 接入 ContextOrchestrator |
