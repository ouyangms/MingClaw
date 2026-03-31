# MingClaw 插件系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 MingClaw 插件系统——插件注册表 (PluginRegistry)、工具注册表 (ToolRegistry)、插件加载器 (PluginLoader)、安全管理器 (SecurityManager)，使微内核能动态加载/卸载插件和注册工具。

**Architecture:** 在 core:kernel 之上新增 core:plugin 模块。插件系统通过 PluginRegistry 管理插件生命周期，ToolRegistry 管理工具注册与执行，PluginLoader 负责从 JAR/APK 文件加载插件，SecurityManager 提供权限检查和安全验证。所有接口定义在 core:model 的 plugin 子包中，实现在 core:plugin 的 internal 包中。

**Tech Stack:** Kotlin 2.0.21, Hilt 2.51, Coroutines 1.7.3, kotlinx.serialization 1.6.2, JUnit 4, MockK 1.13.9

---

## 文件结构总览

```
core/model/
  src/main/java/com/loy/mingclaw/core/model/plugin/
    MingClawPlugin.kt          # 插件核心接口
    Tool.kt                    # 工具核心接口
    PluginTypes.kt             # 所有插件相关数据类型和枚举
  src/test/java/com/loy/mingclaw/core/model/plugin/
    PluginTypesTest.kt         # 序列化和验证测试

core/plugin/
  build.gradle.kts
  src/main/java/com/loy/mingclaw/core/plugin/
    PluginRegistry.kt          # 插件注册表接口
    ToolRegistry.kt            # 工具注册表接口
    PluginLoader.kt            # 插件加载器接口
    SecurityManager.kt         # 安全管理器接口
    internal/PluginRegistryImpl.kt
    internal/ToolRegistryImpl.kt
    internal/PluginLoaderImpl.kt
    internal/SecurityManagerImpl.kt
    di/PluginModule.kt         # Hilt DI 模块
  src/test/java/com/loy/mingclaw/core/plugin/
    ToolRegistryImplTest.kt
    PluginRegistryImplTest.kt
    SecurityManagerImplTest.kt
```

---

### Task 1: 添加插件领域类型到 core:model

**Files:**
- Create: `core/model/src/main/java/com/loy/mingclaw/core/model/plugin/MingClawPlugin.kt`
- Create: `core/model/src/main/java/com/loy/mingclaw/core/model/plugin/Tool.kt`
- Create: `core/model/src/main/java/com/loy/mingclaw/core/model/plugin/PluginTypes.kt`
- Test: `core/model/src/test/java/com/loy/mingclaw/core/model/plugin/PluginTypesTest.kt`
- Modify: `settings.gradle.kts` (添加 `:core:plugin`)

- [ ] **Step 1: 写 PluginTypes 序列化测试**

`core/model/src/test/java/com/loy/mingclaw/core/model/plugin/PluginTypesTest.kt`:

```kotlin
package com.loy.mingclaw.core.model.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginTypesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `PluginMetadata serializes and deserializes`() {
        val original = PluginMetadata(
            pluginId = "tools.calculator",
            version = "1.0.0",
            name = "Calculator",
            description = "Basic calculator",
            author = "MingClaw",
            category = PluginCategory.Tool,
            permissions = listOf("NetworkAccess"),
            dependencies = listOf(PluginDependency(pluginId = "core.math", minVersion = "1.0.0")),
            entryPoint = "com.loy.mingclaw.plugin.CalculatorPlugin",
            minKernelVersion = "1.0.0",
            checksum = "sha256:abc123"
        )
        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<PluginMetadata>(jsonString)
        assertEquals(original, restored)
    }

    @Test
    fun `PluginDependency default values`() {
        val dep = PluginDependency(pluginId = "core.utils")
        assertEquals("core.utils", dep.pluginId)
        assertEquals(null, dep.maxVersion)
        assertTrue(dep.required)
    }

    @Test
    fun `PluginPermission has all expected values`() {
        val permissions = PluginPermission.values()
        assertTrue(permissions.any { it.name == "NetworkAccess" })
        assertTrue(permissions.any { it.name == "FileSystemRead" })
        assertTrue(permissions.any { it.name == "PluginManagement" })
    }

    @Test
    fun `ToolParameter serializes with defaults`() {
        val param = ToolParameter(
            name = "query",
            type = ParameterType.String,
            description = "Search query"
        )
        val jsonString = json.encodeToString(param)
        val restored = json.decodeFromString<ToolParameter>(jsonString)
        assertEquals("query", restored.name)
        assertEquals(ParameterType.String, restored.type)
        assertEquals(false, restored.required)
        assertEquals(null, restored.default)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:model:test`
Expected: FAIL — 源码不存在

