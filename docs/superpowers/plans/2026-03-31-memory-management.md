# Memory Management (core:memory) 实现计划

## 概述
实现记忆管理模块 `core:memory`，提供记忆的持久化存储、向量嵌入生成和基于文本的搜索功能。作为 MVP，使用 Room 存储记忆和嵌入，通过 LlmProvider 生成嵌入向量，使用余弦相似度计算。

## 设计决策
- core:memory 依赖 core:model 的 LlmProvider（而非直接依赖 core:network），保持模型层抽象
- 嵌入向量以 JSON 字符串存储在 Room 中（MVP），未来迁移到 sqlite-vec
- MVP 使用文本搜索（LIKE），未来实现向量相似度搜索
- EmbeddingService 使用 DashScope 的 `text-embedding-v4` 模型

## MVP 范围
- MemoryManager: 记忆的增删查改、搜索、统计、清理
- EmbeddingService: 嵌入向量生成 + 余弦相似度计算
- MemoryStorage: 底层存储抽象（Room DAO）
- Hilt DI 绑定

## 延迟实现
- 向量相似度搜索（HybridSearch + RRF）
- 记忆整合与合并（MemoryIntegration）
- 记忆压缩（LLM 摘要）
- 嵌入缓存（EmbeddingCache）
- sqlite-vec 向量索引

---

## Task T1: 领域类型扩展 (core:model)

在 `core/model/src/main/java/.../memory/` 下添加：

### MemoryTypes.kt
- Memory: id, content, type (MemoryType), importance, metadata, embedding, createdAt, accessedAt, accessCount, updatedAt
- MemoryType enum: ShortTerm, LongTerm, Episodic, Semantic, Procedural
- MemoryStatistics: totalMemories, memoriesByType, averageImportance, totalTokens

---

## Task T2: 数据库扩展 (core:database)

### MemoryEntity (entity/MemoryEntity.kt)
- 表名: memories
- 字段: id (PK), content, type, importance, metadata (JSON), createdAt, accessedAt, accessCount, updatedAt
- 索引: type, importance, created_at

### EmbeddingEntity (entity/EmbeddingEntity.kt)
- 表名: embeddings
- 字段: id (PK), embedding (JSON float array), dimension

### MemoryDao (dao/MemoryDao.kt)
- CRUD: insert, insertAll, update, delete, getById, getAll, getByType
- 查询: search (LIKE), count, countByType, getAverageImportance, deleteBefore
- 响应式: observeAll → Flow<List<MemoryEntity>>

### EmbeddingDao (dao/EmbeddingDao.kt)
- insert, insertAll, delete, getById

### MingClawDatabase 升级
- 版本 1 → 2，添加 MemoryEntity, EmbeddingEntity
- 添加 memoryDao(), embeddingDao() 抽象方法

### DatabaseModule 扩展
- provideMemoryDao, provideEmbeddingDao

---

## Task T3: 模块基础设施

### core/memory/build.gradle.kts
- 依赖: :core:model, :core:common, :core:database
- 不直接依赖 :core:network（通过 LlmProvider 抽象）
- 插件: mingclaw.android.library, mingclaw.hilt, kotlin.serialization

### settings.gradle.kts
- 添加 `include(":core:memory")`

---

## Task T4: 公共接口定义

### MemoryManager (MemoryManager.kt)
- addMemory(content, type, importance): Result<Memory>
- getMemory(id): Result<Memory>
- deleteMemory(id): Result<Unit>
- searchMemories(query, limit): Result<List<Memory>>
- getMemoriesByType(type): Result<List<Memory>>
- observeAllMemories(): Flow<List<Memory>>
- getStatistics(): MemoryStatistics
- cleanup(beforeTimestamp): Result<Int>

### EmbeddingService (EmbeddingService.kt)
- generateEmbedding(text): Result<List<Float>>
- generateEmbeddings(texts): Result<List<List<Float>>>
- similarity(a, b): Float

### MemoryStorage (MemoryStorage.kt)
- add, get, update, delete, search, getAll, observeAll, getStatistics, cleanup

---

## Task T5: 内部实现

### EmbeddingServiceImpl (internal/EmbeddingServiceImpl.kt)
- 注入: LlmProvider
- generateEmbedding: 委托 llmProvider.embed(model="text-embedding-v4")
- generateEmbeddings: 批量委托
- similarity: 余弦相似度（dot product / sqrt(normA) * sqrt(normB)）

### MemoryStorageImpl (internal/MemoryStorageImpl.kt)
- 注入: MemoryDao, EmbeddingDao, @IODispatcher
- add: 插入 MemoryEntity + EmbeddingEntity（如 embedding 非空）
- get: 查询 MemoryEntity + EmbeddingEntity，合并为领域类型
- update: 更新 MemoryEntity + upsert EmbeddingEntity
- search: 委托 MemoryDao.search (LIKE 查询)
- getStatistics: 聚合 count/countByType/getAverageImportance
- 内部方法: toEntity() / toDomain() 进行 Memory ↔ MemoryEntity 转换

### MemoryManagerImpl (internal/MemoryManagerImpl.kt)
- 注入: MemoryStorage, EmbeddingService, @IODispatcher
- addMemory: 生成 UUID → 生成 embedding → 构建 Memory → storage.add
- 其他方法委托 storage

---

## Task T6: DI 模块

### MemoryModule (di/MemoryModule.kt)
- @Binds EmbeddingServiceImpl → EmbeddingService
- @Binds MemoryStorageImpl → MemoryStorage
- @Binds MemoryManagerImpl → MemoryManager
- 全部 @Singleton

---

## Task T7: 测试验证

### EmbeddingServiceImplTest
- generateEmbedding_success, generateEmbeddings_multipleTexts
- similarity_identicalVectors, similarity_orthogonalVectors, similarity_emptyVectors

### MemoryManagerImplTest
- addMemory_returnsMemoryWithEmbedding, similarity_cosineCalculation, similarity_differentVectors

### 构建验证
- `./gradlew :core:memory:test` 通过
- `./gradlew assembleDebug` 通过
