# Workspace Management (core:workspace) 实现计划

## 概述
实现工作区与配置管理模块 `core:workspace`，提供工作区创建/切换/删除、文件读写、配置管理和目录扫描功能。

## MVP 范围
- 使用 `java.nio.file.Path` 和 `java.nio.file.Files` 进行文件操作（minSdk=32 支持）
- 使用 kotlinx.serialization JSON 进行配置序列化
- 文件监听使用简化实现（可测试）
- 导出/导入和模板系统简化处理

## 延迟实现
- Android FileObserver 集成（需要 instrumentation 测试）
- SAF/DocumentsContract 集成
- 复杂的配置合并和回滚
- Uri 导入/导出

---

## Task W1: 工作区领域类型 (core:model)

在 `core/model/src/main/java/com/loy/mingclaw/core/model/workspace/` 下创建：

### Workspace.kt
```kotlin
package com.loy.mingclaw.core.model.workspace

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val path: String,
    val createdAt: Instant,
    val modifiedAt: Instant,
    val isActive: Boolean = false,
    val metadata: WorkspaceMetadata = WorkspaceMetadata(),
)

@Serializable
data class WorkspaceMetadata(
    val description: String = "",
    val tags: List<String> = emptyList(),
    val version: String = "1.0",
    val templateId: String? = null,
)
```

### WorkspaceConfig.kt
```kotlin
package com.loy.mingclaw.core.model.workspace

import kotlinx.serialization.Serializable

@Serializable
sealed class WorkspaceConfig {
    abstract val version: String
    abstract val schema: String
}

@Serializable
data class AgentsConfig(
    override val version: String = "1.0",
    override val schema: String = "agents-v1",
    val agents: Map<String, AgentConfig> = emptyMap(),
) : WorkspaceConfig()

@Serializable
data class AgentConfig(
    val id: String,
    val name: String,
    val description: String,
    val capabilities: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

@Serializable
data class CapabilitiesConfig(
    override val version: String = "1.0",
    override val schema: String = "capabilities-v1",
    val capabilities: Map<String, CapabilityConfig> = emptyMap(),
) : WorkspaceConfig()

@Serializable
data class CapabilityConfig(
    val id: String,
    val name: String,
    val description: String,
    val enabled: Boolean = true,
    val parameters: Map<String, ParameterConfig> = emptyMap(),
)

@Serializable
data class ParameterConfig(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val default: String? = null,
)

@Serializable
data class ToolsConfig(
    override val version: String = "1.0",
    override val schema: String = "tools-v1",
    val tools: Map<String, ToolConfig> = emptyMap(),
) : WorkspaceConfig()

@Serializable
data class ToolConfig(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val enabled: Boolean = true,
    val permissions: List<String> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

@Serializable
enum class ToolCategory {
    SEARCH, FILE_SYSTEM, CALCULATOR, DATETIME, NETWORK, CUSTOM,
}
```

### WorkspaceTypes.kt
```kotlin
package com.loy.mingclaw.core.model.workspace

import kotlinx.serialization.Serializable

// Validation
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
)

// Directory scanning
data class ScanOptions(
    val recursive: Boolean = true,
    val includeHidden: Boolean = false,
    val maxDepth: Int = Int.MAX_VALUE,
) {
    companion object {
        val Default = ScanOptions()
    }
}

@Serializable
data class DirectoryInfo(
    val path: String,
    val name: String,
    val fileCount: Int,
    val directoryCount: Int,
    val totalSize: Long,
    val lastModified: Long,
    val isReadable: Boolean,
    val isWritable: Boolean,
)

@Serializable
sealed class FileTreeNode {
    abstract val path: String
    abstract val name: String

    @Serializable
    data class FileNode(
        override val path: String,
        override val name: String,
        val size: Long,
        val lastModified: Long,
    ) : FileTreeNode()

    @Serializable
    data class DirectoryNode(
        override val path: String,
        override val name: String,
        val children: List<FileTreeNode> = emptyList(),
    ) : FileTreeNode()
}

// File change events
sealed class FileChangeEvent {
    data class Created(val path: String) : FileChangeEvent()
    data class Modified(val path: String) : FileChangeEvent()
    data class Deleted(val path: String) : FileChangeEvent()
    data class Moved(val from: String, val to: String) : FileChangeEvent()
}
```

