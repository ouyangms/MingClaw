# Database Layer (core:database) 实现计划

## 概述
实现 Room 数据库层 `core:database`，提供 MingClaw 的本地持久化存储，包括会话、消息、工作空间、记忆和向量嵌入的实体与 DAO。

## MVP 范围
- MingClawDatabase: Room 数据库（version 2，5 个实体，5 个 DAO）
- SessionEntity / SessionDao: 会话管理
- MessageEntity / MessageDao: 消息存储（外键关联 Session）
- WorkspaceEntity / WorkspaceDao: 工作空间管理
- MemoryEntity / MemoryDao: 记忆存储（含全文搜索、统计查询）
- EmbeddingEntity / EmbeddingDao: 向量嵌入存储
- Converters: Room TypeConverters（Map<String,String>、List<String> 的 JSON 序列化）
- DatabaseModule: Hilt DI 提供数据库实例和所有 DAO

## 延迟实现
- sqlite-vec 向量搜索扩展
- 数据库迁移策略（当前使用 fallbackToDestructiveMigration）
- Schema 导出（exportSchema = false）
- 批量事务操作

---

## Task T1: 更新版本目录 + 模块注册

在 `gradle/libs.versions.toml` 添加 Room 依赖：
- `room = "2.6.1"` → room-runtime, room-ktx, room-compiler, room-testing
- 在 `settings.gradle.kts` 添加 `include(":core:database")`

---

## Task T2: Room 实体定义

在 `core/database/src/main/java/.../entity/` 下创建：

### SessionEntity
- 字段: id (PK), title, createdAt, updatedAt, metadata (JSON), status
- 索引: updated_at

### MessageEntity
- 字段: id (PK), sessionId (FK → Session), role, content, timestamp, toolCalls (JSON), editedAt
- 外键: SessionEntity.CASCADE
- 索引: session_id, timestamp

### WorkspaceEntity
- 字段: id (PK), name, path, createdAt, modifiedAt, isActive, description, tags (JSON array), version, templateId

### MemoryEntity
- 字段: id (PK), content, type, importance, metadata (JSON), createdAt, accessedAt, accessCount, updatedAt
- 索引: type, importance, created_at

### EmbeddingEntity
- 字段: id (PK), embedding (JSON array of floats), dimension

---

## Task T3: DAO 接口

在 `core/database/src/main/java/.../dao/` 下创建：

### SessionDao
- insert, update, delete, getById, getAll, getActiveSessions, observeAll, observeById

### MessageDao
- insert, update, delete, deleteBySessionId, getById, getAllBySessionId, getBySessionId(limit), observeBySessionId

### WorkspaceDao
- insert, update, delete, getById, getAll, getActive, deactivateAll

### MemoryDao
- insert, insertAll, update, delete, getById, getAll, getByType, observeAll, search (LIKE), count, countByType, getAverageImportance, deleteBefore

### EmbeddingDao
- insert, insertAll, delete, getById

---

## Task T4: TypeConverters + MingClawDatabase + DI

### Converters
- fromStringMap / toStringMap: Map<String,String> ↔ JSON
- fromStringList / toStringList: List<String> ↔ JSON

### MingClawDatabase
- @Database(version=2, 5 entities, exportSchema=false)
- @TypeConverters(Converters::class)
- 5 个抽象 DAO 方法

### DatabaseModule
- provideDatabase: Room.databaseBuilder + fallbackToDestructiveMigration
- provideSessionDao, provideMessageDao, provideWorkspaceDao, provideMemoryDao, provideEmbeddingDao

---

## Task T5: 测试验证

### ConvertersTest
- stringMap_roundTrip, stringList_roundTrip, emptyMap_roundTrip

### 构建验证
- `./gradlew :core:database:test` 通过
- `./gradlew assembleDebug` 通过
