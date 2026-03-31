# MingClaw 记忆管理设计

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [记忆管理概述](#记忆管理概述)
2. [记忆存储](#记忆存储)
3. [向量嵌入](#向量嵌入)
4. [混合搜索](#混合搜索)
5. [记忆整合](#记忆整合)
6. [依赖关系](#依赖关系)
7. [附录](#附录)

---

## 记忆管理概述

### 设计目标

MingClaw 记忆管理系统实现：

| 目标 | 说明 | 实现方式 |
|------|------|----------|
| **持久化** | 长期保存重要信息 | 分层存储策略 |
| **语义检索** | 基于语义相似度搜索 | 向量嵌入 + ANN |
| **混合查询** | 支持多种查询方式 | 向量 + 全文 + 元数据 |
| **自动整合** | 提炼和合并知识 | AI 驱动的摘要 |
| **可扩展** | 支持大规模记忆 | 高效索引结构 |

### 架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Memory Management Layer                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                      Memory Orchestrator                          │  │
│  │  - 协调记忆操作                                                    │  │
│  │  - 管理记忆生命周期                                                │  │
│  │  - 优化存储策略                                                    │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌───────────────┬─────────────────┼─────────────────┬─────────────────┐ │
│  │               │                 │                 │                 │ │
│  ▼               ▼                 ▼                 ▼                 │ │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ │
│  │  Memory   │ │ Embedding │ │   Hybrid  │ │  Memory   │ │  Vector   │ │
│  │  Storage  │ │  Service  │ │  Search   │ │Compressor │ │   Index   │ │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Storage Layer                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                │
│  │  Room DB     │ │  File System │ │  Vector DB    │                │
│  │ (Memories)   │ │(Embeddings)  │ │ (sqlite-vec)  │                │
│  └──────────────┘  └──────────────┘  └──────────────┘                │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 记忆存储

### 核心接口

```kotlin
/**
 * 记忆存储接口
 *
 * 职责：
 * - 管理记忆的增删改查
 * - 支持批量操作
 * - 提供事务支持
 */
interface MemoryStorage {

    /**
     * 添加记忆
     */
    suspend fun add(memory: Memory): Result<Memory>

    /**
     * 批量添加记忆
     */
    suspend fun addAll(memories: List<Memory>): Result<List<Memory>>

    /**
     * 获取记忆
     */
    suspend fun get(memoryId: String): Result<Memory>

    /**
     * 更新记忆
     */
    suspend fun update(memory: Memory): Result<Memory>

    /**
     * 删除记忆
     */
    suspend fun delete(memoryId: String): Result<Unit>

    /**
     * 批量删除记忆
     */
    suspend fun deleteAll(memoryIds: List<String>): Result<Unit>

    /**
     * 搜索记忆
     */
    suspend fun search(
        query: String,
        filters: Map<String, Any>,
        limit: Int
    ): Result<List<Memory>>

    /**
     * 获取所有记忆
     */
    suspend fun getAll(
        type: MemoryType? = null,
        limit: Int? = null
    ): Result<List<Memory>>

    /**
     * 获取记忆统计
     */
    suspend fun getStatistics(): MemoryStatistics

    /**
     * 清理过期记忆
     */
    suspend fun cleanup(before: Instant): Result<Int>

    /**
     * 导出记忆
     */
    suspend fun export(format: ExportFormat): Result<ByteArray>

    /**
     * 导入记忆
     */
    suspend fun import(data: ByteArray, format: ExportFormat): Result<Int>
}
```

### 实现类

```kotlin
/**
 * 记忆存储实现
 */
@Singleton
internal class MemoryStorageImpl @Inject constructor(
    private val memoryDao: MemoryDao,
    private val embeddingDao: EmbeddingDao,
    private val vectorStore: VectorStore,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : MemoryStorage {

    override suspend fun add(memory: Memory): Result<Memory> {
        return withContext(ioDispatcher) {
            try {
                // 1. 保存记忆到数据库
                memoryDao.insert(memory.toEntity())

                // 2. 保存嵌入向量
                embeddingDao.insert(
                    EmbeddingEntity(
                        id = memory.id,
                        embedding = memory.embedding,
                        dimension = memory.embedding.size
                    )
                )

                // 3. 添加到向量索引
                vectorStore.add(
                    id = memory.id,
                    embedding = memory.embedding,
                    metadata = memory.metadata + (
                        "type" to memory.type.name,
                        "created_at" to memory.createdAt.toString()
                    )
                )

                Result.success(memory)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun addAll(memories: List<Memory>): Result<List<Memory>> {
        return withContext(ioDispatcher) {
            try {
                database.withTransaction {
                    // 批量插入记忆
                    memoryDao.insertAll(memories.map { it.toEntity() })

                    // 批量插入嵌入
                    embeddingDao.insertAll(
                        memories.map { memory ->
                            EmbeddingEntity(
                                id = memory.id,
                                embedding = memory.embedding,
                                dimension = memory.embedding.size
                            )
                        }
                    )

                    // 批量添加到向量索引
                    vectorStore.addAll(
                        ids = memories.map { it.id },
                        embeddings = memories.map { it.embedding },
                        metadata = memories.map { memory ->
                            memory.metadata + (
                                "type" to memory.type.name,
                                "created_at" to memory.createdAt.toString()
                            )
                        }
                    )
                }

                Result.success(memories)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun get(memoryId: String): Result<Memory> {
        return withContext(ioDispatcher) {
            try {
                val entity = memoryDao.getById(memoryId)
                    ?: return@withContext Result.failure(
                        MemoryNotFoundException(memoryId)
                    )

                val embeddingEntity = embeddingDao.getById(memoryId)
                    ?: return@withContext Result.failure(
                        EmbeddingNotFoundException(memoryId)
                    )

                val memory = entity.toDomain(
                    embedding = embeddingEntity.embedding
                )

                Result.success(memory)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun update(memory: Memory): Result<Memory> {
        return withContext(ioDispatcher) {
            try {
                // 更新数据库
                memoryDao.update(memory.toEntity())

                // 更新嵌入
                embeddingDao.update(
                    EmbeddingEntity(
                        id = memory.id,
                        embedding = memory.embedding,
                        dimension = memory.embedding.size
                    )
                )

                // 更新向量索引
                vectorStore.update(
                    id = memory.id,
                    embedding = memory.embedding,
                    metadata = memory.metadata
                )

                Result.success(memory)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun delete(memoryId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                memoryDao.delete(memoryId)
                embeddingDao.delete(memoryId)
                vectorStore.delete(memoryId)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteAll(memoryIds: List<String>): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                database.withTransaction {
                    memoryDao.deleteAll(memoryIds)
                    embeddingDao.deleteAll(memoryIds)
                    vectorStore.deleteAll(memoryIds)
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun search(
        query: String,
        filters: Map<String, Any>,
        limit: Int
    ): Result<List<Memory>> = withContext(ioDispatcher) {
        try {
            // 构建查询
            val sqlQuery = buildSearchQuery(query, filters)

            // 执行查询
            val results = memoryDao.search(
                query = "%$query%",
                type = filters["type"] as? String,
                limit = limit
            )

            Result.success(results.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAll(
        type: MemoryType?,
        limit: Int?
    ): Result<List<Memory>> = withContext(ioDispatcher) {
        try {
            val results = when {
                type != null && limit != null -> {
                    memoryDao.getByType(type.name, limit)
                }
                type != null -> {
                    memoryDao.getByType(type.name)
                }
                limit != null -> {
                    memoryDao.getAll(limit)
                }
                else -> {
                    memoryDao.getAll()
                }
            }

            Result.success(results.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStatistics(): MemoryStatistics {
        return withContext(ioDispatcher) {
            MemoryStatistics(
                totalMemories = memoryDao.count(),
                memoriesByType = MemoryType.entries.associateWith { type ->
                    memoryDao.countByType(type.name)
                },
                averageImportance = memoryDao.getAverageImportance(),
                totalTokens = memoryDao.getTotalTokens(),
                oldestMemory = memoryDao.getOldest()?.toDomain(),
                newestMemory = memoryDao.getNewest()?.toDomain()
            )
        }
    }

    override suspend fun cleanup(before: Instant): Result<Int> {
        return withContext(ioDispatcher) {
            try {
                val count = memoryDao.deleteBefore(before.toEpochMilliseconds())
                Result.success(count)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun export(format: ExportFormat): Result<ByteArray> {
        return withContext(ioDispatcher) {
            try {
                val memories = memoryDao.getAll().map { it.toDomain() }

                val data = when (format) {
                    ExportFormat.JSON -> Json.encodeToString(memories).toByteArray()
                    ExportFormat.CSV -> exportToCsv(memories)
                    ExportFormat.Markdown -> exportToMarkdown(memories)
                }

                Result.success(data)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun import(
        data: ByteArray,
        format: ExportFormat
    ): Result<Int> = withContext(ioDispatcher) {
        try {
            val memories = when (format) {
                ExportFormat.JSON -> {
                    Json.decodeFromString<List<Memory>>(data.decodeToString())
                }
                else -> return@withContext Result.failure(
                    UnsupportedOperationException("Format not supported: $format")
                )
            }

            addAll(memories)
            Result.success(memories.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 私有辅助方法
    private fun buildSearchQuery(
        query: String,
        filters: Map<String, Any>
    ): String {
        // 构建 SQL 查询
        return """
            SELECT * FROM memories
            WHERE content LIKE :query
            ${if (filters.containsKey("type")) "AND type = :type" else ""}
            ORDER BY importance DESC, created_at DESC
            LIMIT :limit
        """.trimIndent()
    }

    private fun exportToCsv(memories: List<Memory>): ByteArray {
        val csv = buildString {
            appendLine("id,type,content,importance,created_at")
            memories.forEach { memory ->
                appendLine(
                    listOf(
                        memory.id,
                        memory.type.name,
                        memory.content.replace("\"", "\"\""),
                        memory.importance,
                        memory.createdAt.toString()
                    ).joinToString(",") { "\"$it\"" }
                )
            }
        }
        return csv.toByteArray()
    }

    private fun exportToMarkdown(memories: List<Memory>): ByteArray {
        val markdown = buildString {
            appendLine("# Memory Export")
            appendLine()
            memories.forEach { memory ->
                appendLine("## ${memory.type.name}: ${memory.id}")
                appendLine()
                appendLine(memory.content)
                appendLine()
                appendLine("*Importance: ${memory.importance}*")
                appendLine("*Created: ${memory.createdAt}*")
                appendLine()
            }
        }
        return markdown.toByteArray()
    }
}
```

---

## 向量嵌入

### 核心接口

```kotlin
/**
 * 嵌入服务接口
 *
 * 职责：
 * - 生成文本嵌入向量
 * - 管理嵌入缓存
 * - 批量处理嵌入
 */
interface EmbeddingService {

    /**
     * 生成单个文本的嵌入
     */
    suspend fun generateEmbedding(text: String): FloatArray

    /**
     * 批量生成嵌入
     */
    suspend fun generateEmbeddings(texts: List<String>): List<FloatArray>

    /**
     * 计算相似度
     */
    fun similarity(embedding1: FloatArray, embedding2: FloatArray): Float

    /**
     * 计算距离
     */
    fun distance(embedding1: FloatArray, embedding2: FloatArray): Float

    /**
     * 清空缓存
     */
    suspend fun clearCache()
}
```

### 实现类

```kotlin
/**
 * 嵌入服务实现
 */
@Singleton
internal class EmbeddingServiceImpl @Inject constructor(
    private val llmService: LlmService,
    private val embeddingCache: EmbeddingCache,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : EmbeddingService {

    companion object {
        const val EMBEDDING_DIMENSION = 1536 // OpenAI ada-002 dimension
    }

    override suspend fun generateEmbedding(text: String): FloatArray {
        // 检查缓存
        embeddingCache.get(text)?.let { return it }

        // 生成嵌入
        val embedding = withContext(ioDispatcher) {
            llmService.generateEmbedding(text)
        }

        // 验证维度
        require(embedding.size == EMBEDDING_DIMENSION) {
            "Invalid embedding dimension: ${embedding.size}, expected: $EMBEDDING_DIMENSION"
        }

        // 缓存结果
        embeddingCache.put(text, embedding)

        return embedding
    }

    override suspend fun generateEmbeddings(
        texts: List<String>
    ): List<FloatArray> = withContext(ioDispatcher) {
        // 检查缓存
        val cached = mutableMapOf<Int, FloatArray>()
        val toGenerate = mutableListOf<Pair<Int, String>>()

        texts.forEachIndexed { index, text ->
            embeddingCache.get(text)?.let { embedding ->
                cached[index] = embedding
            } ?: run {
                toGenerate.add(index to text)
            }
        }

        // 批量生成
        val generated = if (toGenerate.isNotEmpty()) {
            llmService.generateEmbeddings(toGenerate.map { it.second })
        } else {
            emptyList()
        }

        // 缓存新结果
        toGenerate.forEachIndexed { i, (index, text) ->
            if (i < generated.size) {
                val embedding = generated[i]
                embeddingCache.put(text, embedding)
                cached[index] = embedding
            }
        }

        // 按原始顺序返回
        texts.indices.map { index ->
            cached[index] ?: generateEmbedding(texts[index])
        }
    }

    override fun similarity(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {
        require(embedding1.size == embedding2.size) {
            "Embedding dimensions must match"
        }

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        return (dotProduct / (sqrt(norm1) * sqrt(norm2))).toFloat()
    }

    override fun distance(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {
        // 欧几里得距离
        var sum = 0.0
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sum += diff * diff
        }
        return sqrt(sum).toFloat()
    }

    override suspend fun clearCache() {
        embeddingCache.clear()
    }
}

/**
 * 嵌入缓存
 */
@Singleton
internal class EmbeddingCache @Inject constructor(
    private val dataStore: DataStore<EmbeddingCacheEntry>
) {

    private val cache = mutableMapOf<String, FloatArray>()
    private val maxSize = 10000

    suspend fun get(text: String): FloatArray? {
        val key = hashKey(text)
        return cache[key]
    }

    suspend fun put(text: String, embedding: FloatArray) {
        val key = hashKey(text)

        // 限制缓存大小
        if (cache.size >= maxSize) {
            evictLRU()
        }

        cache[key] = embedding

        // 异步持久化
        CoroutineScope(Dispatchers.IO).launch {
            dataStore.updateData {
                it.toBuilder()
                    .putEntries(
                        key to EmbeddingCacheEntry.newBuilder()
                            .setKey(key)
                            .setEmbedding(ByteString.copyFrom(
                                embedding.flatMap {
                                    listOf(
                                        (it.bits() shr 24).toByte(),
                                        (it.bits() shr 16).toByte(),
                                        (it.bits() shr 8).toByte(),
                                        it.bits().toByte()
                                    )
                                }.toByteArray()
                            ))
                            .setTimestamp(System.currentTimeMillis())
                            .build()
                    )
                    .build()
            }
        }
    }

    suspend fun clear() {
        cache.clear()
        dataStore.updateData { it.toBuilder().clearEntries().build() }
    }

    private fun hashKey(text: String): String {
        return text.sha256()
    }

    private fun evictLRU() {
        // 移除最旧的条目
        if (cache.isNotEmpty()) {
            cache.remove(cache.keys.first())
        }
    }
}
```

---

## 混合搜索

### 核心接口

```kotlin
/**
 * 混合搜索接口
 *
 * 结合向量搜索、全文搜索和元数据过滤
 */
interface HybridSearch {

    /**
     * 搜索记忆
     *
     * @param query 搜索查询
     * @param options 搜索选项
     * @return 搜索结果
     */
    suspend fun search(
        query: String,
        options: SearchOptions
    ): SearchResult

    /**
     * 相似记忆搜索
     */
    suspend fun findSimilar(
        memoryId: String,
        limit: Int = 10
    ): Result<List<Memory>>

    /**
     * 语义搜索
     */
    suspend fun semanticSearch(
        query: String,
        filters: Map<String, Any>,
        limit: Int
    ): Result<List<Memory>>

    /**
     * 全文搜索
     */
    suspend fun fullTextSearch(
        query: String,
        filters: Map<String, Any>,
        limit: Int
    ): Result<List<Memory>>

    /**
     * 混合搜索（推荐）
     */
    suspend fun hybridSearch(
        query: String,
        filters: Map<String, Any>,
        limit: Int
    ): Result<List<Memory>>
}
```

### 实现类

```kotlin
/**
 * 混合搜索实现
 */
@Singleton
internal class HybridSearchImpl @Inject constructor(
    private val memoryStorage: MemoryStorage,
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
    private val tokenEstimator: TokenEstimator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : HybridSearch {

    override suspend fun search(
        query: String,
        options: SearchOptions
    ): SearchResult = withContext(ioDispatcher) {
        val startTime = Clock.System.now()

        // 根据选项选择搜索策略
        val results = when {
            options.hybrid && options.semantic && options.fullText -> {
                performHybridSearch(query, options)
            }
            options.semantic -> {
                performSemanticSearch(query, options)
            }
            options.fullText -> {
                performFullTextSearch(query, options)
            }
            else -> {
                performHybridSearch(query, options)
            }
        }

        val duration = Clock.System.now() - startTime

        SearchResult(
            query = query,
            results = results,
            totalHits = results.size,
            duration = duration,
            metadata = mapOf(
                "strategy" to when {
                    options.hybrid && options.semantic && options.fullText -> "hybrid"
                    options.semantic -> "semantic"
                    options.fullText -> "fulltext"
                    else -> "hybrid"
                }
            )
        )
    }

    override suspend fun findSimilar(
        memoryId: String,
        limit: Int
    ): Result<List<Memory>> = withContext(ioDispatcher) {
        try {
            // 获取源记忆
            val source = memoryStorage.get(memoryId).getOrThrow()

            // 使用源记忆的嵌入进行搜索
            val similar = vectorStore.search(
                embedding = source.embedding,
                limit = limit + 1, // +1 因为会包含自己
                filter = mapOf("type" to source.type.name)
            )

            // 移除自己
            val results = similar
                .filter { it != memoryId }
                .take(limit)
                .mapNotNull { memoryStorage.get(it).getOrNull() }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun semanticSearch(
        query: String,
        filters: Map<String, Any>,
        limit: Int
    ): Result<List<Memory>> = withContext(ioDispatcher) {
        try {
            // 生成查询嵌入
            val queryEmbedding = embeddingService.generateEmbedding(query)

            // 向量搜索
            val memoryIds = vectorStore.search(
                embedding = queryEmbedding,
                limit = limit,
                filter = filters
            )

            // 获取完整记忆
            val results = memoryIds.mapNotNull { id ->
                memoryStorage.get(id).getOrNull()
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun fullTextSearch(
        query: String,
        filters: Map<String, Any>,
        limit: Int
    ): Result<List<Memory>> {
        return memoryStorage.search(query, filters, limit)
    }

    override suspend fun hybridSearch(
        query: String,
        filters: Map<String, Any>,
        limit: Int
    ): Result<List<Memory>> = withContext(ioDispatcher) {
        try {
            // 并行执行两种搜索
            val deferredSemantic = async { semanticSearch(query, filters, limit * 2) }
            val deferredFullText = async { fullTextSearch(query, filters, limit * 2) }

            val semanticResults = deferredSemantic.await().getOrNull()
            val fullTextResults = deferredFullText.await().getOrNull()

            // 融合结果
            val fused = fuseResults(
                semanticResults = semanticResults.orEmpty(),
                fullTextResults = fullTextResults.orEmpty(),
                query = query,
                limit = limit
            )

            Result.success(fused)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 私有辅助方法
    private suspend fun performHybridSearch(
        query: String,
        options: SearchOptions
    ): List<ScoredMemory> {
        // 并行执行
        val semanticJob = async(ioDispatcher) {
            performSemanticSearch(query, options)
        }
        val fullTextJob = async(ioDispatcher) {
            performFullTextSearch(query, options)
        }

        val semanticResults = semanticJob.await()
        val fullTextResults = fullTextJob.await()

        // 融合
        return fuseResults(
            semanticResults = semanticResults,
            fullTextResults = fullTextResults,
            query = query,
            limit = options.limit
        )
    }

    private suspend fun performSemanticSearch(
        query: String,
        options: SearchOptions
    ): List<ScoredMemory> = withContext(ioDispatcher) {
        val queryEmbedding = embeddingService.generateEmbedding(query)

        val memoryIds = vectorStore.search(
            embedding = queryEmbedding,
            limit = options.limit * 2,
            filter = options.filters
        )

        memoryIds.mapNotNull { id ->
            memoryStorage.get(id).getOrNull()?.let { memory ->
                val similarity = embeddingService.similarity(queryEmbedding, memory.embedding)
                ScoredMemory(memory, similarity)
            }
        }
    }

    private suspend fun performFullTextSearch(
        query: String,
        options: SearchOptions
    ): List<ScoredMemory> = withContext(ioDispatcher) {
        val results = memoryStorage.search(
            query = query,
            filters = options.filters,
            limit = options.limit * 2
        ).getOrNull().orEmpty()

        results.map { memory ->
            val score = calculateRelevanceScore(query, memory.content)
            ScoredMemory(memory, score)
        }
    }

    private fun fuseResults(
        semanticResults: List<ScoredMemory>,
        fullTextResults: List<ScoredMemory>,
        query: String,
        limit: Int
    ): List<Memory> {
        // 使用 Reciprocal Rank Fusion (RRF)
        val scores = mutableMapOf<String, Double>()

        // 语义得分
        semanticResults.forEachIndexed { index, scored ->
            val rrfScore = 1.0 / (60 + index + 1)
            scores[scored.memory.id] =
                scores.getOrDefault(scored.memory.id, 0.0) + rrfScore * 0.6
        }

        // 全文得分
        fullTextResults.forEachIndexed { index, scored ->
            val rrfScore = 1.0 / (60 + index + 1)
            scores[scored.memory.id] =
                scores.getOrDefault(scored.memory.id, 0.0) + rrfScore * 0.4
        }

        // 排序并返回
        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { (id, _) ->
                semanticResults.find { it.memory.id == id }?.memory
                    ?: fullTextResults.find { it.memory.id == id }?.memory
            }
    }

    private fun calculateRelevanceScore(query: String, content: String): Float {
        val queryTerms = query.lowercase().split(Whitespace)
        val contentLower = content.lowercase()

        var score = 0f
        for (term in queryTerms) {
            when {
                contentLower.contains(term) -> {
                    // 精确匹配
                    score += 1f
                }
                contentLower.any { it.isLetter() && term.any { c -> c.equals(it, ignoreCase = true) } } -> {
                    // 部分匹配
                    score += 0.5f
                }
            }
        }

        // 考虑内容长度
        return score / (1 + content.length / 1000f)
    }
}

/**
 * 搜索选项
 */
@Serializable
data class SearchOptions(
    val limit: Int = 20,
    val offset: Int = 0,
    val semantic: Boolean = true,
    val fullText: Boolean = true,
    val hybrid: Boolean = true,
    val filters: Map<String, Any> = emptyMap(),
    val minScore: Float = 0.0f
)

/**
 * 搜索结果
 */
@Serializable
data class SearchResult(
    val query: String,
    val results: List<Memory>,
    val totalHits: Int,
    val duration: Duration,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 带分的记忆
 */
data class ScoredMemory(
    val memory: Memory,
    val score: Float
)
```

---

## 记忆整合

### 核心接口

```kotlin
/**
 * 记忆整合接口
 *
 * 职责：
 * - 提取关键信息
 * - 合并相似记忆
 * - 生成摘要
 * - 更新重要性
 */
interface MemoryIntegration {

    /**
     * 整合新记忆
     */
    suspend fun integrate(memory: Memory): Result<Memory>

    /**
     * 批量整合记忆
     */
    suspend fun integrateAll(memories: List<Memory>): Result<Int>

    /**
     * 合并相似记忆
     */
    suspend fun mergeSimilar(
        threshold: Float = 0.9f
    ): Result<Int>

    /**
     * 更新记忆重要性
     */
    suspend fun updateImportance(
        memoryId: String,
        delta: Float
    ): Result<Unit>

    /**
     * 压缩记忆
     */
    suspend fun compress(
        memoryIds: List<String>
    ): Result<Memory>

    /**
     * 提取知识点
     */
    suspend fun extractKnowledge(
        text: String
    ): Result<List<KnowledgePoint>>
}
```

### 实现类

```kotlin
/**
 * 记忆整合实现
 */
@Singleton
internal class MemoryIntegrationImpl @Inject constructor(
    private val memoryStorage: MemoryStorage,
    private val embeddingService: EmbeddingService,
    private val llmService: LlmService,
    private val hybridSearch: HybridSearch,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : MemoryIntegration {

    override suspend fun integrate(memory: Memory): Result<Memory> {
        return withContext(ioDispatcher) {
            try {
                // 1. 检查是否存在相似记忆
                val similar = hybridSearch.findSimilar(
                    memoryId = memory.id,
                    limit = 5
                ).getOrNull().orEmpty()

                // 2. 如果找到高度相似的记忆，合并
                val highlySimilar = similar.filter { existing ->
                    embeddingService.similarity(memory.embedding, existing.embedding) > 0.95f
                }

                if (highlySimilar.isNotEmpty()) {
                    // 合并到现有记忆
                    val existing = highlySimilar.first()
                    val merged = mergeMemories(existing, memory)

                    memoryStorage.update(merged)
                    memoryStorage.delete(memory.id)

                    return@withContext Result.success(merged)
                }

                // 3. 否则，添加新记忆
                memoryStorage.add(memory)
                Result.success(memory)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun integrateAll(
        memories: List<Memory>
    ): Result<Int> = withContext(ioDispatcher) {
        try {
            var integrated = 0

            for (memory in memories) {
                integrate(memory)
                    .onSuccess { integrated++ }
            }

            Result.success(integrated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun mergeSimilar(threshold: Float): Result<Int> {
        return withContext(ioDispatcher) {
            try {
                // 获取所有记忆
                val allMemories = memoryStorage.getAll().getOrNull()
                    ?: return@withContext Result.failure(
                        Exception("Failed to retrieve memories")
                    )

                var mergedCount = 0
                val processed = mutableSetOf<String>()

                for (memory in allMemories) {
                    if (memory.id in processed) continue

                    // 查找相似记忆
                    val similar = hybridSearch.findSimilar(
                        memoryId = memory.id,
                        limit = 10
                    ).getOrNull().orEmpty()

                    // 找出需要合并的记忆
                    val toMerge = similar.filter { other ->
                        other.id != memory.id &&
                        other.id !in processed &&
                        embeddingService.similarity(memory.embedding, other.embedding) > threshold
                    }

                    if (toMerge.isNotEmpty()) {
                        // 合并记忆
                        val merged = mergeMemories(memory, *toMerge.toTypedArray())
                        memoryStorage.update(merged)

                        // 删除被合并的记忆
                        memoryStorage.deleteAll(toMerge.map { it.id })

                        processed.add(memory.id)
                        processed.addAll(toMerge.map { it.id })
                        mergedCount += toMerge.size
                    }
                }

                Result.success(mergedCount)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateImportance(
        memoryId: String,
        delta: Float
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            val memory = memoryStorage.get(memoryId).getOrThrow()

            val updated = memory.copy(
                importance = (memory.importance + delta).coerceIn(0f, 1f)
            )

            memoryStorage.update(updated)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun compress(
        memoryIds: List<String>
    ): Result<Memory> = withContext(ioDispatcher) {
        try {
            // 获取所有要压缩的记忆
            val memories = memoryIds.mapNotNull { id ->
                memoryStorage.get(id).getOrNull()
            }

            if (memories.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("No memories to compress")
                )
            }

            // 使用 LLM 生成摘要
            val prompt = buildString {
                appendLine("Please provide a concise summary of the following information:")
                appendLine()
                memories.forEach { memory ->
                    appendLine(memory.content)
                    appendLine()
                }
            }

            val response = llmService.generate(
                prompt = prompt,
                maxTokens = 1000
            )

            // 创建压缩后的记忆
            val compressedMemory = Memory(
                id = UUID.randomUUID().toString(),
                content = response.text.trim(),
                type = memories.first().type,
                embedding = embeddingService.generateEmbedding(response.text),
                metadata = memories.flatMap { it.metadata.entries }
                    .groupBy { it.key }
                    .mapValues { entry ->
                        if (entry.value.size == 1) {
                            entry.value.first().value
                        } else {
                            entry.value.map { it.value }.distinct().joinToString(", ")
                        }
                    },
                importance = memories.map { it.importance }.average().toFloat(),
                createdAt = Clock.System.now(),
                accessedAt = Clock.System.now(),
                accessCount = 0
            )

            // 保存压缩记忆
            memoryStorage.add(compressedMemory)

            // 删除原始记忆
            memoryStorage.deleteAll(memoryIds)

            Result.success(compressedMemory)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun extractKnowledge(
        text: String
    ): Result<List<KnowledgePoint>> = withContext(ioDispatcher) {
        try {
            val prompt = buildString {
                appendLine("Extract key knowledge points from the following text:")
                appendLine()
                appendLine(text)
                appendLine()
                appendLine("Knowledge points (one per line):")
            }

            val response = llmService.generate(
                prompt = prompt,
                maxTokens = 2000
            )

            val points = response.text.lines()
                .filter { it.isNotBlank() }
                .mapIndexed { index, line ->
                    KnowledgePoint(
                        id = UUID.randomUUID().toString(),
                        content = line.trim(),
                        source = text,
                        confidence = 1.0f,
                        extractedAt = Clock.System.now()
                    )
                }

            Result.success(points)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 私有辅助方法
    private fun mergeMemories(
        base: Memory,
        others: Array<out Memory>
    ): Memory {
        val all = listOf(base) + others

        // 合并内容
        val mergedContent = buildString {
            appendLine(base.content)
            if (others.isNotEmpty()) {
                appendLine()
                appendLine("(Merged ${others.size} related memories)")
            }
        }

        // 计算平均嵌入
        val avgEmbedding = if (all.size == 1) {
            base.embedding
        } else {
            val dim = base.embedding.size
            FloatArray(dim) { i ->
                all.map { it.embedding[i] }.average().toFloat()
            }
        }

        // 合并元数据
        val mergedMetadata = all.flatMap { it.metadata.entries }
            .groupBy { it.key }
            .mapValues { entry ->
                val values = entry.value.map { it.value }.distinct()
                if (values.size == 1) values.first() else values.joinToString(", ")
            }

        return base.copy(
            content = mergedContent,
            embedding = avgEmbedding,
            metadata = mergedMetadata,
            importance = all.map { it.importance }.average().toFloat(),
            accessCount = all.sumOf { it.accessCount }
        )
    }
}

/**
 * 知识点
 */
@Serializable
data class KnowledgePoint(
    val id: String,
    val content: String,
    val source: String,
    val confidence: Float,
    val extractedAt: Instant
)
```

---

## 依赖关系

### 模块依赖图

```
┌─────────────────────────────────────────────────────────────────┐
│                      Memory Orchestrator                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Memory     │  │  Embedding   │  │   Hybrid     │         │
│  │   Storage    │  │   Service    │  │   Search     │         │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
│         │                  │                  │                  │
└─────────┼──────────────────┼──────────────────┼──────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Shared Dependencies                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │     LLM      │  │   Vector     │  │    Token     │         │
│  │   Service    │  │    Store     │  │  Estimator   │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                          Data Layer                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  Memory DAO  │  │ Embedding    │  │  Vector DB   │         │
│  │              │  │     DAO      │  │ (sqlite-vec) │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 附录

### A. 数据库定义

```kotlin
/**
 * 记忆实体
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val type: String,
    val importance: Float,
    val metadata: String, // JSON
    val created_at: Long,
    val accessed_at: Long,
    val access_count: Int,
    val updated_at: Long?
)

/**
 * 嵌入实体
 */
@Entity(tableName = "embeddings")
data class EmbeddingEntity(
    @PrimaryKey val id: String,
    val embedding: ByteArray,
    val dimension: Int
)
```

### B. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构设计
- [04-context-management.md](./04-context-management.md) - 上下文管理
- [05-plugin-system.md](./05-plugin-system.md) - 插件系统

---

**文档维护**: 本文档应随着记忆管理功能的实现持续更新
**审查周期**: 每两周一次或重大变更时
