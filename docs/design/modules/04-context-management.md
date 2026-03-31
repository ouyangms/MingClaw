# MingClaw 上下文管理设计

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [上下文管理概述](#上下文管理概述)
2. [会话管理](#会话管理)
3. [记忆检索](#记忆检索)
4. [上下文窗口管理](#上下文窗口管理)
5. [上下文压缩](#上下文压缩)
6. [依赖关系](#依赖关系)
7. [附录](#附录)

---

## 上下文管理概述

### 设计目标

MingClaw 上下文管理系统确保 AI 助手能够：

| 目标 | 说明 | 实现方式 |
|------|------|----------|
| **连贯对话** | 维持多轮对话的上下文 | 会话历史管理 |
| **智能检索** | 快速找到相关信息 | 向量相似度搜索 |
| **高效利用** | 最大化 Token 使用效率 | 动态窗口管理 |
| **渐进压缩** | 保留重要信息 | 智能摘要算法 |

### 架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Context Management Layer                        │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                      Context Orchestrator                         │  │
│  │  - 协调各个上下文模块                                              │  │
│  │  - 管理上下文生命周期                                              │  │
│  │  - 优化上下文分配                                                  │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌───────────────┬─────────────────┼─────────────────┬─────────────────┐ │
│  │               │                 │                 │                 │ │
│  ▼               ▼                 ▼                 ▼                 │ │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ │
│  │ Session   │ │  Memory   │ │  Window   │ │Compress   │ │  Cache    │ │
│  │ Manager   │ │ Retriever │ │  Manager  │ │  Manager  │ │  Manager  │ │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Data Layer                                     │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐             │
│  │ Room DB   │ │DataStore  │ │  Vector   │ │  File     │             │
│  │ (Sessions)│ │ (Config)  │ │  Store    │ │ System    │             │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘             │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 会话管理

### 核心接口

```kotlin
/**
 * 会话上下文管理器接口
 *
 * 职责：
 * - 管理会话生命周期
 * - 维护对话历史
 * - 估算 Token 使用
 * - 提供会话查询
 */
interface SessionContextManager {

    /**
     * 创建新会话
     */
    suspend fun createSession(
        title: String? = null,
        metadata: Map<String, String> = emptyMap()
    ): Result<Session>

    /**
     * 获取会话
     */
    suspend fun getSession(sessionId: String): Result<Session>

    /**
     * 获取会话上下文
     */
    suspend fun getSessionContext(sessionId: String): SessionContext

    /**
     * 添加消息
     */
    suspend fun addMessage(
        sessionId: String,
        message: Message
    ): Result<Message>

    /**
     * 更新消息
     */
    suspend fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String
    ): Result<Message>

    /**
     * 删除消息
     */
    suspend fun deleteMessage(
        sessionId: String,
        messageId: String
    ): Result<Unit>

    /**
     * 获取会话历史
     */
    suspend fun getConversationHistory(
        sessionId: String,
        limit: Int? = null
    ): Result<List<Message>>

    /**
     * 搜索消息
     */
    suspend fun searchMessages(
        sessionId: String,
        query: String,
        limit: Int = 20
    ): Result<List<Message>>

    /**
     * 归档会话
     */
    suspend fun archiveSession(sessionId: String): Result<Unit>

    /**
     * 删除会话
     */
    suspend fun deleteSession(sessionId: String): Result<Unit>

    /**
     * 获取所有会话
     */
    suspend fun getAllSessions(
        includeArchived: Boolean = false
    ): Result<List<Session>>

    /**
     * 监听会话变化
     */
    fun watchSession(sessionId: String): Flow<SessionEvent>
}
```

### 实现类

```kotlin
/**
 * 会话上下文管理器实现
 */
@Singleton
internal class SessionContextManagerImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val tokenEstimator: TokenEstimator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : SessionContextManager {

    // 会话缓存
    private val sessionCache = mutableMapOf<String, Session>()

    // 消息缓存
    private val messageCache = mutableMapOf<String, MutableList<Message>>()

    // 事件流
    private val sessionEvents = mutableMapOf<String, MutableSharedFlow<SessionEvent>>()

    override suspend fun createSession(
        title: String?,
        metadata: Map<String, String>
    ): Result<Session> = withContext(ioDispatcher) {
        try {
            val session = Session(
                id = UUID.randomUUID().toString(),
                title = title ?: generateSessionTitle(),
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
                metadata = metadata,
                status = SessionStatus.Active
            )

            // 保存到数据库
            sessionDao.insert(session.toEntity())

            // 更新缓存
            sessionCache[session.id] = session
            messageCache[session.id] = mutableListOf()

            // 创建事件流
            sessionEvents[session.id] = MutableSharedFlow(
                replay = 10,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )

            // 发布事件
            emitEvent(session.id, SessionEvent.Created(session))

            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSession(sessionId: String): Result<Session> {
        // 先查缓存
        sessionCache[sessionId]?.let { return Result.success(it) }

        // 查数据库
        return withContext(ioDispatcher) {
            try {
                val entity = sessionDao.getById(sessionId)
                    ?: return@withContext Result.failure(
                        SessionNotFoundException(sessionId)
                    )

                val session = entity.toDomain()
                sessionCache[sessionId] = session
                Result.success(session)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getSessionContext(sessionId: String): SessionContext {
        val session = getSession(sessionId).getOrThrow()

        // 获取消息
        val messages = messageCache[sessionId]
            ?: loadMessages(sessionId).also {
                messageCache[sessionId] = it.toMutableList()
            }

        // 计算 Token 统计
        val tokenStats = calculateTokenStats(messages)

        return SessionContext(
            sessionId = session.id,
            title = session.title,
            messages = messages,
            messageCount = messages.size,
            totalTokens = tokenStats.totalTokens,
            metadata = session.metadata,
            status = session.status
        )
    }

    override suspend fun addMessage(
        sessionId: String,
        message: Message
    ): Result<Message> = withContext(ioDispatcher) {
        try {
            // 验证会话存在
            val session = getSession(sessionId).getOrThrow()

            // 设置消息属性
            val messageWithId = message.copy(
                id = message.id ?: UUID.randomUUID().toString(),
                sessionId = sessionId,
                timestamp = message.timestamp ?: Clock.System.now()
            )

            // 保存到数据库
            messageDao.insert(messageWithId.toEntity())

            // 更新缓存
            messageCache.getOrPut(sessionId) { mutableListOf() }
                .add(messageWithId)

            // 更新会话时间
            val updatedSession = session.copy(
                updatedAt = Clock.System.now()
            )
            sessionCache[sessionId] = updatedSession
            sessionDao.update(updatedSession.toEntity())

            // 发布事件
            emitEvent(sessionId, SessionEvent.MessageAdded(messageWithId))

            Result.success(messageWithId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String
    ): Result<Message> = withContext(ioDispatcher) {
        try {
            val oldMessage = messageDao.getById(messageId)
                ?: return@withContext Result.failure(
                    MessageNotFoundException(messageId)
                )

            val updatedMessage = oldMessage.toDomain().copy(
                content = content,
                editedAt = Clock.System.now()
            )

            messageDao.update(updatedMessage.toEntity())

            // 更新缓存
            messageCache[sessionId]?.let { messages ->
                val index = messages.indexOfFirst { it.id == messageId }
                if (index >= 0) {
                    messages[index] = updatedMessage
                }
            }

            emitEvent(sessionId, SessionEvent.MessageUpdated(updatedMessage))

            Result.success(updatedMessage)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMessage(
        sessionId: String,
        messageId: String
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            messageDao.delete(messageId)

            // 更新缓存
            messageCache[sessionId]?.removeIf { it.id == messageId }

            emitEvent(sessionId, SessionEvent.MessageDeleted(messageId))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getConversationHistory(
        sessionId: String,
        limit: Int?
    ): Result<List<Message>> = withContext(ioDispatcher) {
        try {
            val messages = if (limit != null) {
                messageDao.getBySessionId(sessionId, limit)
            } else {
                messageDao.getAllBySessionId(sessionId)
            }.map { it.toDomain() }

            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchMessages(
        sessionId: String,
        query: String,
        limit: Int
    ): Result<List<Message>> = withContext(ioDispatcher) {
        try {
            // 全文搜索
            val results = messageDao.search(sessionId, "%$query%", limit)
                .map { it.toDomain() }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun archiveSession(sessionId: String): Result<Unit> {
        return updateSessionStatus(sessionId, SessionStatus.Archived)
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                // 删除所有消息
                messageDao.deleteBySessionId(sessionId)

                // 删除会话
                sessionDao.delete(sessionId)

                // 清理缓存
                sessionCache.remove(sessionId)
                messageCache.remove(sessionId)
                sessionEvents.remove(sessionId)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getAllSessions(
        includeArchived: Boolean
    ): Result<List<Session>> = withContext(ioDispatcher) {
        try {
            val sessions = if (includeArchived) {
                sessionDao.getAll()
            } else {
                sessionDao.getActiveSessions()
            }.map { it.toDomain() }

            // 更新缓存
            sessions.forEach { sessionCache[it.id] = it }

            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun watchSession(sessionId: String): Flow<SessionEvent> {
        return sessionEvents.getOrPut(sessionId) {
            MutableSharedFlow(
                replay = 10,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }.asSharedFlow()
    }

    // 私有辅助方法
    private suspend fun loadMessages(sessionId: String): List<Message> {
        return messageDao.getAllBySessionId(sessionId)
            .map { it.toDomain() }
    }

    private fun calculateTokenStats(messages: List<Message>): TokenStats {
        return TokenStats(
            totalTokens = messages.sumOf {
                tokenEstimator.estimate(it.content)
            },
            userTokens = messages.filter { it.role == MessageRole.User }
                .sumOf { tokenEstimator.estimate(it.content) },
            assistantTokens = messages.filter { it.role == MessageRole.Assistant }
                .sumOf { tokenEstimator.estimate(it.content) }
        )
    }

    private suspend fun updateSessionStatus(
        sessionId: String,
        status: SessionStatus
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val session = getSession(sessionId).getOrThrow()
            val updated = session.copy(
                status = status,
                updatedAt = Clock.System.now()
            )

            sessionDao.update(updated.toEntity())
            sessionCache[sessionId] = updated

            emitEvent(sessionId, SessionEvent.StatusChanged(status))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateSessionTitle(): String {
        return "Session ${Clock.System.now().toEpochMilliseconds()}"
    }

    private suspend fun emitEvent(sessionId: String, event: SessionEvent) {
        sessionEvents[sessionId]?.emit(event)
    }
}
```

---

## 记忆检索

### 核心接口

```kotlin
/**
 * 记忆上下文管理器接口
 *
 * 职责：
 * - 检索相关记忆
 * - 管理永久记忆
 * - 处理临时记忆
 * - 更新记忆重要性
 */
interface MemoryContextManager {

    /**
     * 检索相关记忆
     *
     * @param query 查询内容
     * @param maxTokens 最大 Token 数
     * @param memoryType 记忆类型
     * @return 相关记忆列表
     */
    suspend fun retrieveRelevantMemories(
        query: String,
        maxTokens: Int = 1000,
        memoryType: MemoryType = MemoryType.All
    ): List<Memory>

    /**
     * 添加记忆
     */
    suspend fun addMemory(
        content: String,
        type: MemoryType,
        metadata: Map<String, String> = emptyMap()
    ): Result<Memory>

    /**
     * 更新记忆
     */
    suspend fun updateMemory(
        memoryId: String,
        content: String
    ): Result<Memory>

    /**
     * 删除记忆
     */
    suspend fun deleteMemory(memoryId: String): Result<Unit>

    /**
     * 搜索记忆
     */
    suspend fun searchMemories(
        query: String,
        limit: Int = 20
    ): Result<List<Memory>>

    /**
     * 获取永久记忆
     */
    suspend fun getPermanentMemories(): List<Memory>

    /**
     * 更新记忆重要性
     */
    suspend fun updateMemoryImportance(
        memoryId: String,
        importance: Float
    ): Result<Unit>

    /**
     * 压缩旧记忆
     */
    suspend fun compressOldMemories(
        threshold: Float = 0.3f
    ): Result<Int>

    /**
     * 获取记忆统计
     */
    suspend fun getMemoryStatistics(): MemoryStatistics
}
```

### 实现类

```kotlin
/**
 * 记忆上下文管理器实现
 */
@Singleton
internal class MemoryContextManagerImpl @Inject constructor(
    private val memoryDao: MemoryDao,
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val tokenEstimator: TokenEstimator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : MemoryContextManager {

    // 记忆缓存
    private val memoryCache = mutableMapOf<String, Memory>()

    // 向量索引缓存
    private val indexCache = mutableMapOf<String, VectorIndex>()

    override suspend fun retrieveRelevantMemories(
        query: String,
        maxTokens: Int,
        memoryType: MemoryType
    ): List<Memory> = withContext(ioDispatcher) {

        // 1. 生成查询向量
        val queryEmbedding = embeddingService.generateEmbedding(query)

        // 2. 向量搜索
        val similarMemories = vectorStore.search(
            embedding = queryEmbedding,
            limit = 50, // 获取更多候选
            filter = if (memoryType == MemoryType.All) {
                null
            } else {
                mapOf("type" to memoryType.name)
            }
        )

        // 3. 按 Token 预算筛选
        var totalTokens = 0
        val selected = mutableListOf<Memory>()

        for (memoryId in similarMemories) {
            val memory = getMemory(memoryId) ?: continue
            val tokens = tokenEstimator.estimate(memory.content)

            if (totalTokens + tokens <= maxTokens) {
                selected.add(memory)
                totalTokens += tokens

                // 更新访问计数
                updateMemoryAccessCount(memory.id)
            }

            if (totalTokens >= maxTokens) break
        }

        selected
    }

    override suspend fun addMemory(
        content: String,
        type: MemoryType,
        metadata: Map<String, String>
    ): Result<Memory> = withContext(ioDispatcher) {
        try {
            // 1. 生成嵌入向量
            val embedding = embeddingService.generateEmbedding(content)

            // 2. 创建记忆对象
            val memory = Memory(
                id = UUID.randomUUID().toString(),
                content = content,
                type = type,
                embedding = embedding,
                metadata = metadata,
                importance = calculateInitialImportance(content, type),
                createdAt = Clock.System.now(),
                accessedAt = Clock.System.now(),
                accessCount = 0
            )

            // 3. 保存到数据库
            memoryDao.insert(memory.toEntity())

            // 4. 添加到向量索引
            vectorStore.add(
                id = memory.id,
                embedding = embedding,
                metadata = metadata + ("type" to type.name)
            )

            // 5. 更新缓存
            memoryCache[memory.id] = memory

            Result.success(memory)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMemory(
        memoryId: String,
        content: String
    ): Result<Memory> = withContext(ioDispatcher) {
        try {
            val memory = getMemory(memoryId)
                ?: return@withContext Result.failure(
                    MemoryNotFoundException(memoryId)
                )

            // 生成新嵌入
            val newEmbedding = embeddingService.generateEmbedding(content)

            val updated = memory.copy(
                content = content,
                embedding = newEmbedding,
                updatedAt = Clock.System.now()
            )

            // 更新数据库
            memoryDao.update(updated.toEntity())

            // 更新向量索引
            vectorStore.update(memoryId, newEmbedding)

            // 更新缓存
            memoryCache[memoryId] = updated

            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteMemory(memoryId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                memoryDao.delete(memoryId)
                vectorStore.delete(memoryId)
                memoryCache.remove(memoryId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun searchMemories(
        query: String,
        limit: Int
    ): Result<List<Memory>> = withContext(ioDispatcher) {
        try {
            // 全文搜索
            val results = memoryDao.search("%$query%", limit)
                .map { it.toDomain() }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPermanentMemories(): List<Memory> {
        return memoryDao.getByType(MemoryType.Permanent.name)
            .map { it.toDomain() }
    }

    override suspend fun updateMemoryImportance(
        memoryId: String,
        importance: Float
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val memory = getMemory(memoryId)
                ?: return@withContext Result.failure(
                    MemoryNotFoundException(memoryId)
                )

            val updated = memory.copy(importance = importance.coerceIn(0f, 1f))
            memoryDao.update(updated.toEntity())
            memoryCache[memoryId] = updated

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun compressOldMemories(threshold: Float): Result<Int> {
        return withContext(ioDispatcher) {
            try {
                // 1. 查找低重要性记忆
                val toCompress = memoryDao.getLowImportance(threshold)
                    .map { it.toDomain() }

                // 2. 分组压缩
                val compressedCount = compressMemories(toCompress)

                Result.success(compressedCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getMemoryStatistics(): MemoryStatistics {
        return MemoryStatistics(
            totalMemories = memoryDao.count(),
            memoriesByType = MemoryType.entries.associateWith { type ->
                memoryDao.countByType(type.name)
            },
            averageImportance = memoryDao.getAverageImportance(),
            totalTokens = memoryDao.getTotalTokens()
        )
    }

    // 私有辅助方法
    private suspend fun getMemory(memoryId: String): Memory? {
        return memoryCache[memoryId]
            ?: memoryDao.getById(memoryId)?.toDomain()?.also {
                memoryCache[memoryId] = it
            }
    }

    private fun calculateInitialImportance(
        content: String,
        type: MemoryType
    ): Float {
        val baseImportance = when (type) {
            MemoryType.Permanent -> 0.8f
            MemoryType.LongTerm -> 0.6f
            MemoryType.ShortTerm -> 0.4f
            MemoryType.Temporary -> 0.2f
        }

        // 根据内容长度调整
        val lengthFactor = content.length.coerceIn(0, 1000) / 1000f

        return (baseImportance + lengthFactor * 0.2f).coerceIn(0f, 1f)
    }

    private suspend fun updateMemoryAccessCount(memoryId: String) {
        val memory = getMemory(memoryId) ?: return
        val updated = memory.copy(
            accessCount = memory.accessCount + 1,
            accessedAt = Clock.System.now()
        )
        memoryDao.update(updated.toEntity())
        memoryCache[memoryId] = updated
    }

    private suspend fun compressMemories(memories: List<Memory>): Int {
        // 使用 LLM 压缩相关记忆
        var compressedCount = 0
        memories.chunked(10).forEach { chunk ->
            val summary = generateSummary(chunk)
            if (summary != null) {
                // 删除旧记忆，添加摘要
                chunk.forEach { memoryDao.delete(it.id) }
                addMemory(summary, MemoryType.LongTerm)
                compressedCount += chunk.size
            }
        }
        return compressedCount
    }

    private suspend fun generateSummary(memories: List<Memory>): String? {
        // 调用 LLM 生成摘要
        // 实现略
        return null
    }
}
```

---

## 上下文窗口管理

### 核心接口

```kotlin
/**
 * 上下文窗口管理器接口
 *
 * 职责：
 * - 计算 Token 预算
 * - 分配 Token 到各组件
 * - 优化上下文窗口使用
 * - 监控 Token 使用情况
 */
interface ContextWindowManager {

    /**
     * 计算 Token 预算
     */
    fun calculateTokenBudget(request: UserRequest): TokenBudget

    /**
     * 分配 Token 预算
     */
    fun allocateTokenBudget(
        budget: TokenBudget,
        components: List<ContextComponent>
    ): Map<String, Int>

    /**
     * 估算内容 Token 数
     */
    fun estimateTokens(content: String): Int

    /**
     * 检查是否需要压缩
     */
    fun shouldCompress(context: SessionContext): Boolean

    /**
     * 获取窗口统计
     */
    fun getWindowStatistics(): WindowStatistics

    /**
     * 监听 Token 使用
     */
    fun watchTokenUsage(): Flow<TokenUsageEvent>
}
```

### 实现类

```kotlin
/**
 * 上下文窗口管理器实现
 */
@Singleton
internal class ContextWindowManagerImpl @Inject constructor(
    private val tokenEstimator: TokenEstimator,
    private val configManager: KernelConfigManager
) : ContextWindowManager {

    // Token 使用历史
    private val usageHistory = CircularBuffer<TokenUsageRecord>(100)

    // 使用事件流
    private val _usageEvents = MutableSharedFlow<TokenUsageEvent>(
        replay = 50,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun calculateTokenBudget(request: UserRequest): TokenBudget {
        val config = configManager.getConfig()
        val maxTokens = config.maxTokens

        // 系统保留
        val systemReserved = 1000

        // 根据请求类型调整预算
        val requestComplexity = estimateRequestComplexity(request)

        val available = maxTokens - systemReserved
        return TokenBudget(
            totalTokens = maxTokens,
            systemTokens = systemReserved,
            memoryTokens = (available * 0.2 * requestComplexity.memoryMultiplier).toInt(),
            toolTokens = (available * 0.15 * requestComplexity.toolMultiplier).toInt(),
            conversationTokens = (available * 0.65 * requestComplexity.conversationMultiplier).toInt()
        )
    }

    override fun allocateTokenBudget(
        budget: TokenBudget,
        components: List<ContextComponent>
    ): Map<String, Int> {

        val allocation = mutableMapOf<String, Int>()
        var remaining = budget.totalTokens - budget.systemTokens

        // 按优先级排序
        val sorted = components.sortedByDescending { it.priority }

        for (component in sorted) {
            val allocated = minOf(
                component.requestedTokens,
                remaining,
                component.maxTokens
            )

            allocation[component.id] = allocated
            remaining -= allocated

            if (remaining <= 0) break
        }

        return allocation
    }

    override fun estimateTokens(content: String): Int {
        return tokenEstimator.estimate(content)
    }

    override fun shouldCompress(context: SessionContext): Boolean {
        val config = configManager.getConfig()
        val compressionThreshold = config.compressionThreshold

        return context.totalTokens > compressionThreshold
    }

    override fun getWindowStatistics(): WindowStatistics {
        val recentUsage = usageHistory.toList()
            .takeLast(20)

        return WindowStatistics(
            averageTokenUsage = recentUsage.map { it.totalTokens }.average(),
            peakTokenUsage = recentUsage.maxOfOrNull { it.totalTokens } ?: 0,
            compressionCount = recentUsage.count { it.wasCompressed },
            cacheHitRate = calculateCacheHitRate()
        )
    }

    override fun watchTokenUsage(): Flow<TokenUsageEvent> {
        return _usageEvents.asSharedFlow()
    }

    // 私有辅助方法
    private fun estimateRequestComplexity(request: UserRequest): RequestComplexity {
        val length = request.content.length
        val hasToolUse = request.hasToolRequests
        val hasHistory = request.conversationDepth > 0

        return RequestComplexity(
            memoryMultiplier = when {
                length > 500 -> 1.5f
                length > 200 -> 1.2f
                else -> 1.0f
            },
            toolMultiplier = if (hasToolUse) 1.3f else 1.0f,
            conversationMultiplier = when {
                hasHistory && request.conversationDepth > 10 -> 0.8f
                hasHistory -> 1.0f
                else -> 1.2f
            }
        )
    }

    private fun calculateCacheHitRate(): Double {
        // 实现缓存命中率计算
        return 0.0
    }
}
```

---

## 上下文压缩

### 核心接口

```kotlin
/**
 * 上下文压缩管理器接口
 */
interface ContextCompressionManager {

    /**
     * 压缩会话上下文
     */
    suspend fun compressContext(
        context: SessionContext,
        targetTokens: Int
    ): Result<CompressedContext>

    /**
     * 生成对话摘要
     */
    suspend fun generateSummary(
        messages: List<Message>
    ): Result<String>

    /**
     * 提取关键信息
     */
    suspend fun extractKeyInformation(
        messages: List<Message>
    ): Result<List<KeyPoint>>

    /**
     * 合并相似消息
     */
    suspend fun mergeSimilarMessages(
        messages: List<Message>
    ): Result<List<Message>>
}
```

### 实现类

```kotlin
/**
 * 上下文压缩管理器实现
 */
@Singleton
internal class ContextCompressionManagerImpl @Inject constructor(
    private val llmService: LlmService,
    private val embeddingService: EmbeddingService,
    private val tokenEstimator: TokenEstimator
) : ContextCompressionManager {

    override suspend fun compressContext(
        context: SessionContext,
        targetTokens: Int
    ): Result<CompressedContext> = withContext(Dispatchers.Default) {
        try {
            val currentTokens = tokenEstimator.estimate(
                context.messages.joinToString("\n") { it.content }
            )

            if (currentTokens <= targetTokens) {
                return@withContext Result.success(
                    CompressedContext(
                        messages = context.messages,
                        summary = null,
                        keyPoints = emptyList(),
                        compressionRatio = 1.0,
                        originalTokens = currentTokens,
                        compressedTokens = currentTokens
                    )
                )
            }

            // 1. 生成摘要
            val summary = generateSummary(context.messages).getOrNull()

            // 2. 提取关键信息
            val keyPoints = extractKeyInformation(context.messages).getOrNull()

            // 3. 选择保留的消息
            val retainedMessages = selectMessagesToRetain(
                context.messages,
                targetTokens
            )

            // 4. 构建压缩后的上下文
            val compressedContent = buildCompressedContent(
                summary,
                keyPoints,
                retainedMessages
            )

            val compressedTokens = tokenEstimator.estimate(compressedContent)

            Result.success(
                CompressedContext(
                    messages = retainedMessages,
                    summary = summary,
                    keyPoints = keyPoints,
                    compressionRatio = compressedTokens.toDouble() / currentTokens,
                    originalTokens = currentTokens,
                    compressedTokens = compressedTokens
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateSummary(
        messages: List<Message>
    ): Result<String> = withContext(Dispatchers.Default) {
        try {
            val prompt = buildString {
                appendLine("Please provide a concise summary of the following conversation:")
                appendLine()
                messages.forEach { message ->
                    appendLine("${message.role.name}: ${message.content}")
                }
                appendLine()
                appendLine("Summary:")
            }

            val response = llmService.generate(
                prompt = prompt,
                maxTokens = 500
            )

            Result.success(response.text.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun extractKeyInformation(
        messages: List<Message>
    ): Result<List<KeyPoint>> = withContext(Dispatchers.Default) {
        try {
            // 使用 LLM 提取关键信息
            val prompt = buildString {
                appendLine("Extract key information points from this conversation:")
                appendLine()
                messages.forEach { message ->
                    appendLine("${message.role.name}: ${message.content}")
                }
                appendLine()
                appendLine("Key points (one per line):")
            }

            val response = llmService.generate(
                prompt = prompt,
                maxTokens = 1000
            )

            val keyPoints = response.text.lines()
                .filter { it.isNotBlank() }
                .mapIndexed { index, line ->
                    KeyPoint(
                        id = UUID.randomUUID().toString(),
                        content = line.trim(),
                        importance = calculatePointImportance(line)
                    )
                }

            Result.success(keyPoints)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mergeSimilarMessages(
        messages: List<Message>
    ): Result<List<Message>> = withContext(Dispatchers.Default) {
        try {
            // 生成消息嵌入
            val embeddings = messages.map { message ->
                embeddingService.generateEmbedding(message.content)
            }

            // 计算相似度矩阵
            val similarityMatrix = calculateSimilarityMatrix(embeddings)

            // 合并相似消息
            val merged = mutableListOf<Message>()
            val mergedIndices = mutableSetOf<Int>()

            for (i in messages.indices) {
                if (i in mergedIndices) continue

                val similarIndices = findSimilarMessages(
                    i,
                    similarityMatrix,
                    threshold = 0.85
                )

                if (similarIndices.size > 1) {
                    // 合并消息
                    val mergedMessage = mergeMessages(
                        similarIndices.map { messages[it] }
                    )
                    merged.add(mergedMessage)
                    mergedIndices.addAll(similarIndices)
                } else {
                    merged.add(messages[i])
                }
            }

            Result.success(merged)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 私有辅助方法
    private suspend fun selectMessagesToRetain(
        messages: List<Message>,
        targetTokens: Int
    ): List<Message> {

        // 1. 为每条消息计算重要性分数
        val scored = messages.mapIndexed { index, message ->
            val score = calculateMessageScore(message, index, messages)
            IndexedMessage(index, message, score)
        }

        // 2. 按重要性排序
        val sorted = scored.sortedByDescending { it.score }

        // 3. 贪婪选择消息
        val selected = mutableListOf<Message>()
        var usedTokens = 0

        for (item in sorted) {
            val tokens = tokenEstimator.estimate(item.message.content)
            if (usedTokens + tokens <= targetTokens) {
                selected.add(item.message)
                usedTokens += tokens
            }
        }

        // 4. 按原始顺序排列
        return selected.sortedBy { message ->
            messages.indexOf(message)
        }
    }

    private fun calculateMessageScore(
        message: Message,
        index: Int,
        allMessages: List<Message>
    ): Float {
        var score = 1.0f

        // 最近的消息得分更高
        score *= (1.0 + (index.toFloat() / allMessages.size))

        // 用户消息得分更高
        if (message.role == MessageRole.User) {
            score *= 1.2f
        }

        // 包含工具调用的消息得分更高
        if (message.toolCalls.isNotEmpty()) {
            score *= 1.3f
        }

        // 较长的消息得分略低（避免过长消息）
        val lengthPenalty = 1.0 - minOf(
            message.content.length / 5000.0,
            0.3
        )
        score *= lengthPenalty

        return score
    }

    private fun buildCompressedContent(
        summary: String?,
        keyPoints: List<KeyPoint>?,
        messages: List<Message>
    ): String {
        return buildString {
            summary?.let {
                appendLine("## Conversation Summary")
                appendLine(it)
                appendLine()
            }

            keyPoints?.let {
                if (it.isNotEmpty()) {
                    appendLine("## Key Points")
                    it.forEach { point ->
                        appendLine("- ${point.content}")
                    }
                    appendLine()
                }
            }

            appendLine("## Recent Messages")
            messages.takeLast(20).forEach { message ->
                appendLine("${message.role.name}: ${message.content}")
            }
        }
    }

    private fun calculatePointImportance(point: String): Float {
        // 简单的重要性计算
        return when {
            point.contains("important", ignoreCase = true) -> 0.9f
            point.contains("remember", ignoreCase = true) -> 0.85f
            point.contains("note", ignoreCase = true) -> 0.8f
            point.length > 100 -> 0.7f
            else -> 0.5f
        }
    }

    private fun calculateSimilarityMatrix(
        embeddings: List<FloatArray>
    ): Array<FloatArray> {
        val size = embeddings.size
        val matrix = Array(size) { FloatArray(size) }

        for (i in 0 until size) {
            for (j in i until size) {
                val similarity = cosineSimilarity(embeddings[i], embeddings[j])
                matrix[i][j] = similarity
                matrix[j][i] = similarity
            }
        }

        return matrix
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return (dotProduct / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    private fun findSimilarMessages(
        index: Int,
        matrix: Array<FloatArray>,
        threshold: Float
    ): List<Int> {
        val similar = mutableListOf(index)

        for (i in matrix[index].indices) {
            if (i != index && matrix[index][i] >= threshold) {
                similar.add(i)
            }
        }

        return similar
    }

    private fun mergeMessages(messages: List<Message>): Message {
        val mergedContent = buildString {
            appendLine("[Merged ${messages.size} similar messages]")
            messages.forEach { message ->
                appendLine(message.content)
            }
        }

        return messages.first().copy(
            content = mergedContent.trim(),
            merged = true,
            mergedCount = messages.size
        )
    }
}
```

---

## 依赖关系

### 模块依赖图

```
┌─────────────────────────────────────────────────────────────────┐
│                     Context Orchestrator                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Session    │  │   Memory     │  │   Window     │         │
│  │   Manager    │  │   Retriever  │  │   Manager    │         │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
│         │                  │                  │                  │
└─────────┼──────────────────┼──────────────────┼──────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Shared Dependencies                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │    Token     │  │  Embedding   │  │     LLM      │         │
│  │  Estimator   │  │   Service    │  │   Service    │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                          Data Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  Session DAO │  │  Memory DAO  │  │ Vector Store │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

### 依赖说明

| 模块 | 依赖 | 说明 |
|------|------|------|
| **SessionContextManager** | SessionDao, MessageDao, TokenEstimator | 会话数据管理 |
| **MemoryContextManager** | MemoryDao, EmbeddingService, VectorStore | 记忆检索和存储 |
| **ContextWindowManager** | TokenEstimator, KernelConfigManager | Token 预算管理 |
| **ContextCompressionManager** | LlmService, EmbeddingService, TokenEstimator | 上下文压缩 |

---

## 附录

### A. 数据类定义

```kotlin
/**
 * 会话数据类
 */
@Serializable
data class Session(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val metadata: Map<String, String>,
    val status: SessionStatus
)

/**
 * 会话状态
 */
enum class SessionStatus {
    Active,
    Archived,
    Deleted
}

/**
 * 消息数据类
 */
@Serializable
data class Message(
    val id: String? = null,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Instant? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val editedAt: Instant? = null,
    val merged: Boolean = false,
    val mergedCount: Int = 0
)

/**
 * 消息角色
 */
enum class MessageRole {
    User,
    Assistant,
    System,
    Tool
}

/**
 * 会话上下文
 */
data class SessionContext(
    val sessionId: String,
    val title: String,
    val messages: List<Message>,
    val messageCount: Int,
    val totalTokens: Int,
    val metadata: Map<String, String>,
    val status: SessionStatus
)

/**
 * 记忆数据类
 */
@Serializable
data class Memory(
    val id: String,
    val content: String,
    val type: MemoryType,
    val embedding: FloatArray,
    val metadata: Map<String, String>,
    val importance: Float,
    val createdAt: Instant,
    val accessedAt: Instant,
    val accessCount: Int,
    val updatedAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Memory) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

/**
 * 记忆类型
 */
enum class MemoryType {
    Permanent,
    LongTerm,
    ShortTerm,
    Temporary,
    All
}

/**
 * 压缩后的上下文
 */
data class CompressedContext(
    val messages: List<Message>,
    val summary: String?,
    val keyPoints: List<KeyPoint>,
    val compressionRatio: Double,
    val originalTokens: Int,
    val compressedTokens: Int
)

/**
 * 关键信息点
 */
@Serializable
data class KeyPoint(
    val id: String,
    val content: String,
    val importance: Float
)
```

### B. 事件定义

```kotlin
/**
 * 会话事件
 */
sealed interface SessionEvent {
    data class Created(val session: Session) : SessionEvent
    data class MessageAdded(val message: Message) : SessionEvent
    data class MessageUpdated(val message: Message) : SessionEvent
    data class MessageDeleted(val messageId: String) : SessionEvent
    data class StatusChanged(val status: SessionStatus) : SessionEvent
    data class Deleted(val sessionId: String) : SessionEvent
}

/**
 * Token 使用事件
 */
sealed interface TokenUsageEvent {
    data class BudgetCalculated(
        val budget: TokenBudget,
        val timestamp: Instant = Clock.System.now()
    ) : TokenUsageEvent

    data class CompressionTriggered(
        val sessionId: String,
        val originalTokens: Int,
        val targetTokens: Int,
        val timestamp: Instant = Clock.System.now()
    ) : TokenUsageEvent

    data class LimitExceeded(
        val sessionId: String,
        val tokens: Int,
        val limit: Int,
        val timestamp: Instant = Clock.System.now()
    ) : TokenUsageEvent
}
```

### C. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构设计
- [03-core-modules.md](./03-core-modules.md) - 核心模块设计
- [06-memory-management.md](./06-memory-management.md) - 记忆管理详细设计

---

**文档维护**: 本文档应随着上下文管理功能的实现持续更新
**审查周期**: 每两周一次或重大变更时