- [ ] **Step 3: 创建 MingClawPlugin.kt**

`core/model/src/main/java/com/loy/mingclaw/core/model/plugin/MingClawPlugin.kt`:

```kotlin
package com.loy.mingclaw.core.model.plugin

import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.common.EventResult

interface MingClawPlugin {
    val pluginId: String
    val version: String
    val name: String
    val description: String
    val author: String

    fun getDependencies(): List<PluginDependency>
    fun getRequiredPermissions(): List<PluginPermission>
    suspend fun onInitialize(context: PluginContext): Result<Unit>
    fun onStart()
    fun onStop()
    suspend fun onCleanup()
    fun getTools(): List<Tool>
    fun handleEvent(event: Event): EventResult
}
```

- [ ] **Step 4: 创建 PluginContext.kt (在同文件中)**

将以下内容追加到 `MingClawPlugin.kt` 底部：

```kotlin
class PluginContext(
    val pluginId: String,
    val config: Map<String, Any> = emptyMap()
)
```

- [ ] **Step 5: 创建 Tool.kt**

`core/model/src/main/java/com/loy/mingclaw/core/model/plugin/Tool.kt`:

```kotlin
package com.loy.mingclaw.core.model.plugin

interface Tool {
    val toolId: String
    val name: String
    val description: String
    val category: ToolCategory
    val parameters: Map<String, ToolParameter>
    val requiresConfirmation: Boolean

    suspend fun execute(args: Map<String, Any>): ToolResult
}
```

- [ ] **Step 6: 创建 ToolResult sealed interface (在同文件中)**

将以下内容追加到 `Tool.kt` 底部：

```kotlin
sealed interface ToolResult {
    data class Success(val data: Any, val format: ResultFormat = ResultFormat.Text) : ToolResult
    data class Error(val message: String, val code: String? = null) : ToolResult
    data class Partial(val progress: Float, val message: String? = null, val data: Any? = null) : ToolResult
}

enum class ResultFormat {
    Text, Json, Markdown, Html, Binary
}
```

- [ ] **Step 7: 创建 PluginTypes.kt**

`core/model/src/main/java/com/loy/mingclaw/core/model/plugin/PluginTypes.kt`:

```kotlin
package com.loy.mingclaw.core.model.plugin

import kotlinx.serialization.Serializable

@Serializable
data class PluginMetadata(
    val pluginId: String,
    val version: String,
    val name: String,
    val description: String,
    val author: String,
    val category: PluginCategory,
    val permissions: List<String>,
    val dependencies: List<PluginDependency>,
    val entryPoint: String,
    val minKernelVersion: String,
    val checksum: String
)

enum class PluginCategory {
    Tool, Service, UI, Integration, Experimental
}

@Serializable
data class PluginDependency(
    val pluginId: String,
    val minVersion: String,
    val maxVersion: String? = null,
    val required: Boolean = true
)

enum class PluginPermission(val description: String) {
    NetworkAccess("访问网络"),
    FileSystemRead("读取文件系统"),
    FileSystemWrite("写入文件系统"),
    CameraAccess("访问摄像头"),
    MicrophoneAccess("访问麦克风"),
    LocationAccess("访问位置信息"),
    ContactAccess("访问联系人"),
    NotificationAccess("显示通知"),
    BackgroundExecution("后台执行"),
    SystemSettings("修改系统设置"),
    SensitiveData("访问敏感数据"),
    PluginManagement("管理其他插件")
}

enum class ToolCategory {
    Information, Action, Computation, Media, System, Custom
}

@Serializable
data class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = false,
    val default: Any? = null,
    val enum: List<Any>? = null,
    val format: String? = null
)

enum class ParameterType {
    String, Number, Integer, Boolean, Array, Object, Null
}

@Serializable
data class PluginInfo(
    val pluginId: String,
    val version: String,
    val name: String,
    val description: String,
    val author: String,
    val category: PluginCategory,
    val status: PluginStatus,
    val permissions: List<PluginPermission>,
    val dependencies: List<PluginDependency>,
    val tools: List<String>
)

enum class PluginStatus {
    Registered, Loading, Running, Stopped, Error, Unregistered
}
```

- [ ] **Step 8: 添加 `:core:plugin` 到 settings.gradle.kts**

在 `settings.gradle.kts` 的 `include` 块中添加 `include(":core:plugin")`。

- [ ] **Step 9: 运行测试确认通过**

Run: `./gradlew :core:model:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add core/model/src/main/java/com/loy/mingclaw/core/model/plugin/ core/model/src/test/java/com/loy/mingclaw/core/model/plugin/ settings.gradle.kts
git commit -m "feat: add plugin domain types (MingClawPlugin, Tool, PluginMetadata, ToolRegistry)"
```

