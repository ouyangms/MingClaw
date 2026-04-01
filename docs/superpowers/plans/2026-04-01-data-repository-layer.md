# Data Repository Layer (core:data) 实现计划

## 概述
实现数据仓库层 `core:data`，作为 core:database / core:network / core:memory 与上层（feature/、core:context 等）之间的胶水层。提供 4 个 Repository 接口及其 OfflineFirst 实现，封装数据源细节，暴露领域模型驱动的 API。

## 设计决策
- core:data 依赖 core:model、core:common、core:database、core:network、core:memory，不依赖上层模块
- Repository 接口以领域模型（core:model）为参数和返回值，内部通过 Mapper 进行 Entity ↔ Domain 转换
- MemoryRepository 委托 core:memory 的 MemoryStorage + EmbeddingService，而非直接访问 DAO
- ChatRepository 是编排型 Repository，同时依赖 SessionRepository（消息持久化）和 @CloudLlm LlmProvider（LLM 调用），不存在循环依赖
- MemoryRepository.vectorSearch 的 MVP 实现为内存过滤：加载全部记忆后用余弦相似度排序
- WorkspaceRepository.setActiveWorkspace 通过 deactivateAll() + update(active=true) 实现原子性切换
- Mapper 使用顶层扩展函数 asEntity() / asDomain()，保持双向映射一致性

## MVP 范围
- SessionRepository: 会话 CRUD + 消息持久化 + Flow 观察
- WorkspaceRepository: 工作区 CRUD + setActiveWorkspace 事务性切换
- MemoryRepository: 委托 MemoryStorage + vectorSearch 内存过滤
- ChatRepository: 编排型，封装 LlmProvider + SessionRepository，流式聊天 + 自动消息持久化
- 3 个 Mapper: SessionMapper, MessageMapper, WorkspaceMapper（双向映射 + roundTrip 测试）
- ChatRequest / ChatStreamResult 数据类
- DataModule: Hilt DI 绑定
- 完整单元测试

## 延迟实现
- WorkspaceRepository 的 Flow 观察（WorkspaceDao 暂无 observeAll）
- MemoryRepository 的混合搜索（HybridSearch + RRF）
- ChatRepository 的重试/错误恢复策略
- ChatRepository 的 token 统计和成本追踪
- Repository 层的缓存策略（内存缓存 + 过期策略）
- 离线同步（网络恢复后同步本地与远端）

---

## Task T1: 模块注册与基础设施

### settings.gradle.kts
- 添加 `include(":core:data")`

### core/data/build.gradle.kts
- 插件: mingclaw.android.library, mingclaw.hilt, kotlin.serialization
- 依赖: :core:model, :core:common, :core:database, :core:network, :core:memory
- 测试: junit, coroutines-test, mockk, turbine

---

## Task T2: Repository 公共接口

### SessionRepository (repository/SessionRepository.kt)
- createSession(title): Session
- getSession(sessionId): Session?
- updateSession(session): Session
- deleteSession(sessionId)
- observeAllSessions(): Flow<List<Session>>
- observeSession(sessionId): Flow<Session?>
- addMessage(sessionId, message): Message
- getMessages(sessionId, limit): List<Message>
- observeMessages(sessionId): Flow<List<Message>>
- deleteMessages(sessionId)

### WorkspaceRepository (repository/WorkspaceRepository.kt)
- createWorkspace(workspace): Workspace
- getWorkspace(workspaceId): Workspace?
- getActiveWorkspace(): Workspace?
- getAllWorkspaces(): List<Workspace>
- updateWorkspace(workspace): Workspace
- deleteWorkspace(workspaceId)
- setActiveWorkspace(workspaceId): Workspace

### MemoryRepository (repository/MemoryRepository.kt)
- save(memory): Result<Memory>
- get(memoryId): Result<Memory>
- update(memory): Result<Memory>
- delete(memoryId): Result<Unit>
- observeAll(): Flow<List<Memory>>
- search(query, limit): Result<List<Memory>>
- vectorSearch(queryEmbedding, limit, threshold): Result<List<Memory>>
- getStatistics(): MemoryStatistics
- cleanup(before): Result<Int>

### ChatRepository (repository/ChatRepository.kt)
- chatStream(request): Flow<ChatStreamResult>
- chat(request): Result<String>

### 数据类
- ChatRequest: sessionId, model, messages, temperature, maxTokens
- ChatStreamResult: Chunk(content, finishReason) | Complete(fullContent) | Error(message)

---

## Task T3: Entity ↔ Domain Mapper

### SessionMapper (mapper/SessionMapper.kt)
- Session.asEntity(): SessionEntity
  - Instant → Long (toEpochMilliseconds)
  - Map<String,String> → JSON String (MapSerializer)
  - SessionStatus → String (name)
- SessionEntity.asDomain(): Session
  - Long → Instant (fromEpochMilliseconds)
  - JSON String → Map<String,String> (容错: 解析失败返回 emptyMap)
  - String → SessionStatus (容错: 解析失败返回 Active)