### 测试
- `WorkspaceTest.kt`: Workspace 序列化、WorkspaceMetadata 默认值
- `WorkspaceConfigTest.kt`: AgentsConfig/CapabilitiesConfig/ToolsConfig 序列化

---

## Task W2: 模块基础设施

### core/workspace/build.gradle.kts
```kotlin
plugins {
    id("mingclaw.android.library")
    id("mingclaw.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.loy.mingclaw.core.workspace"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
```

### 接口文件
- `WorkspaceManager.kt` - getCurrentWorkspace, listWorkspaces, createWorkspace, switchWorkspace, deleteWorkspace
- `WorkspaceFileManager.kt` - readFile, writeFile, deleteFile, exists, createBackup
- `WorkspaceConfigManager.kt` - readConfig, writeConfig, validateConfig
- `DirectoryScanner.kt` - scanDirectory, scanByPattern, getFileTree, getDirectoryInfo
- `ConfigValidator.kt` - validate(config)

### settings.gradle.kts 添加 `include(":core:workspace")`

---

## Task W3: WorkspaceFileManager 实现

### internal/WorkspaceFileManagerImpl.kt
- 使用 `java.nio.file.Files` 进行文件操作
- `readFile`: Files.readString(path)
- `writeFile`: Files.writeString(path, content) 带原子写入
- `deleteFile`: Files.deleteIfExists(path)
- `exists`: Files.exists(path)
- `createBackup`: 复制到 .backup 文件
- `watchFile`: 使用 SharedFlow 发送文件变更事件（简化实现）
- 使用 @IODispatcher 进行协程上下文切换

### 测试
- 使用 @TempDir 创建临时目录进行测试
- 测试读写、追加、删除、备份恢复

---

## Task W4: ConfigValidator + WorkspaceConfigManager

### internal/ConfigValidatorImpl.kt
- 验证 version 非空
- 验证 schema 格式
- 验证各配置类型的必填字段

### internal/WorkspaceConfigManagerImpl.kt
- 使用 WorkspaceFileManager 进行文件 I/O
- JSON 配置解析使用 kotlinx.serialization
- 缓存已读取的配置
- 配置合并使用 JsonElement 深度合并

### 测试
- ConfigValidator 正确/错误配置验证
- WorkspaceConfigManager 读写配置
- 配置合并

---

## Task W5: DirectoryScanner 实现

### internal/DirectoryScannerImpl.kt
- 使用 `java.nio.file.Files.walkFileTree` 或 `Files.list`
- 递归扫描支持 maxDepth
- 模式匹配使用 Regex
- 文件树构建为 FileTreeNode.DirectoryNode

### 测试
- 递归扫描
- 模式过滤
- 文件树构建
- 目录信息收集

---

## Task W6: WorkspaceManager + DI

### internal/WorkspaceManagerImpl.kt
- 工作区目录在应用内部存储 `files/workspaces/` 下
- 创建工作区时自动创建 AGENTS.md, MEMORY.md, CONFIG/, SKILLS/, EXPERIENCE/ 结构
- 切换工作区更新 active 状态
- listWorkspaces 返回 Flow<List<Workspace>>
- 使用 ConcurrentHashMap 缓存工作区

### di/WorkspaceModule.kt
```kotlin
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WorkspaceModule {
    @Binds @Singleton
    abstract fun bindWorkspaceFileManager(impl: WorkspaceFileManagerImpl): WorkspaceFileManager

    @Binds @Singleton
    abstract fun bindConfigValidator(impl: ConfigValidatorImpl): ConfigValidator

    @Binds @Singleton
    abstract fun bindWorkspaceConfigManager(impl: WorkspaceConfigManagerImpl): WorkspaceConfigManager

    @Binds @Singleton
    abstract fun bindDirectoryScanner(impl: DirectoryScannerImpl): DirectoryScanner

    @Binds @Singleton
    abstract fun bindWorkspaceManager(impl: WorkspaceManagerImpl): WorkspaceManager
}
```

### 测试
- 创建/切换/删除工作区
- 工作区目录结构验证
- 工作区列表

---

## Task W7: 全量验证
- `./gradlew test` 全部通过
- `./gradlew assembleDebug` 构建成功
- 验证所有模块正确注册