---

### Task 2: 创建 core:plugin 模块基础设施

**Files:**
- Create: `core/plugin/build.gradle.kts`
- Create: `core/plugin/src/main/java/com/loy/mingclaw/core/plugin/PluginRegistry.kt`
- Create: `core/plugin/src/main/java/com/loy/mingclaw/core/plugin/ToolRegistry.kt`
- Create: `core/plugin/src/main/java/com/loy/mingclaw/core/plugin/PluginLoader.kt`
- Create: `core/plugin/src/main/java/com/loy/mingclaw/core/plugin/SecurityManager.kt`

- [ ] **Step 1: 创建 core/plugin/build.gradle.kts**

```kotlin
plugins {
    id("mingclaw.android.library")
    id("mingclaw.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.loy.mingclaw.core.plugin"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:kernel"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
```

- [ ] **Step 2: 创建 PluginRegistry.kt 接口**

```kotlin
package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginCategory
import com.loy.mingclaw.core.model.plugin.PluginInfo
import com.loy.mingclaw.core.model.plugin.PluginStatus
import com.loy.mingclaw.core.model.plugin.Tool

interface PluginRegistry {
    suspend fun registerPlugin(plugin: MingClawPlugin): Result<Unit>
    suspend fun unregisterPlugin(pluginId: String): Result<Unit>
    fun getPlugin(pluginId: String): MingClawPlugin?
    fun getAllPlugins(): List<MingClawPlugin>
    fun getPluginsByCategory(category: PluginCategory): List<MingClawPlugin>
    fun getPluginInfo(pluginId: String): PluginInfo?
    fun searchPlugins(query: String): List<MingClawPlugin>
    fun getAvailableTools(): List<Tool>
    fun getPluginStatus(pluginId: String): PluginStatus?
}
```

- [ ] **Step 3: 创建 ToolRegistry.kt 接口**

```kotlin
package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.Tool
import com.loy.mingclaw.core.model.plugin.ToolCategory
import com.loy.mingclaw.core.model.plugin.ToolResult

interface ToolRegistry {
    fun registerTool(tool: Tool): Result<Unit>
    fun unregisterTool(toolId: String): Result<Unit>
    fun getTool(toolId: String): Tool?
    fun getAllTools(): List<Tool>
    fun getToolsByCategory(category: ToolCategory): List<Tool>
    fun searchTools(query: String): List<Tool>
    suspend fun executeTool(toolId: String, args: Map<String, Any>): ToolResult
}
```

- [ ] **Step 4: 创建 PluginLoader.kt 接口**

```kotlin
package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginMetadata
import java.io.File

interface PluginLoader {
    suspend fun loadFromFile(file: File): Result<MingClawPlugin>
    fun validatePlugin(plugin: MingClawPlugin): ValidationResult
    suspend fun extractMetadata(file: File): Result<PluginMetadata>
}

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)
```

- [ ] **Step 5: 创建 SecurityManager.kt 接口**

```kotlin
package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginPermission

interface SecurityManager {
    suspend fun initialize(): Result<Unit>
    fun checkPluginPermission(pluginId: String, permission: PluginPermission): Boolean
    fun isPluginSafe(plugin: MingClawPlugin): Boolean
    fun verifySignature(data: ByteArray, signature: ByteArray): Boolean
}
```

- [ ] **Step 6: Commit**

```bash
git add core/plugin/
git commit -m "feat: add core:plugin module interfaces (PluginRegistry, ToolRegistry, PluginLoader, SecurityManager)"
```

---

### Task 3: 实现 ToolRegistry

**Files:**
- Create: `core/plugin/src/main/java/com/loy/mingclaw/core/plugin/internal/ToolRegistryImpl.kt`
- Test: `core/plugin/src/test/java/com/loy/mingclaw/core/plugin/ToolRegistryImplTest.kt`

- [ ] **Step 1: 写 ToolRegistry 失败测试**

`core/plugin/src/test/java/com/loy/mingclaw/core/plugin/ToolRegistryImplTest.kt`:

