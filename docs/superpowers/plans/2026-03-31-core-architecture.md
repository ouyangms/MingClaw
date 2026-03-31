# MingClaw 核心基础架构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 MingClaw 的核心基础架构——多模块构建系统、微内核接口、事件总线、配置管理，使后续功能模块有可运行的骨架。

**Architecture:** 采用插件化微内核四层架构。本次实现聚焦 Core Layer：微内核接口 (`MingClawKernel`)、事件总线 (`EventBus`)、配置管理 (`ConfigManager`)，以及支撑它们的多模块 Gradle 构建系统。使用 Hilt DI、Kotlin Coroutines/Flow、DataStore、kotlinx.serialization。

**Tech Stack:** Kotlin 2.0.21, Gradle 8.13 (Kotlin DSL), Hilt 2.51, Coroutines 1.7.3, DataStore 1.0.0, kotlinx.serialization 1.6.2, kotlinx-datetime 0.5.0, JUnit 4, MockK 1.13.9, Turbine 1.1.0

---

## 文件结构总览

本计划将创建以下文件：

```
gradle/libs.versions.toml                          # 更新：添加全部依赖版本
settings.gradle.kts                                # 更新：注册所有模块
build.gradle.kts                                   # 更新：添加 hilt 插件

build-logic/convention/build.gradle.kts            # 创建：Convention 插件构建
build-logic/convention/src/main/kotlin/
  mingclaw.android.library.gradle.kts              # Android Library 约定插件
  mingclaw.hilt.gradle.kts                         # Hilt 约定插件
build-logic/settings.gradle.kts                    # 创建：build-logic 设置

core/model/
  build.gradle.kts                                 # 创建
  src/main/java/com/loy/mingclaw/core/model/
    Event.kt                                       # 事件类型 sealed interface
    KernelConfig.kt                                # 内核配置 data class
    SystemStatus.kt                                # 系统状态 data class
    TokenBudget.kt                                 # Token 预算 data class
    common/Subscription.kt                         # 订阅类型
  src/test/java/com/loy/mingclaw/core/model/
    KernelConfigTest.kt                            # KernelConfig 序列化测试
    TokenBudgetTest.kt                             # TokenBudget 计算测试

core/common/
  build.gradle.kts                                 # 创建
  src/main/java/com/loy/mingclaw/core/common/
    dispatchers/DispatcherModule.kt                 # Hilt @IODispatcher 限定符
    dispatchers/MingClawDispatchers.kt              # Dispatcher 接口
  src/test/java/com/loy/mingclaw/core/common/
    dispatchers/DispatcherModuleTest.kt             # Dispatcher 注入测试

core/kernel/
  build.gradle.kts                                 # 创建
  src/main/java/com/loy/mingclaw/core/kernel/
    MingClawKernel.kt                              # 微内核核心接口
    EventBus.kt                                    # 事件总线接口
    ConfigManager.kt                               # 配置管理接口
    internal/MingClawKernelImpl.kt                  # 微内核实现
    internal/EventBusImpl.kt                        # 事件总线实现
    internal/ConfigManagerImpl.kt                   # 配置管理实现
    internal/DefaultKernelConfig.kt                 # 默认配置提供者
    di/KernelModule.kt                              # Hilt DI 模块
  src/test/java/com/loy/mingclaw/core/kernel/
    EventBusImplTest.kt                             # 事件总线测试
    ConfigManagerImplTest.kt                        # 配置管理测试
    MingClawKernelImplTest.kt                       # 微内核集成测试

app/build.gradle.kts                               # 更新：添加 Hilt、依赖模块
app/src/main/java/com/loy/mingclaw/
  MingClawApplication.kt                            # 创建：Application 子类
app/src/main/AndroidManifest.xml                    # 更新：注册 Application
app/src/main/res/values/strings.xml                 # 更新：app_name 改为 MingClaw
```

---

### Task 1: 更新版本目录

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: 更新 libs.versions.toml，添加全部基础架构依赖**

将文件内容替换为：

```toml
[versions]
# SDK
compileSdk = "36"
minSdk = "32"
targetSdk = "36"

# Kotlin & Tools
agp = "8.13.2"
kotlin = "2.0.21"
kotlinxCoroutines = "1.7.3"
kotlinxSerializationJson = "1.6.2"
kotlinxDatetime = "0.5.0"
ksp = "2.0.21-1.0.28"

# AndroidX
androidxCore = "1.18.0"
androidxAppcompat = "1.7.1"
androidxLifecycle = "2.7.0"
androidxActivity = "1.8.2"
androidxDataStore = "1.0.0"
material = "1.13.0"

# DI
hilt = "2.51"
androidxHilt = "1.1.0"

# Testing
junit = "4.13.2"
junitVersion = "1.3.0"
espressoCore = "3.7.0"
mockk = "1.13.9"
turbine = "1.1.0"
kotlinxCoroutinesTest = "1.7.3"

[libraries]
# Kotlin
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutinesTest" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerializationJson" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinxDatetime" }

# AndroidX
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "androidxCore" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "androidxAppcompat" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "androidxLifecycle" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "androidxDataStore" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }

# Testing
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

# Build logic (for convention plugins)
android-gradlePlugin = { group = "com.android.tools.build", name = "gradle", version.ref = "agp" }
kotlin-gradlePlugin = { group = "org.jetbrains.kotlin", name = "kotlin-gradle-plugin", version.ref = "kotlin" }
ksp-gradlePlugin = { group = "com.google.devtools.ksp", name = "com.google.devtools.ksp.gradle.plugin", version.ref = "ksp" }
hilt-gradlePlugin = { group = "com.google.dagger", name = "hilt-android-gradle-plugin", version.ref = "hilt" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: 验证 Gradle Sync 成功**

Run: `./gradlew help`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: update version catalog with core architecture dependencies"
```