### MessageMapper (mapper/MessageMapper.kt)
- Message.asEntity(): MessageEntity
  - ToolCall list → JSON String (ListSerializer(ToolCall.serializer()))
  - 空 toolCalls → null
- MessageEntity.asDomain(): Message
  - JSON String → ToolCall list (容错: 解析失败返回 emptyList)
  - null → emptyList
  - role String → MessageRole (容错: 解析失败返回 User)

### WorkspaceMapper (mapper/WorkspaceMapper.kt)
- Workspace.asEntity(): WorkspaceEntity
  - WorkspaceMetadata 分解为 description, tags, version, templateId
  - tags List<String> → JSON String (ListSerializer(String.serializer()))
- WorkspaceEntity.asDomain(): Workspace
  - description, tags, version, templateId → WorkspaceMetadata
  - tags JSON String → List<String> (容错: 解析失败返回 emptyList)

---

## Task T4: OfflineFirst 实现

### OfflineFirstSessionRepository (repository/internal/)
- 注入: SessionDao, MessageDao, @IODispatcher
- createSession: 生成 UUID + 时间戳 → insert
- addMessage: 持久化消息 + 更新 session.updatedAt
- getMessages: limit == Int.MAX_VALUE 时用 getAllBySessionId，否则用 getBySessionId(limit)
- observe*: DAO Flow → map asDomain()

### OfflineFirstWorkspaceRepository (repository/internal/)
- 注入: WorkspaceDao, @IODispatcher
- setActiveWorkspace: deactivateAll() + getById + update(isActive=true)
  - 未找到时抛 NoSuchElementException

### OfflineFirstMemoryRepository (repository/internal/)
- 注入: MemoryStorage, EmbeddingService, @IODispatcher
- 大部分方法委托 MemoryStorage
- vectorSearch (MVP): 调用 storage.getAll() 获取全部记忆 → 过滤非空 embedding → 用 EmbeddingService.similarity() 计算余弦相似度 → 按 threshold 过滤 → 按相似度降序排序 → take(limit)

### OfflineFirstChatRepository (repository/internal/)
- 注入: SessionRepository, @CloudLlm LlmProvider, @IODispatcher
- chat: 持久化用户消息 → 调用 llmProvider.chat → 持久化 AI 回复 → 返回 content
- chatStream: 持久化用户消息 → 调用 llmProvider.chatStream → 收集 chunks → 拼接完整内容 → 持久化 AI 回复 → emit Complete
  - LLM 失败时 emit Error

---

## Task T5: DI 模块

### DataModule (di/DataModule.kt)
- @Binds OfflineFirstSessionRepository → SessionRepository (@Singleton)
- @Binds OfflineFirstWorkspaceRepository → WorkspaceRepository (@Singleton)
- @Binds OfflineFirstMemoryRepository → MemoryRepository (@Singleton)
- @Binds OfflineFirstChatRepository → ChatRepository (@Singleton)

---

## Task T6: 单元测试

### SessionMapperTest
- asEntity / asDomain 映射验证
- roundTrip: entity → domain → entity 数据一致性
- roundTrip: domain → entity → domain 数据一致性
- 无效 status 容错
- 无效 metadata JSON 容错

### MessageMapperTest
- asEntity / asDomain 映射验证
- roundTrip 基本字段 + ToolCall 序列化一致性
- 空 toolCalls → null / null toolCalls → emptyList
- 无效 role 容错

### WorkspaceMapperTest
- asEntity / asDomain 映射验证
- roundTrip 双向一致性
- 无效 tags JSON 容错

### OfflineFirstSessionRepositoryTest
- createSession 生成 UUID 并 insert
- getSession 存在/不存在
- updateSession 更新时间戳
- addMessage 持久化 + 更新 session.updatedAt
- getMessages 限制数量
- observeAllSessions / observeMessages Flow 观察
- deleteMessages 委托 DAO

### OfflineFirstWorkspaceRepositoryTest
- createWorkspace / getWorkspace / getAllWorkspaces
- setActiveWorkspace 事务性操作（deactivateAll + update）
- setActiveWorkspace 未找到时抛异常
- deleteWorkspace 委托 DAO

### OfflineFirstMemoryRepositoryTest
- save / get / update / delete 委托 MemoryStorage
- vectorSearch 按相似度排序
- vectorSearch threshold 过滤
- getStatistics / cleanup / observeAll 委托

### OfflineFirstChatRepositoryTest
- chat 持久化用户消息 + AI 回复
- chat LLM 失败返回 failure
- chatStream 依次 emit Chunk + Complete
- chatStream 持久化用户消息和 AI 完整回复
- chatStream LLM 失败 emit Error

---

## Task T7: 构建验证
- `./gradlew :core:data:assembleDebug` 编译通过
- `./gradlew :core:data:test` 测试通过
- `./gradlew assembleDebug` 全量编译通过
- `./gradlew test` 全量测试通过