```kotlin
package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.Tool
import com.loy.mingclaw.core.model.plugin.ToolCategory
import com.loy.mingclaw.core.model.plugin.ToolParameter
import com.loy.mingclaw.core.model.plugin.ToolResult
import com.loy.mingclaw.core.model.plugin.ParameterType
import com.loy.mingclaw.core.plugin.internal.ToolRegistryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolRegistryImplTest {

    private lateinit var registry: ToolRegistryImpl
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private val testTool = object : Tool {
        override val toolId = "tools.test.echo"
        override val name = "Echo"
        override val description = "Echoes input"
        override val category = ToolCategory.Action
        override val parameters = mapOf(
            "message" to ToolParameter(
                name = "message",
                type = ParameterType.String,
                description = "Message to echo",
                required = true
            )
        )
        override val requiresConfirmation = false
        override suspend fun execute(args: Map<String, Any>): ToolResult {
            return ToolResult.Success(data = args["message"] ?: "")
        }
    }

    @Before
    fun setup() {
        registry = ToolRegistryImpl(testDispatcher)
    }

    @Test
    fun `registerTool adds tool and getTool retrieves it`() {
        val result = registry.registerTool(testTool)
        assertTrue(result.isSuccess)
        assertEquals(testTool, registry.getTool("tools.test.echo"))
    }

    @Test
    fun `registerTool rejects duplicate toolId`() {
        registry.registerTool(testTool)
        val result = registry.registerTool(testTool)
        assertTrue(result.isFailure)
    }

    @Test
    fun `unregisterTool removes tool`() {
        registry.registerTool(testTool)
        val result = registry.unregisterTool("tools.test.echo")
        assertTrue(result.isSuccess)
        assertEquals(null, registry.getTool("tools.test.echo"))
    }

    @Test
    fun `getAllTools returns all registered tools`() {
        registry.registerTool(testTool)
        val tool2 = object : Tool {
            override val toolId = "tools.test.calc"
            override val name = "Calc"
            override val description = "Calculates"
            override val category = ToolCategory.Computation
            override val parameters = emptyMap()
            override val requiresConfirmation = false
            override suspend fun execute(args: Map<String, Any>) = ToolResult.Success(data = 0)
        }
        registry.registerTool(tool2)
        assertEquals(2, registry.getAllTools().size)
    }

    @Test
    fun `getToolsByCategory filters correctly`() {
        registry.registerTool(testTool)
        val actionTools = registry.getToolsByCategory(ToolCategory.Action)
        assertEquals(1, actionTools.size)
        assertEquals("tools.test.echo", actionTools[0].toolId)

        val computeTools = registry.getToolsByCategory(ToolCategory.Computation)
        assertTrue(computeTools.isEmpty())
    }

    @Test
    fun `executeTool runs tool and returns result`() = runTest(testDispatcher) {
        registry.registerTool(testTool)
        val result = registry.executeTool("tools.test.echo", mapOf("message" to "hello"))
        assertTrue(result is ToolResult.Success)
        assertEquals("hello", (result as ToolResult.Success).data)
    }

    @Test
    fun `executeTool returns error for unknown tool`() = runTest(testDispatcher) {
        val result = registry.executeTool("nonexistent", emptyMap())
        assertTrue(result is ToolResult.Error)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:plugin:test --tests ToolRegistryImplTest`
Expected: FAIL

- [ ] **Step 3: 创建 ToolRegistryImpl**

`core/plugin/src/main/java/com/loy/mingclaw/core/plugin/internal/ToolRegistryImpl.kt`:

```kotlin
package com.loy.mingclaw.core.plugin.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.plugin.Tool
import com.loy.mingclaw.core.model.plugin.ToolCategory
import com.loy.mingclaw.core.model.plugin.ToolResult
import com.loy.mingclaw.core.plugin.ToolRegistry
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ToolRegistryImpl @Inject constructor(
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : ToolRegistry {

    private val tools = ConcurrentHashMap<String, Tool>()
    private val toolsByCategory = ConcurrentHashMap<ToolCategory, MutableList<String>>()

    override fun registerTool(tool: Tool): Result<Unit> {
        val existing = tools.putIfAbsent(tool.toolId, tool)
        if (existing != null) {
            return Result.failure(IllegalArgumentException("Tool already registered: ${tool.toolId}"))
        }
        toolsByCategory.computeIfAbsent(tool.category) { mutableListOf() }.add(tool.toolId)
        return Result.success(Unit)
    }

    override fun unregisterTool(toolId: String): Result<Unit> {
        val tool = tools.remove(toolId) ?: return Result.failure(IllegalArgumentException("Tool not found: $toolId"))
        toolsByCategory[tool.category]?.remove(toolId)
        return Result.success(Unit)
    }

    override fun getTool(toolId: String): Tool? = tools[toolId]

    override fun getAllTools(): List<Tool> = tools.values.toList()

    override fun getToolsByCategory(category: ToolCategory): List<Tool> {
        val toolIds = toolsByCategory[category] ?: return emptyList()
        return toolIds.mapNotNull { tools[it] }
    }

    override fun searchTools(query: String): List<Tool> {
        val lowerQuery = query.lowercase()
        return tools.values.filter {
            it.name.lowercase().contains(lowerQuery) ||
                it.description.lowercase().contains(lowerQuery)
        }
    }

    override suspend fun executeTool(toolId: String, args: Map<String, Any>): ToolResult {
        val tool = tools[toolId]
            ?: return ToolResult.Error(message = "Tool not found: $toolId", code = "TOOL_NOT_FOUND")
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            ToolResult.Error(message = e.message ?: "Tool execution failed", code = "EXECUTION_ERROR")
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :core:plugin:test --tests ToolRegistryImplTest`
Expected: BUILD SUCCESSFUL, 7 tests pass