---

### Task 2: 创建 build-logic Convention 插件

**Files:**
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/mingclaw.android.library.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/mingclaw.hilt.gradle.kts`
- Modify: `settings.gradle.kts` (添加 pluginManagement 和 build-logic include)
- Modify: `build.gradle.kts` (添加 convention 插件声明)

- [ ] **Step 1: 创建 build-logic/settings.gradle.kts**

```kotlin
dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
```

- [ ] **Step 2: 创建 build-logic/convention/build.gradle.kts**

```kotlin
plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

group = "com.loy.mingclaw.buildlogic"
```

- [ ] **Step 3: 创建 Android Library 约定插件**

`build-logic/convention/src/main/kotlin/mingclaw.android.library.gradle.kts`:

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}
```

- [ ] **Step 4: 创建 Hilt 约定插件**

`build-logic/convention/src/main/kotlin/mingclaw.hilt.gradle.kts`:

```kotlin
plugins {
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}
```

- [ ] **Step 5: 更新根 settings.gradle.kts**

将 `settings.gradle.kts` 替换为：

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MingClaw"
include(":app")
include(":core:model")
include(":core:common")
include(":core:kernel")
```

- [ ] **Step 6: 更新根 build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    mingclaw.android.library apply false
    mingclaw.hilt apply false
}
```

- [ ] **Step 7: 验证 Gradle 配置正确**