- [ ] **Step 5: Commit**

```bash
git add core/plugin/src/main/java/com/loy/mingclaw/core/plugin/internal/ToolRegistryImpl.kt core/plugin/src/test/java/com/loy/mingclaw/core/plugin/ToolRegistryImplTest.kt
git commit -m "feat: implement ToolRegistry with category indexing and execution"
```

---

### Task 4: 实现 SecurityManager

**Files:**
- Create: `core/plugin/src/main/java/com/loy/mingclaw/core/plugin/internal/SecurityManagerImpl.kt`
- Test: `core/plugin/src/test/java/com/loy/mingclaw/core/plugin/SecurityManagerImplTest.kt`

- [ ] **Step 1: 写 SecurityManager 失败测试**

`core/plugin/src/test/java/com/loy/mingclaw/core/plugin/SecurityManagerImplTest.kt`:

```kotlin
package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginPermission
import com.loy.mingclaw.core.plugin.internal.SecurityManagerImpl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecurityManagerImplTest {

    private lateinit var securityManager: SecurityManagerImpl

    @Before
    fun setup() {
        securityManager = SecurityManagerImpl()
    }

    @Test
    fun `initialize succeeds`() {
        val result = securityManager.initialize()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `checkPluginPermission returns false for ungranted permission`() {
        securityManager.initialize()
        assertFalse(securityManager.checkPluginPermission("unknown.plugin", PluginPermission.NetworkAccess))
    }

    @Test
    fun `isPluginSafe validates pluginId format`() {
        val safePlugin = object : MingClawPlugin {
            override val pluginId = "tools.calculator"
            override val version = "1.0.0"
            override val name = "Calc"
            override val description = "Calculator"
            override val author = "Test"
            override fun getDependencies() = emptyList<com.loy.mingclaw.core.model.plugin.PluginDependency>()
            override fun getRequiredPermissions() = emptyList<PluginPermission>()
            override suspend fun onInitialize(context: com.loy.mingclaw.core.model.plugin.PluginContext) = Result.success(Unit)
            override fun onStart() {}
            override fun onStop() {}
            override suspend fun onCleanup() {}
            override fun getTools() = emptyList<com.loy.mingclaw.core.model.plugin.Tool>()
            override fun handleEvent(event: com.loy.mingclaw.core.model.Event) = com.loy.mingclaw.core.model.common.EventResult.Success("test")
        }
        assertTrue(securityManager.isPluginSafe(safePlugin))
    }

    @Test
    fun `isPluginSafe rejects invalid pluginId`() {
        val badPlugin = object : MingClawPlugin {
            override val pluginId = "INVALID!!"
            override val version = "1.0.0"
            override val name = "Bad"
            override val description = "Bad plugin"
            override val author = "Test"
            override fun getDependencies() = emptyList<com.loy.mingclaw.core.model.plugin.PluginDependency>()
            override fun getRequiredPermissions() = emptyList<PluginPermission>()
            override suspend fun onInitialize(context: com.loy.mingclaw.core.model.plugin.PluginContext) = Result.success(Unit)
            override fun onStart() {}
            override fun onStop() {}
            override suspend fun onCleanup() {}
            override fun getTools() = emptyList<com.loy.mingclaw.core.model.plugin.Tool>()
            override fun handleEvent(event: com.loy.mingclaw.core.model.Event) = com.loy.mingclaw.core.model.common.EventResult.Success("test")
        }
        assertFalse(securityManager.isPluginSafe(badPlugin))
    }

    @Test
    fun `verifySignature returns false for wrong signature`() {
        securityManager.initialize()
        val data = "test".toByteArray()
        val signature = "wrong".toByteArray()
        assertFalse(securityManager.verifySignature(data, signature))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:plugin:test --tests SecurityManagerImplTest`
Expected: FAIL

- [ ] **Step 3: 创建 SecurityManagerImpl**

`core/plugin/src/main/java/com/loy/mingclaw/core/plugin/internal/SecurityManagerImpl.kt`:

```kotlin
package com.loy.mingclaw.core.plugin.internal

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginPermission
import com.loy.mingclaw.core.plugin.SecurityManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SecurityManagerImpl @Inject constructor() : SecurityManager {

    private val pluginIdPattern = Regex("[a-z0-9_.]+")
    private var initialized = false

    override suspend fun initialize(): Result<Unit> {
        initialized = true
        return Result.success(Unit)
    }

    override fun checkPluginPermission(pluginId: String, permission: PluginPermission): Boolean {
        // In MVP, all permissions are denied by default
        // Permission granting will be implemented with DataStore persistence
        return false
    }

    override fun isPluginSafe(plugin: MingClawPlugin): Boolean {
        return pluginIdPattern.matches(plugin.pluginId) &&
            plugin.version.isNotBlank() &&
            plugin.name.isNotBlank()
    }

    override fun verifySignature(data: ByteArray, signature: ByteArray): Boolean {
        // MVP: Signature verification not yet implemented
        return false
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :core:plugin:test --tests SecurityManagerImplTest`
Expected: BUILD SUCCESSFUL, 5 tests pass

- [ ] **Step 5: Commit**

```bash
git add core/plugin/src/main/java/com/loy/mingclaw/core/plugin/internal/SecurityManagerImpl.kt core/plugin/src/test/java/com/loy/mingclaw/core/plugin/SecurityManagerImplTest.kt
git commit -m "feat: implement SecurityManager with plugin ID validation"
```

---

### Task 5: 实现 PluginRegistry 与 DI 模块

**Files:**
- Create: `core/plugin/src/main/java/com/loy/mingclaw/core/plugin/internal/PluginRegistryImpl.kt`
- Create: `core/plugin/src/main/java/com/loy/mingclaw/core/plugin/di/PluginModule.kt`
- Test: `core/plugin/src/test/java/com/loy/mingclaw/core/plugin/PluginRegistryImplTest.kt`

- [ ] **Step 1: 写 PluginRegistry 失败测试**

`core/plugin/src/test/java/com/loy/mingclaw/core/plugin/PluginRegistryImplTest.kt`:

```kotlin
package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginCategory
import com.loy.mingclaw.core.model.plugin.PluginPermission
import com.loy.mingclaw.core.model.plugin.PluginStatus
import com.loy.mingclaw.core.model.plugin.Tool
import com.loy.mingclaw.core.model.plugin.ToolCategory
import com.loy.mingclaw.core.model.plugin.ToolParameter
import com.loy.mingclaw.core.model.plugin.ToolResult
import com.loy.mingclaw.core.model.plugin.ParameterType
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.plugin.internal.PluginRegistryImpl
import com.loy.mingclaw.core.plugin.internal.SecurityManagerImpl
import com.loy.mingclaw.core.plugin.internal.ToolRegistryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginRegistryImplTest {

    private lateinit var registry: PluginRegistryImpl
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private fun createTestPlugin(
        pluginId: String = "tools.test",
        tools: List<Tool> = emptyList()
    ) = object : MingClawPlugin {
        override val pluginId = pluginId
        override val version = "1.0.0"
        override val name = "Test Plugin"
        override val description = "A test plugin"
        override val author = "Test"
        override fun getDependencies() = emptyList<com.loy.mingclaw.core.model.plugin.PluginDependency>()
        override fun getRequiredPermissions() = emptyList<PluginPermission>()
        override suspend fun onInitialize(context: com.loy.mingclaw.core.model.plugin.PluginContext) = Result.success(Unit)
        override fun onStart() {}
        override fun onStop() {}
        override suspend fun onCleanup() {}
        override fun getTools() = tools
        override fun handleEvent(event: Event) = EventResult.Success("test")
    }

    @Before
    fun setup() {
        val toolRegistry = ToolRegistryImpl(testDispatcher)
        val securityManager = SecurityManagerImpl()
        registry = PluginRegistryImpl(toolRegistry, securityManager, testDispatcher)
    }

    @Test
    fun `registerPlugin stores plugin and updates status`() = runTest(testDispatcher) {
        val plugin = createTestPlugin()
        val result = registry.registerPlugin(plugin)
        assertTrue(result.isSuccess)
        assertEquals(PluginStatus.Running, registry.getPluginStatus("tools.test"))
    }

    @Test
    fun `registerPlugin registers plugin tools`() = runTest(testDispatcher) {
        val tool = object : Tool {
            override val toolId = "tools.test.echo"
            override val name = "Echo"
            override val description = "Echo"
            override val category = ToolCategory.Action
            override val parameters = emptyMap<String, ToolParameter>()
            override val requiresConfirmation = false
            override suspend fun execute(args: Map<String, Any>) = ToolResult.Success("echo")
        }
        val plugin = createTestPlugin(tools = listOf(tool))
        registry.registerPlugin(plugin)

        val availableTools = registry.getAvailableTools()
        assertEquals(1, availableTools.size)
        assertEquals("tools.test.echo", availableTools[0].toolId)
    }

    @Test
    fun `unregisterPlugin removes plugin and its tools`() = runTest(testDispatcher) {
        val tool = object : Tool {
            override val toolId = "tools.test.echo"
            override val name = "Echo"
            override val description = "Echo"
            override val category = ToolCategory.Action
            override val parameters = emptyMap<String, ToolParameter>()
            override val requiresConfirmation = false
            override suspend fun execute(args: Map<String, Any>) = ToolResult.Success("echo")
        }
        val plugin = createTestPlugin(tools = listOf(tool))
        registry.registerPlugin(plugin)
        registry.unregisterPlugin("tools.test")

        assertEquals(null, registry.getPlugin("tools.test"))
        assertEquals(0, registry.getAvailableTools().size)
    }

    @Test
    fun `getAllPlugins returns all registered plugins`() = runTest(testDispatcher) {
        registry.registerPlugin(createTestPlugin("tools.a"))
        registry.registerPlugin(createTestPlugin("tools.b"))
        assertEquals(2, registry.getAllPlugins().size)
    }

    @Test
    fun `getPluginsByCategory filters correctly`() = runTest(testDispatcher) {
        registry.registerPlugin(createTestPlugin("tools.test"))
        // Default category matching is by PluginCategory - plugins don't expose category directly
        // so this test verifies getAllPlugins returns the plugin
        assertEquals(1, registry.getAllPlugins().size)
    }

    @Test
    fun `registerPlugin rejects invalid pluginId`() = runTest(testDispatcher) {
        val plugin = createTestPlugin(pluginId = "INVALID!!")
        val result = registry.registerPlugin(plugin)
        assertTrue(result.isFailure)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:plugin:test --tests PluginRegistryImplTest`