Run: `./gradlew projects`
Expected: 列出 `:app`、`:core:model`、`:core:common`、`:core:kernel` 四个项目（编译会失败因为模块 build 文件尚不存在，但 projects 任务应成功）

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts build-logic/
git commit -m "build: add convention plugins and multi-module project structure"
```

---

### Task 3: 创建 core:model 模块

**Files:**
- Create: `core/model/build.gradle.kts`
- Create: `core/model/src/main/java/com/loy/mingclaw/core/model/Event.kt`
- Create: `core/model/src/main/java/com/loy/mingclaw/core/model/KernelConfig.kt`
- Create: `core/model/src/main/java/com/loy/mingclaw/core/model/SystemStatus.kt`
- Create: `core/model/src/main/java/com/loy/mingclaw/core/model/TokenBudget.kt`
- Create: `core/model/src/main/java/com/loy/mingclaw/core/model/common/Subscription.kt`
- Test: `core/model/src/test/java/com/loy/mingclaw/core/model/KernelConfigTest.kt`
- Test: `core/model/src/test/java/com/loy/mingclaw/core/model/TokenBudgetTest.kt`

- [ ] **Step 1: 写 KernelConfig 序列化失败测试**

`core/model/src/test/java/com/loy/mingclaw/core/model/KernelConfigTest.kt`:

```kotlin
package com.loy.mingclaw.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class KernelConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `KernelConfig serializes and deserializes with defaults`() {
        val original = KernelConfig()
        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<KernelConfig>(jsonString)
        assertEquals(original, restored)
    }

    @Test
    fun `KernelConfig has correct default values`() {
        val config = KernelConfig()
        assertEquals(8192, config.maxTokens)
        assertEquals("claude-opus-4-6", config.modelConfig.modelName)
        assertEquals(0.7, config.modelConfig.temperature, 0.001)
        assertEquals(4096, config.modelConfig.maxTokens)
    }

    @Test
    fun `KernelConfig serializes with custom values`() {
        val config = KernelConfig(
            maxTokens = 16384,
            modelConfig = ModelConfig(modelName = "gpt-4", temperature = 0.5)
        )
        val jsonString = json.encodeToString(config)
        val restored = json.decodeFromString<KernelConfig>(jsonString)
        assertEquals(16384, restored.maxTokens)
        assertEquals("gpt-4", restored.modelConfig.modelName)
        assertEquals(0.5, restored.modelConfig.temperature, 0.001)
    }
}
```

- [ ] **Step 2: 写 TokenBudget 计算失败测试**

`core/model/src/test/java/com/loy/mingclaw/core/model/TokenBudgetTest.kt`:

```kotlin
package com.loy.mingclaw.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenBudgetTest {

    @Test
    fun `calculate allocates tokens with default system reserve`() {
        val budget = TokenBudget.calculate(maxTokens = 10000)
        assertEquals(10000, budget.totalTokens)
        assertEquals(1000, budget.systemTokens)
        assertEquals(1800, budget.memoryTokens)   // 9000 * 0.2
        assertEquals(1800, budget.toolTokens)      // 9000 * 0.2
        assertEquals(5400, budget.conversationTokens) // 9000 * 0.6
    }

    @Test
    fun `calculate allocates tokens with custom system reserve`() {
        val budget = TokenBudget.calculate(maxTokens = 5000, systemReserved = 500)
        assertEquals(5000, budget.totalTokens)
        assertEquals(500, budget.systemTokens)
        assertEquals(900, budget.memoryTokens)     // 4500 * 0.2
        assertEquals(900, budget.toolTokens)        // 4500 * 0.2
        assertEquals(2700, budget.conversationTokens) // 4500 * 0.6
    }

    @Test
    fun `calculate handles minimum tokens`() {
        val budget = TokenBudget.calculate(maxTokens = 1000, systemReserved = 0)
        assertEquals(1000, budget.totalTokens)
        assertEquals(0, budget.systemTokens)
        assertEquals(200, budget.memoryTokens)
        assertEquals(200, budget.toolTokens)
        assertEquals(600, budget.conversationTokens)
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew :core:model:test`
Expected: `FAIL` — 模块 build 文件和源码尚未创建

- [ ] **Step 4: 创建 core:model/build.gradle.kts**

```kotlin
plugins {
    id("mingclaw.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
}
```

- [ ] **Step 5: 创建 Event.kt**

`core/model/src/main/java/com/loy/mingclaw/core/model/Event.kt`:

```kotlin
package com.loy.mingclaw.core.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

sealed interface Event {
    val timestamp: Instant

    @Serializable
    data class PluginLoaded(
        val pluginId: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class PluginUnloaded(
        val pluginId: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class PluginError(
        val pluginId: String,
        val error: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class TaskStarted(
        val taskId: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class TaskCompleted(
        val taskId: String,
        val result: String? = null,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class TaskFailed(
        val taskId: String,
        val error: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class ConfigUpdated(
        val config: KernelConfig,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event
}
```

- [ ] **Step 6: 创建 KernelConfig.kt**

`core/model/src/main/java/com/loy/mingclaw/core/model/KernelConfig.kt`:

```kotlin
package com.loy.mingclaw.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class KernelConfig(
    val maxTokens: Int = 8192,
    val modelConfig: ModelConfig = ModelConfig(),
    val pluginConfig: PluginConfig = PluginConfig(),
)

@Serializable
data class ModelConfig(
    val modelName: String = "claude-opus-4-6",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    val timeoutSeconds: Long = 120,
) {
    val timeout: Duration get() = timeoutSeconds.seconds
}

@Serializable
data class PluginConfig(
    val autoLoad: List<String> = emptyList(),
    val disabledPlugins: List<String> = emptyList(),
    val pluginDirectories: List<String> = listOf("/system/plugins", "/user/plugins"),
)
```

- [ ] **Step 7: 创建 SystemStatus.kt**

`core/model/src/main/java/com/loy/mingclaw/core/model/SystemStatus.kt`:

```kotlin
package com.loy.mingclaw.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SystemStatus(
    val isRunning: Boolean = false,
    val loadedPluginCount: Int = 0,
    val activeTaskCount: Int = 0,
    val uptimeSeconds: Long = 0,
    val lastHealthCheckTimestamp: Long = 0,
)

enum class PluginState {
    Registered, Loading, Running, Stopped, Error, Unregistered,
}
```

- [ ] **Step 8: 创建 TokenBudget.kt**

`core/model/src/main/java/com/loy/mingclaw/core/model/TokenBudget.kt`:

```kotlin
package com.loy.mingclaw.core.model

data class TokenBudget(
    val totalTokens: Int,
    val systemTokens: Int,
    val memoryTokens: Int,
    val toolTokens: Int,
    val conversationTokens: Int,
) {
    companion object {
        fun calculate(maxTokens: Int, systemReserved: Int = 1000): TokenBudget {
            val available = maxTokens - systemReserved
            return TokenBudget(
                totalTokens = maxTokens,
                systemTokens = systemReserved,
                memoryTokens = (available * 0.2).toInt(),
                toolTokens = (available * 0.2).toInt(),
                conversationTokens = (available * 0.6).toInt(),
            )
        }
    }
}
```

- [ ] **Step 9: 创建 Subscription.kt**

`core/model/src/main/java/com/loy/mingclaw/core/model/common/Subscription.kt`:

```kotlin
package com.loy.mingclaw.core.model.common

data class Subscription(
    val id: String,
    val eventType: String,
)

sealed class EventResult {
    data class Success(val subscriberId: String) : EventResult()
    data class Failed(val subscriberId: String, val error: Throwable) : EventResult()
    data class Skipped(val subscriberId: String) : EventResult()
}

fun interface EventSubscriber {
    val id: String
    fun onEvent(event: com.loy.mingclaw.core.model.Event): EventResult
}
```

- [ ] **Step 10: 运行测试确认通过**

Run: `./gradlew :core:model:test`
Expected: `BUILD SUCCESSFUL`，所有测试通过

- [ ] **Step 11: Commit**

```bash
git add core/model/
git commit -m "feat: add core:model module with domain types (Event, KernelConfig, TokenBudget)"
```

---

### Task 4: 创建 core:common 模块

**Files:**
- Create: `core/common/build.gradle.kts`
- Create: `core/common/src/main/java/com/loy/mingclaw/core/common/dispatchers/MingClawDispatchers.kt`
- Create: `core/common/src/main/java/com/loy/mingclaw/core/common/dispatchers/DispatcherModule.kt`
- Test: `core/common/src/test/java/com/loy/mingclaw/core/common/dispatchers/DispatcherModuleTest.kt`

- [ ] **Step 1: 写 Dispatcher 注入失败测试**

`core/common/src/test/java/com/loy/mingclaw/core/common/dispatchers/DispatcherModuleTest.kt`:

```kotlin
package com.loy.mingclaw.core.common.dispatchers

import org.junit.Assert.assertEquals
import org.junit.Test

class DispatcherModuleTest {

    @Test
    fun `MingClawDispatchers has IO and Default dispatchers`() {
        assertEquals("IO", MingClawDispatchers.IO)
        assertEquals("Default", MingClawDispatchers.Default)
    }

    @Test
    fun `IODispatcher qualifier uses correct name`() {
        val qualifier = IODispatcher
        assertEquals("IODispatcher", qualifier.name)
    }

    @Test
    fun `DefaultDispatcher qualifier uses correct name`() {
        val qualifier = DefaultDispatcher
        assertEquals("DefaultDispatcher", qualifier.name)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:common:test`
Expected: `FAIL`

- [ ] **Step 3: 创建 core/common/build.gradle.kts**

```kotlin
plugins {
    id("mingclaw.android.library")
    id("mingclaw.hilt")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
```

- [ ] **Step 4: 创建 MingClawDispatchers.kt**

`core/common/src/main/java/com/loy/mingclaw/core/common/dispatchers/MingClawDispatchers.kt`:

```kotlin
package com.loy.mingclaw.core.common.dispatchers

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

object MingClawDispatchers {
    const val IO = "IO"
    const val Default = "Default"
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IODispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IODispatcher
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :core:common:test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add core/common/
git commit -m "feat: add core:common module with Hilt dispatcher qualifiers"
```

---

### Task 5: 创建 EventBus 接口与实现

**Files:**
- Create: `core/kernel/build.gradle.kts`
- Create: `core/kernel/src/main/java/com/loy/mingclaw/core/kernel/EventBus.kt`
- Create: `core/kernel/src/main/java/com/loy/mingclaw/core/kernel/internal/EventBusImpl.kt`
- Test: `core/kernel/src/test/java/com/loy/mingclaw/core/kernel/EventBusImplTest.kt`

- [ ] **Step 1: 写 EventBus 失败测试**

`core/kernel/src/test/java/com/loy/mingclaw/core/kernel/EventBusImplTest.kt`:

```kotlin
package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.EventSubscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventBusImplTest {

    private lateinit var eventBus: EventBusImpl
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        eventBus = EventBusImpl(testDispatcher)
    }

    @Test
    fun `publish delivers event to single subscriber`() = runTest(testDispatcher) {
        var receivedEvent: Event? = null
        val subscriber = object : EventSubscriber {
            override val id = "sub-1"
            override fun onEvent(event: Event): EventResult {
                receivedEvent = event
                return EventResult.Success(id)
            }
        }

        eventBus.subscribe("PluginLoaded", subscriber)
        val event = Event.PluginLoaded(pluginId = "test-plugin")
        val results = eventBus.publish(event)

        assertEquals(1, results.size)
        assertTrue(results[0] is EventResult.Success)
        assertEquals(event, receivedEvent)
    }

    @Test
    fun `publish delivers event to multiple subscribers`() = runTest(testDispatcher) {
        val received = mutableListOf<Event>()
        repeat(3) { index ->
            val subscriber = object : EventSubscriber {
                override val id = "sub-$index"
                override fun onEvent(event: Event): EventResult {
                    received.add(event)
                    return EventResult.Success(id)
                }
            }
            eventBus.subscribe("TaskStarted", subscriber)
        }

        val event = Event.TaskStarted(taskId = "task-1")
        val results = eventBus.publish(event)

        assertEquals(3, results.size)
        assertEquals(3, received.size)
    }

    @Test
    fun `unsubscribe removes subscriber`() = runTest(testDispatcher) {
        var callCount = 0
        val subscriber = object : EventSubscriber {
            override val id = "sub-remove"
            override fun onEvent(event: Event): EventResult {
                callCount++
                return EventResult.Success(id)
            }
        }

        val subscription = eventBus.subscribe("ConfigUpdated", subscriber)
        eventBus.unsubscribe(subscription)

        eventBus.publish(Event.ConfigUpdated(config = com.loy.mingclaw.core.model.KernelConfig()))
        assertEquals(0, callCount)
    }

    @Test
    fun `publish returns empty results when no subscribers`() = runTest(testDispatcher) {
        val event = Event.PluginUnloaded(pluginId = "plugin-1")
        val results = eventBus.publish(event)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `subscriber failure returns Failed result`() = runTest(testDispatcher) {
        val subscriber = object : EventSubscriber {
            override val id = "failing-sub"
            override fun onEvent(event: Event): EventResult {
                throw RuntimeException("boom")
            }
        }

        eventBus.subscribe("PluginError", subscriber)
        val results = eventBus.publish(Event.PluginError(pluginId = "p1", error = "err"))

        assertEquals(1, results.size)
        assertTrue(results[0] is EventResult.Failed)
        assertEquals("failing-sub", (results[0] as EventResult.Failed).subscriberId)
    }

    @Test
    fun `shutdown prevents new subscriptions`() = runTest(testDispatcher) {
        eventBus.shutdown()
        val subscriber = object : EventSubscriber {
            override val id = "late-sub"
            override fun onEvent(event: Event): EventResult = EventResult.Success(id)
        }

        val subscription = eventBus.subscribe("PluginLoaded", subscriber)
        val results = eventBus.publish(Event.PluginLoaded(pluginId = "p"))
        assertTrue(results.isEmpty())
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:kernel:test --tests EventBusImplTest`
Expected: `FAIL`

- [ ] **Step 3: 创建 core/kernel/build.gradle.kts**

```kotlin
plugins {
    id("mingclaw.android.library")
    id("mingclaw.hilt")
    id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 4: 创建 EventBus 接口**

`core/kernel/src/main/java/com/loy/mingclaw/core/kernel/EventBus.kt`:

```kotlin
package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.Subscription
import com.loy.mingclaw.core.model.common.EventSubscriber
import kotlinx.coroutines.Job

interface EventBus {
    fun subscribe(eventType: String, subscriber: EventSubscriber): Subscription
    fun unsubscribe(subscription: Subscription)
    fun publish(event: Event): List<EventResult>
    fun publishAsync(event: Event): Job
    suspend fun shutdown()
}
```

- [ ] **Step 5: 创建 EventBusImpl**

`core/kernel/src/main/java/com/loy/mingclaw/core/kernel/internal/EventBusImpl.kt`:

```kotlin
package com.loy.mingclaw.core.kernel.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.kernel.EventBus
import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.EventSubscriber
import com.loy.mingclaw.core.model.common.Subscription
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EventBusImpl @Inject constructor(
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : EventBus {

    private val subscribers = ConcurrentHashMap<String, MutableList<EventSubscriber>>()
    @Volatile
    private var isShutdown = false
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    override fun subscribe(eventType: String, subscriber: EventSubscriber): Subscription {
        if (isShutdown) return Subscription(id = "", eventType = eventType)
        subscribers.computeIfAbsent(eventType) { mutableListOf() }.add(subscriber)
        return Subscription(id = UUID.randomUUID().toString(), eventType = eventType)
    }

    override fun unsubscribe(subscription: Subscription) {
        subscribers[subscription.eventType]?.let { list ->
            list.removeAll { it.id == subscription.id }
        }
    }

    override fun publish(event: Event): List<EventResult> {
        if (isShutdown) return emptyList()
        val eventType = event::class.simpleName ?: return emptyList()
        val eventSubscribers = subscribers[eventType] ?: return emptyList()

        return eventSubscribers.map { subscriber ->
            try {
                subscriber.onEvent(event)
            } catch (e: Exception) {
                EventResult.Failed(subscriberId = subscriber.id, error = e)
            }
        }
    }

    override fun publishAsync(event: Event): Job {
        return scope.launch { publish(event) }
    }

    override suspend fun shutdown() {
        isShutdown = true
        subscribers.clear()
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `./gradlew :core:kernel:test --tests EventBusImplTest`
Expected: `BUILD SUCCESSFUL`，所有 6 个测试通过

- [ ] **Step 7: Commit**

```bash
git add core/kernel/
git commit -m "feat: add EventBus interface and implementation with subscriber management"
```

---

### Task 6: 创建 ConfigManager 接口与实现

**Files:**
- Create: `core/kernel/src/main/java/com/loy/mingclaw/core/kernel/ConfigManager.kt`
- Create: `core/kernel/src/main/java/com/loy/mingclaw/core/kernel/internal/DefaultKernelConfig.kt`
- Create: `core/kernel/src/main/java/com/loy/mingclaw/core/kernel/internal/ConfigManagerImpl.kt`
- Test: `core/kernel/src/test/java/com/loy/mingclaw/core/kernel/ConfigManagerImplTest.kt`

- [ ] **Step 1: 写 ConfigManager 失败测试**

`core/kernel/src/test/java/com/loy/mingclaw/core/kernel/ConfigManagerImplTest.kt`:

```kotlin
package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.kernel.internal.ConfigManagerImpl
import com.loy.mingclaw.core.kernel.internal.DefaultKernelConfigProvider
import com.loy.mingclaw.core.model.KernelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigManagerImplTest {

    private lateinit var configManager: ConfigManagerImpl
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        configManager = ConfigManagerImpl(
            defaultConfigProvider = DefaultKernelConfigProvider(),
            dispatcher = testDispatcher,
        )
    }

    @Test
    fun `getConfig returns default config initially`() {
        val config = configManager.getConfig()
        val defaultConfig = KernelConfig()
        assertEquals(defaultConfig.maxTokens, config.maxTokens)
        assertEquals(defaultConfig.modelConfig.modelName, config.modelConfig.modelName)
    }

    @Test
    fun `updateConfig applies partial updates`() {
        val result = configManager.updateConfig(
            updates = mapOf("maxTokens" to 16384),
        )
        val updated = result.getOrNull()
        assertEquals(16384, updated?.maxTokens)
    }

    @Test
    fun `updateConfig rejects invalid maxTokens`() {
        val result = configManager.updateConfig(
            updates = mapOf("maxTokens" to -1),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `updateConfig rejects blank modelName`() {
        val result = configManager.updateConfig(
            updates = mapOf("modelName" to ""),
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `resetToDefault restores defaults`() {
        configManager.updateConfig(mapOf("maxTokens" to 16384))
        configManager.resetToDefault()
        val config = configManager.getConfig()
        assertEquals(KernelConfig().maxTokens, config.maxTokens)
    }

    @Test
    fun `watchConfigChanges emits on update`() = runTest(testDispatcher) {
        var emittedConfig: KernelConfig? = null
        val job = CoroutineScope(testDispatcher).launch {
            emittedConfig = configManager.watchConfigChanges().first()
        }

        configManager.updateConfig(mapOf("maxTokens" to 4096))
        job.join()

        assertEquals(4096, emittedConfig?.maxTokens)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:kernel:test --tests ConfigManagerImplTest`
Expected: `FAIL`

- [ ] **Step 3: 创建 ConfigManager 接口**

`core/kernel/src/main/java/com/loy/mingclaw/core/kernel/ConfigManager.kt`:

```kotlin
package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.model.KernelConfig
import kotlinx.coroutines.flow.Flow

interface ConfigManager {
    fun getConfig(): KernelConfig
    fun updateConfig(updates: Map<String, Any>): Result<KernelConfig>
    fun resetToDefault(): KernelConfig
    fun watchConfigChanges(): Flow<KernelConfig>
}
```

- [ ] **Step 4: 创建 DefaultKernelConfigProvider**

`core/kernel/src/main/java/com/loy/mingclaw/core/kernel/internal/DefaultKernelConfigProvider.kt`:

```kotlin
package com.loy.mingclaw.core.kernel.internal

import com.loy.mingclaw.core.model.KernelConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultKernelConfigProvider @Inject constructor() {
    fun provide(): KernelConfig = KernelConfig()
}
```

- [ ] **Step 5: 创建 ConfigManagerImpl**

`core/kernel/src/main/java/com/loy/mingclaw/core/kernel/internal/ConfigManagerImpl.kt`:

```kotlin
package com.loy.mingclaw.core.kernel.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.model.KernelConfig
import com.loy.mingclaw.core.model.ModelConfig
import com.loy.mingclaw.core.model.PluginConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ConfigManagerImpl @Inject constructor(
    private val defaultConfigProvider: DefaultKernelConfigProvider,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : ConfigManager {

    private val configRef = AtomicReference(defaultConfigProvider.provide())
    private val _configChanges = MutableSharedFlow<KernelConfig>(replay = 1)

    override fun getConfig(): KernelConfig = configRef.get()

    override fun updateConfig(updates: Map<String, Any>): Result<KernelConfig> {
        val current = configRef.get()
        val newConfig = applyUpdates(current, updates)
            ?: return Result.failure(IllegalArgumentException("Invalid config updates"))

        configRef.set(newConfig)
        _configChanges.tryEmit(newConfig)
        return Result.success(newConfig)
    }

    override fun resetToDefault(): KernelConfig {
        val default = defaultConfigProvider.provide()
        configRef.set(default)
        _configChanges.tryEmit(default)
        return default
    }

    override fun watchConfigChanges(): Flow<KernelConfig> = _configChanges.asSharedFlow()

    private fun applyUpdates(
        current: KernelConfig,
        updates: Map<String, Any>,
    ): KernelConfig? {
        var config = current

        updates.forEach { (key, value) ->
            config = when (key) {
                "maxTokens" -> {
                    val tokens = (value as? Number)?.toInt() ?: return null
                    if (tokens <= 0) return null
                    config.copy(maxTokens = tokens)
                }
                "modelName" -> {
                    val name = value as? String ?: return null
                    if (name.isBlank()) return null
                    config.copy(
                        modelConfig = config.modelConfig.copy(modelName = name)
                    )
                }
                "temperature" -> {
                    val temp = (value as? Number)?.toDouble() ?: return null
                    if (temp < 0 || temp > 2) return null
                    config.copy(
                        modelConfig = config.modelConfig.copy(temperature = temp)
                    )
                }
                else -> return null
            }
        }
        return config
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `./gradlew :core:kernel:test --tests ConfigManagerImplTest`
Expected: `BUILD SUCCESSFUL`，所有 6 个测试通过

- [ ] **Step 7: Commit**

```bash
git add core/kernel/src/main/java/com/loy/mingclaw/core/kernel/ConfigManager.kt core/kernel/src/main/java/com/loy/mingclaw/core/kernel/internal/DefaultKernelConfig.kt core/kernel/src/main/java/com/loy/mingclaw/core/kernel/internal/ConfigManagerImpl.kt core/kernel/src/test/java/com/loy/mingclaw/core/kernel/ConfigManagerImplTest.kt
git commit -m "feat: add ConfigManager with in-memory config and reactive updates"
```

---

### Task 7: 创建 MingClawKernel 接口与实现

**Files:**
- Create: `core/kernel/src/main/java/com/loy/mingclaw/core/kernel/MingClawKernel.kt`
- Create: `core/kernel/src/main/java/com/loy/mingclaw/core/kernel/internal/MingClawKernelImpl.kt`
- Create: `core/kernel/src/main/java/com/loy/mingclaw/core/kernel/di/KernelModule.kt`
- Test: `core/kernel/src/test/java/com/loy/mingclaw/core/kernel/MingClawKernelImplTest.kt`

- [ ] **Step 1: 写 MingClawKernel 失败测试**

`core/kernel/src/test/java/com/loy/mingclaw/core/kernel/MingClawKernelImplTest.kt`:

```kotlin
package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.kernel.internal.ConfigManagerImpl
import com.loy.mingclaw.core.kernel.internal.DefaultKernelConfigProvider
import com.loy.mingclaw.core.kernel.internal.EventBusImpl
import com.loy.mingclaw.core.kernel.internal.MingClawKernelImpl
import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.KernelConfig
import com.loy.mingclaw.core.model.SystemStatus
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.EventSubscriber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MingClawKernelImplTest {

    private lateinit var kernel: MingClawKernelImpl
    private lateinit var eventBus: EventBusImpl
    private lateinit var configManager: ConfigManagerImpl
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        eventBus = EventBusImpl(testDispatcher)
        configManager = ConfigManagerImpl(DefaultKernelConfigProvider(), testDispatcher)
        kernel = MingClawKernelImpl(eventBus, configManager, testDispatcher)
    }

    @Test
    fun `getSystemStatus returns initial state`() = runTest(testDispatcher) {
        val status = kernel.getSystemStatus()
        assertFalse(status.isRunning)
        assertEquals(0, status.loadedPluginCount)
        assertEquals(0, status.activeTaskCount)
    }

    @Test
    fun `getConfig returns default config`() {
        val config = kernel.getConfig()
        val defaultConfig = KernelConfig()
        assertEquals(defaultConfig.maxTokens, config.maxTokens)
    }

    @Test
    fun `updateConfig delegates to config manager`() {
        val result = kernel.updateConfig(mapOf("maxTokens" to 16384))
        assertEquals(16384, result.getOrNull()?.maxTokens)
    }

    @Test
    fun `subscribe and publish work through kernel`() = runTest(testDispatcher) {
        var receivedEvent: Event? = null
        val subscriber = object : EventSubscriber {
            override val id = "test-sub"
            override fun onEvent(event: Event): EventResult {
                receivedEvent = event
                return EventResult.Success(id)
            }
        }

        kernel.subscribe("PluginLoaded", subscriber)
        val results = kernel.publish(Event.PluginLoaded(pluginId = "p1"))
        assertEquals(1, results.size)
        assertEquals("p1", (receivedEvent as Event.PluginLoaded).pluginId)
    }

    @Test
    fun `shutdown updates system status`() = runTest(testDispatcher) {
        kernel.shutdown()
        val status = kernel.getSystemStatus()
        assertFalse(status.isRunning)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:kernel:test --tests MingClawKernelImplTest`
Expected: `FAIL`

- [ ] **Step 3: 创建 MingClawKernel 接口**

`core/kernel/src/main/java/com/loy/mingclaw/core/kernel/MingClawKernel.kt`:

```kotlin
package com.loy.mingclaw.core.kernel

import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.KernelConfig
import com.loy.mingclaw.core.model.SystemStatus
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.EventSubscriber
import com.loy.mingclaw.core.model.common.Subscription
import kotlinx.coroutines.flow.Flow

interface MingClawKernel {
    fun getSystemStatus(): SystemStatus
    fun getConfig(): KernelConfig
    fun updateConfig(updates: Map<String, Any>): Result<KernelConfig>
    fun subscribe(eventType: String, subscriber: EventSubscriber): Subscription
    fun publish(event: Event): List<EventResult>
    suspend fun shutdown()
}
```

- [ ] **Step 4: 创建 MingClawKernelImpl**

`core/kernel/src/main/java/com/loy/mingclaw/core/kernel/internal/MingClawKernelImpl.kt`:

```kotlin
package com.loy.mingclaw.core.kernel.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.kernel.EventBus
import com.loy.mingclaw.core.kernel.MingClawKernel
import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.KernelConfig
import com.loy.mingclaw.core.model.PluginState
import com.loy.mingclaw.core.model.SystemStatus
import com.loy.mingclaw.core.model.common.EventResult
import com.loy.mingclaw.core.model.common.EventSubscriber
import com.loy.mingclaw.core.model.common.Subscription
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MingClawKernelImpl @Inject constructor(
    private val eventBus: EventBus,
    private val configManager: ConfigManager,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : MingClawKernel {

    private val isRunning = AtomicBoolean(false)
    private val systemStatus = AtomicReference(SystemStatus())

    override fun getSystemStatus(): SystemStatus = systemStatus.get()

    override fun getConfig(): KernelConfig = configManager.getConfig()

    override fun updateConfig(updates: Map<String, Any>): Result<KernelConfig> {
        return configManager.updateConfig(updates)
    }

    override fun subscribe(eventType: String, subscriber: EventSubscriber): Subscription {
        return eventBus.subscribe(eventType, subscriber)
    }

    override fun publish(event: Event): List<EventResult> {
        return eventBus.publish(event)
    }

    override suspend fun shutdown() {
        isRunning.set(false)
        eventBus.shutdown()
        systemStatus.set(SystemStatus())
    }
}
```

- [ ] **Step 5: 创建 Hilt DI 模块**

`core/kernel/src/main/java/com/loy/mingclaw/core/kernel/di/KernelModule.kt`:

```kotlin
package com.loy.mingclaw.core.kernel.di

import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.kernel.EventBus
import com.loy.mingclaw.core.kernel.MingClawKernel
import com.loy.mingclaw.core.kernel.internal.ConfigManagerImpl
import com.loy.mingclaw.core.kernel.internal.EventBusImpl
import com.loy.mingclaw.core.kernel.internal.MingClawKernelImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KernelModule {

    @Binds
    @Singleton
    abstract fun bindEventBus(impl: EventBusImpl): EventBus

    @Binds
    @Singleton
    abstract fun bindConfigManager(impl: ConfigManagerImpl): ConfigManager

    @Binds
    @Singleton
    abstract fun bindMingClawKernel(impl: MingClawKernelImpl): MingClawKernel
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `./gradlew :core:kernel:test --tests MingClawKernelImplTest`
Expected: `BUILD SUCCESSFUL`，所有 5 个测试通过

- [ ] **Step 7: 运行全部 core:kernel 测试**

Run: `./gradlew :core:kernel:test`
Expected: `BUILD SUCCESSFUL`，所有测试通过（EventBus 6 + ConfigManager 6 + Kernel 5 = 17）

- [ ] **Step 8: Commit**

```bash
git add core/kernel/src/main/java/com/loy/mingclaw/core/kernel/MingClawKernel.kt core/kernel/src/main/java/com/loy/mingclaw/core/kernel/internal/MingClawKernelImpl.kt core/kernel/src/main/java/com/loy/mingclaw/core/kernel/di/KernelModule.kt core/kernel/src/test/java/com/loy/mingclaw/core/kernel/MingClawKernelImplTest.kt
git commit -m "feat: add MingClawKernel microkernel interface, implementation, and DI module"
```

---

### Task 8: 配置 Application 模块

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/loy/mingclaw/MingClawApplication.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `settings.gradle.kts` (修正 rootProject.name，已在 Task 2 完成)

- [ ] **Step 1: 更新 app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("mingclaw.hilt")
}

android {
    namespace = "com.loy.mingclaw"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.loy.mingclaw"
        minSdk = 32
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:kernel"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

- [ ] **Step 2: 创建 MingClawApplication.kt**

`app/src/main/java/com/loy/mingclaw/MingClawApplication.kt`:

```kotlin
package com.loy.mingclaw

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MingClawApplication : Application()
```

- [ ] **Step 3: 更新 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <application
        android:name=".MingClawApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyApplication" />

</manifest>
```

- [ ] **Step 4: 更新 strings.xml**

```xml
<resources>
    <string name="app_name">MingClaw</string>
</resources>
```

- [ ] **Step 5: 运行全量构建验证**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 运行全部测试**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/loy/mingclaw/MingClawApplication.kt app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml
git commit -m "feat: configure app module with Hilt Application and core module dependencies"
```

---

### Task 9: 全量验证与最终清理

**Files:**
- 全项目

- [ ] **Step 1: 运行全部单元测试**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 运行 lint 检查**

Run: `./gradlew lintDebug`
Expected: `BUILD SUCCESSFUL`（可能有 lint warnings 但不应有 errors）

- [ ] **Step 3: 构建 Debug APK**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`，APK 生成在 `app/build/outputs/apk/debug/`

- [ ] **Step 4: 验证模块结构**

Run: `./gradlew projects`
Expected: 输出包含以下模块：
```
:app
:core:model
:core:common
:core:kernel
```

- [ ] **Step 5: Final commit (如有未提交的更改)**

```bash
git status
# 如有未跟踪或修改的文件，提交它们
git add -A
git commit -m "chore: clean up and verify full project build"
```