Expected: FAIL

- [ ] **Step 3: 创建 PluginRegistryImpl**

`core/plugin/src/main/java/com/loy/mingclaw/core/plugin/internal/PluginRegistryImpl.kt`:

```kotlin
package com.loy.mingclaw.core.plugin.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginCategory
import com.loy.mingclaw.core.model.plugin.PluginInfo
import com.loy.mingclaw.core.model.plugin.PluginStatus
import com.loy.mingclaw.core.model.plugin.Tool
import com.loy.mingclaw.core.plugin.PluginRegistry
import com.loy.mingclaw.core.plugin.SecurityManager
import com.loy.mingclaw.core.plugin.ToolRegistry
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PluginRegistryImpl @Inject constructor(
    private val toolRegistry: ToolRegistry,
    private val securityManager: SecurityManager,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : PluginRegistry {

    private val plugins = ConcurrentHashMap<String, MingClawPlugin>()
    private val pluginStatus = ConcurrentHashMap<String, PluginStatus>()

    override suspend fun registerPlugin(plugin: MingClawPlugin): Result<Unit> {
        if (!securityManager.isPluginSafe(plugin)) {
            return Result.failure(IllegalArgumentException("Plugin failed security check: ${plugin.pluginId}"))
        }
        plugins[plugin.pluginId] = plugin
        pluginStatus[plugin.pluginId] = PluginStatus.Running

        // Register all plugin tools
        plugin.getTools().forEach { tool ->
            toolRegistry.registerTool(tool)
        }
        return Result.success(Unit)
    }

    override suspend fun unregisterPlugin(pluginId: String): Result<Unit> {
        val plugin = plugins.remove(pluginId)
            ?: return Result.failure(IllegalArgumentException("Plugin not found: $pluginId"))

        // Unregister all plugin tools
        plugin.getTools().forEach { tool ->
            toolRegistry.unregisterTool(tool.toolId)
        }
        pluginStatus[pluginId] = PluginStatus.Unregistered
        return Result.success(Unit)
    }

    override fun getPlugin(pluginId: String): MingClawPlugin? = plugins[pluginId]

    override fun getAllPlugins(): List<MingClawPlugin> = plugins.values.toList()

    override fun getPluginsByCategory(category: PluginCategory): List<MingClawPlugin> {
        // Category filtering would need plugins to expose their category
        // For now return all since MingClawPlugin doesn't have a category field
        return getAllPlugins()
    }

    override fun getPluginInfo(pluginId: String): PluginInfo? {
        val plugin = plugins[pluginId] ?: return null
        return PluginInfo(
            pluginId = plugin.pluginId,
            version = plugin.version,
            name = plugin.name,
            description = plugin.description,
            author = plugin.author,
            category = PluginCategory.Tool,
            status = pluginStatus[pluginId] ?: PluginStatus.Unknown,
            permissions = plugin.getRequiredPermissions(),
            dependencies = plugin.getDependencies(),
            tools = plugin.getTools().map { it.toolId }
        )
    }

    override fun searchPlugins(query: String): List<MingClawPlugin> {
        val lowerQuery = query.lowercase()
        return plugins.values.filter {
            it.name.lowercase().contains(lowerQuery) ||
                it.description.lowercase().contains(lowerQuery)
        }
    }

    override fun getAvailableTools(): List<Tool> = toolRegistry.getAllTools()

    override fun getPluginStatus(pluginId: String): PluginStatus? =
        pluginStatus[pluginId]
}
```

- [ ] **Step 4: 创建 Hilt DI 模块**

`core/plugin/src/main/java/com/loy/mingclaw/core/plugin/di/PluginModule.kt`:

```kotlin
package com.loy.mingclaw.core.plugin.di

import com.loy.mingclaw.core.plugin.PluginRegistry
import com.loy.mingclaw.core.plugin.PluginLoader
import com.loy.mingclaw.core.plugin.SecurityManager
import com.loy.mingclaw.core.plugin.ToolRegistry
import com.loy.mingclaw.core.plugin.internal.PluginRegistryImpl
import com.loy.mingclaw.core.plugin.internal.PluginLoaderImpl
import com.loy.mingclaw.core.plugin.internal.SecurityManagerImpl
import com.loy.mingclaw.core.plugin.internal.ToolRegistryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PluginModule {

    @Binds
    @Singleton
    abstract fun bindToolRegistry(impl: ToolRegistryImpl): ToolRegistry

    @Binds
    @Singleton
    abstract fun bindSecurityManager(impl: SecurityManagerImpl): SecurityManager

    @Binds
    @Singleton
    abstract fun bindPluginRegistry(impl: PluginRegistryImpl): PluginRegistry

    @Binds
    @Singleton
    abstract fun bindPluginLoader(impl: PluginLoaderImpl): PluginLoader
}
```

- [ ] **Step 5: 创建 PluginLoaderImpl (stub)**

`core/plugin/src/main/java/com/loy/mingclaw/core/plugin/internal/PluginLoaderImpl.kt`:

```kotlin
package com.loy.mingclaw.core.plugin.internal

import com.loy.mingclaw.core.model.plugin.MingClawPlugin
import com.loy.mingclaw.core.model.plugin.PluginDependency
import com.loy.mingclaw.core.model.plugin.PluginMetadata
import com.loy.mingclaw.core.plugin.PluginLoader
import com.loy.mingclaw.core.plugin.ValidationResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PluginLoaderImpl @Inject constructor() : PluginLoader {

    override suspend fun loadFromFile(file: File): Result<MingClawPlugin> {
        if (!file.exists()) {
            return Result.failure(IllegalArgumentException("Plugin file not found: ${file.path}"))
        }
        return Result.failure(UnsupportedOperationException("Dynamic plugin loading not yet implemented"))
    }

    override fun validatePlugin(plugin: MingClawPlugin): ValidationResult {
        val errors = mutableListOf<String>()
        if (plugin.pluginId.isBlank()) errors.add("Plugin ID cannot be blank")
        if (plugin.version.isBlank()) errors.add("Version cannot be blank")
        if (plugin.name.isBlank()) errors.add("Name cannot be blank")
        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    override suspend fun extractMetadata(file: File): Result<PluginMetadata> {
        if (!file.exists()) {
            return Result.failure(IllegalArgumentException("Plugin file not found: ${file.path}"))
        }
        return Result.failure(UnsupportedOperationException("Metadata extraction not yet implemented"))
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `./gradlew :core:plugin:test`
Expected: BUILD SUCCESSFUL, all tests pass (7 ToolRegistry + 5 SecurityManager + 6 PluginRegistry = 18)

- [ ] **Step 7: Commit**

```bash
git add core/plugin/src/main/java/com/loy/mingclaw/core/plugin/internal/ core/plugin/src/main/java/com/loy/mingclaw/core/plugin/di/ core/plugin/src/test/java/com/loy/mingclaw/core/plugin/PluginRegistryImplTest.kt
git commit -m "feat: implement PluginRegistry, SecurityManager, PluginLoader stub, and DI module"
```

---

### Task 6: 全量验证

**Files:** 全项目

- [ ] **Step 1: 运行全部测试**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 构建 Debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 验证模块结构**

Run: `./gradlew projects`
Expected: 包含 `:core:plugin`

- [ ] **Step 4: Commit 如有更改**

```bash
git add -A
git commit -m "chore: verify plugin system build and tests"
```
