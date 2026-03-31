# MingClaw 整体架构设计

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [架构概述](#架构概述)
2. [架构分层](#架构分层)
3. [核心组件](#核心组件)
4. [数据流](#数据流)
5. [模块关系](#模块关系)
6. [技术选型](#技术选型)
7. [部署架构](#部署架构)

---

## 架构概述

### 设计目标

MingClaw 采用**插件化微内核架构**，实现以下目标：

| 目标 | 说明 |
|------|------|
| **可扩展性** | 通过插件系统动态添加能力，无需修改核心代码 |
| **可进化性** | 三路径进化机制（行为/知识/能力）实现自我改进 |
| **可维护性** | 清晰的模块边界和职责分离 |
| **可测试性** | 每个模块可独立测试 |
| **高性能** | 离线优先架构，本地处理优先 |

### 架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Self-Evolution Layer (自我进化层)                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                       Evolution Engine                                │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐│  │
│  │  │   Behavior   │ │   Knowledge  │ │  Capability  │ │   Feedback   ││  │
│  │  │  Evolver     │ │  Evolver     │ │   Evolver    │ │   Collector  ││  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘│  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                    │                                         │
│  ┌─────────────────────────────────▼─────────────────────────────────────┐  │
│  │                      Evolution Store (进化存储)                        │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐│  │
│  │  │  AGENTS.md   │ │  MEMORY.md   │ │  SKILLS/     │ │  EXPERIENCE/ ││  │
│  │  │ (行为规则)   │ │ (长期记忆)   │ │  (技能包)    │ │  (经验数据)  ││  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘│  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Application Layer (应用层)                           │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐     │
│  │   Chat UI    │ │ Task Monitor │ │Plugin Manager│ │  Settings    │     │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Core Layer (核心层)                                │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │                        Microkernel                                   │  │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐│  │
│  │  │   Plugin     │ │    Event     │ │    Task      │ │   Config     ││  │
│  │  │  Registry    │ │    Bus       │ │ Dispatcher   │ │  Manager     ││  │
│  │  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘│  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│                                    │                                         │
│  ┌─────────────────────────────────▼─────────────────────────────────────┐  │
│  │                     Core Services                                     │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │  │
│  │  │ Context  │ │  Memory  │ │ Session  │ │  LLM     │ │ Security │  │  │
│  │  │ Manager  │ │ Manager  │ │ Manager  │ │ Service  │ │ Service  │  │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘  │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Data Layer (数据层)                                 │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐     │
│  │  Room DB     │ │  DataStore   │ │  File System │ │  SharedPref  │     │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 架构分层

### 第一层：自我进化层

**职责**: 负责系统的自我进化能力

| 组件 | 职责 |
|------|------|
| **Behavior Evolver** | 分析用户反馈，优化行为规则 |
| **Knowledge Evolver** | 提取对话中的知识，更新长期记忆 |
| **Capability Evolver** | 识别能力缺口，自动安装技能 |
| **Feedback Collector** | 收集显式和隐式反馈 |
| **Evolution Store** | 存储进化相关的所有数据 |

### 第二层：应用层

**职责**: 用户界面和交互

| 组件 | 职责 |
|------|------|
| **Chat UI** | 对话界面 |
| **Task Monitor** | 任务监控界面 |
| **Plugin Manager** | 插件管理界面 |
| **Settings** | 设置界面 |

### 第三层：核心层

**职责**: 核心业务逻辑和服务

#### 3.1 微内核

```kotlin
/**
 * 微内核核心接口
 */
interface MingClawKernel {
    /**
     * 加载插件
     */
    suspend fun loadPlugin(pluginId: String): Result<PluginContext>

    /**
     * 卸载插件
     */
    suspend fun unloadPlugin(pluginId: String): Result<Unit>

    /**
     * 获取已加载的插件列表
     */
    fun getLoadedPlugins(): List<PluginInfo>

    /**
     * 分发任务到合适的处理器
     */
    suspend fun dispatchTask(task: AgentTask): TaskResult

    /**
     * 调度定期任务
     */
    fun scheduleRecurringTask(task: ScheduledTask): CancellableTask

    /**
     * 订阅事件
     */
    fun subscribe(eventType: String, handler: EventHandler): Subscription

    /**
     * 发布事件
     */
    fun publish(event: Event): List<EventResult>

    /**
     * 获取配置
     */
    fun getConfig(): KernelConfig

    /**
     * 更新配置
     */
    fun updateConfig(updates: ConfigUpdates): Result<Unit>
}
```

#### 3.2 核心服务

| 服务 | 接口 | 职责 |
|------|------|------|
| **Context Manager** | `SessionContextManager` | 管理会话上下文 |
| **Memory Manager** | `MemoryContextManager` | 管理记忆检索 |
| **Session Manager** | `SessionRepository` | 管理会话数据 |
| **LLM Service** | `LlmApiClient` | 调用大语言模型 |
| **Security Service** | `SecurityManager` | 安全相关操作 |

### 第四层：数据层

**职责**: 数据持久化和存储

| 组件 | 用途 | 技术选型 |
|------|------|----------|
| **Room DB** | 结构化数据存储 | Room + SQLite |
| **DataStore** | 键值对存储 | Proto DataStore |
| **File System** | 文件存储 | Android File API |
| **SharedPref** | 简单配置 | SharedPreferences (向后兼容) |

---

## 核心组件

### 1. 微内核 (Microkernel)

微内核是系统的核心，负责：

```kotlin
/**
 * 微内核实现类
 */
internal class MingClawKernelImpl @Inject constructor(
    private val pluginRegistry: PluginRegistry,
    private val eventBus: EventBus,
    private val taskDispatcher: TaskDispatcher,
    private val configManager: KernelConfigManager,
    private val securityManager: SecurityManager
) : MingClawKernel {

    private val loadedPlugins = mutableMapOf<String, PluginContext>()
    private val subscriptions = mutableMapOf<String, Subscription>()

    override suspend fun loadPlugin(pluginId: String): Result<PluginContext> {
        // 1. 检查权限
        if (!securityManager.checkPluginPermission(pluginId)) {
            return Result.failure(SecurityException("Plugin permission denied"))
        }

        // 2. 加载插件
        val plugin = pluginRegistry.loadPlugin(pluginId)
            ?: return Result.failure(PluginNotFoundException(pluginId))

        // 3. 初始化插件
        val context = PluginContext(pluginId, kernel = this)
        val initResult = plugin.onInitialize(context)

        return initResult.map {
            loadedPlugins[pluginId] = context
            plugin.onStart()
            eventBus.publish(Event.PluginLoaded(pluginId))
            context
        }
    }

    override suspend fun dispatchTask(task: AgentTask): TaskResult {
        // 1. 验证任务
        val validationResult = validateTask(task)
        if (!validationResult.isValid) {
            return TaskResult.Failure(validationResult.error)
        }

        // 2. 查找处理器
        val handler = taskDispatcher.findHandler(task.type)
            ?: return TaskResult.Failure("No handler for task type: ${task.type}")

        // 3. 执行任务
        return try {
            handler.execute(task)
        } catch (e: Exception) {
            TaskResult.Failure(e.message ?: "Unknown error")
        }
    }

    // ... 其他方法实现
}
```

### 2. 事件总线 (Event Bus)

```kotlin
/**
 * 事件总线接口
 */
interface EventBus {
    fun subscribe(eventType: String, subscriber: EventSubscriber): Subscription
    fun publish(event: Event): List<EventResult>
    fun publishAsync(event: Event): Job
}

/**
 * 事件总线实现
 */
internal class EventBusImpl @Inject constructor(
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : EventBus {

    private val subscribers = mutableMapOf<String, MutableList<EventSubscriber>>()
    private val mutex = Mutex()

    override fun subscribe(
        eventType: String,
        subscriber: EventSubscriber
    ): Subscription {
        runBlocking {
            mutex.withLock {
                subscribers.getOrPut(eventType) { mutableListOf() }.add(subscriber)
            }
        }

        return Subscription(
            id = UUID.randomUUID().toString(),
            unsubscribe = {
                runBlocking {
                    mutex.withLock {
                        subscribers[eventType]?.remove(subscriber)
                    }
                }
            }
        )
    }

    override fun publish(event: Event): List<EventResult> {
        val eventType = event::class.simpleName ?: return emptyList()
        val eventSubscribers = subscribers[eventType] ?: return emptyList()

        return eventSubscribers.map { subscriber ->
            try {
                subscriber.onEvent(event)
                EventResult.Success(subscriber.id)
            } catch (e: Exception) {
                EventResult.Failure(subscriber.id, e.message)
            }
        }
    }
}
```

### 3. 插件注册表 (Plugin Registry)

```kotlin
/**
 * 插件注册表
 */
interface PluginRegistry {
    fun registerPlugin(plugin: MingClawPlugin): Result<Unit>
    fun unregisterPlugin(pluginId: String): Result<Unit>
    fun getPlugin(pluginId: String): MingClawPlugin?
    fun getAllPlugins(): List<MingClawPlugin>
    fun loadPlugin(pluginId: String): MingClawPlugin?
}

/**
 * 插件注册表实现
 */
internal class PluginRegistryImpl @Inject constructor(
    private val pluginLoader: PluginLoader,
    private val securityManager: SecurityManager
) : PluginRegistry {

    private val plugins = mutableMapOf<String, MingClawPlugin>()
    private val pluginMetadata = mutableMapOf<String, PluginMetadata>()

    override fun registerPlugin(plugin: MingClawPlugin): Result<Unit> {
        val pluginId = plugin.pluginId

        // 1. 验证插件
        val validation = validatePlugin(plugin)
        if (!validation.isValid) {
            return Result.failure(PluginValidationException(validation.error))
        }

        // 2. 检查安全
        if (!securityManager.isPluginSafe(plugin)) {
            return Result.failure(SecurityException("Plugin security check failed"))
        }

        // 3. 注册插件
        plugins[pluginId] = plugin
        pluginMetadata[pluginId] = PluginMetadata(
            pluginId = pluginId,
            version = plugin.version,
            name = plugin.name,
            description = plugin.description,
            author = plugin.author
        )

        return Result.success(Unit)
    }

    private fun validatePlugin(plugin: MingClawPlugin): ValidationResult {
        // 验证插件ID格式
        if (!plugin.pluginId.matches(Regex("[a-z0-9_]+"))) {
            return ValidationResult(false, "Invalid plugin ID format")
        }

        // 验证版本
        if (plugin.version == Version.UNKNOWN) {
            return ValidationResult(false, "Plugin version is required")
        }

        // 验证依赖
        for (dependency in plugin.getDependencies()) {
            if (!plugins.containsKey(dependency.pluginId)) {
                return ValidationResult(false, "Missing dependency: ${dependency.pluginId}")
            }
        }

        return ValidationResult(true)
    }
}
```

---

## 数据流

### 用户请求处理流程

```
┌──────────────┐
│   用户输入    │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│   Chat UI    │
└──────┬───────┘
       │
       ▼
┌──────────────────────────┐
│   SessionContextManager  │
│   - 添加消息到会话        │
│   - 估算Token使用         │
│   - 检查是否需要压缩       │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│   MemoryContextManager   │
│   - 检索相关记忆          │
│   - 获取永久记忆          │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│   ContextWindowManager   │
│   - 分配Token预算         │
│   - 构建完整上下文         │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│      LLM Service         │
│   - 调用大语言模型        │
│   - 流式返回响应          │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│   Task Orchestrator      │
│   - 解析响应内容          │
│   - 执行工具调用          │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│    Plugin Registry       │
│   - 路由到对应插件        │
│   - 执行具体操作          │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│      Event Bus           │
│   - 发布执行结果事件      │
│   - 通知其他组件          │
└──────────────────────────┘
```

### 进化触发流程

```
┌──────────────────┐
│  反馈收集触发器    │
│  - 用户反馈        │
│  - 任务失败        │
│  - 性能下降        │
│  - 定期检查        │
└─────┬────────────┘
      │
      ▼
┌──────────────────┐
│ EvolutionTrigger │
│    Manager       │
│  - 评估触发条件    │
└─────┬────────────┘
      │
      ▼
┌──────────────────┐
│  Evolution       │
│    Analyzer      │
│  - 分析当前状态    │
│  - 生成进化建议    │
└─────┬────────────┘
      │
      ▼
┌─────────────────────────────────────┐
│        三种进化路径                  │
├─────────────────────────────────────┤
│ 1. BehaviorEvolver                  │
│    - 分析行为规则                    │
│    - 生成规则更新                    │
│    - 更新 AGENTS.md                  │
├─────────────────────────────────────┤
│ 2. KnowledgeEvolver                 │
│    - 提取知识点                      │
│    - 评估重要性                      │
│    - 合并到 MEMORY.md                │
├─────────────────────────────────────┤
│ 3. CapabilityEvolver                │
│    - 识别能力缺口                    │
│    - 搜索可用技能                    │
│    - 安装新技能                      │
└─────────────────────────────────────┘
      │
      ▼
┌──────────────────┐
│ DynamicPrompt    │
│    Builder       │
│  - 重新加载配置    │
│  - 下次会话生效    │
└──────────────────┘
```

---

## 模块关系

### 依赖关系图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              应用层 (app/)                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │   chat   │  │   task   │  │  plugin  │  │ settings │              │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘              │
└───────┼────────────┼────────────┼────────────┼─────────────────────────┘
        │            │            │            │
        ▼            ▼            ▼            ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            功能层 (feature/)                            │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐            │
│  │ chat:api       │  │ task:api       │  │ plugin:api     │            │
│  │ chat:impl      │  │ task:impl      │  │ plugin:impl    │            │
│  └────────┬───────┘  └────────┬───────┘  └────────┬───────┘            │
└───────────┼──────────────────┼──────────────────┼───────────────────────┘
            │                  │                  │
            ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            核心层 (core/)                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │  kernel  │  │evolution │  │ context  │  │  task    │  │ memory │ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └───┬────┘ │
│       │             │             │             │             │       │
│  ┌────▼─────┐  ┌───▼──────┐  ┌───▼──────┐  ┌───▼──────┐  ┌───▼─────┐│
│  │security  │  │workspace │  │  plugin  │  │ quality  │  │android  ││
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └─────────┘│
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          数据层 (core/data/)                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │ database │  │ network  │  │ datastore│  │  local   │               │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘               │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          模型层 (core/model/)                            │
│              (纯 Kotlin 数据类，无依赖)                                  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 模块间通信规则

| 规则 | 说明 |
|------|------|
| **单向依赖** | 上层可以依赖下层，下层不能依赖上层 |
| **事件通信** | 同层模块间通过事件总线通信 |
| **接口隔离** | 功能模块通过 api 模块暴露接口 |
| **依赖注入** | 使用 Hilt 进行依赖注入 |

---

## 技术选型

### 核心技术栈

| 层次 | 技术 | 版本 | 用途 |
|------|------|------|------|
| **UI** | Jetpack Compose | 1.7+ | 声明式UI |
| **架构** | MVVM + Clean Architecture | - | 应用架构 |
| **异步** | Kotlin Coroutines + Flow | 1.7+ | 异步处理 |
| **DI** | Hilt | 2.51+ | 依赖注入 |
| **数据库** | Room | 2.6+ | 本地数据库 |
| **网络** | Retrofit + OkHttp | 2.11+ | 网络请求 |
| **向量存储** | sqlite-vec | 0.1+ | 向量搜索 |

### 架构模式

| 模式 | 应用场景 |
|------|----------|
| **微内核架构** | 核心系统 + 插件 |
| **事件驱动架构** | 模块间通信 |
| **仓储模式** | 数据访问抽象 |
| **策略模式** | 算法可替换 |
| **观察者模式** | 响应式更新 |

---

## 部署架构

### 应用结构

```
MingClaw.apk
├── Application
│   ├── MingClawApp (Application入口)
│   └── MainActivity (主Activity)
│
├── Feature Modules (动态加载)
│   ├── chat
│   ├── task-monitor
│   ├── plugin-manager
│   └── settings
│
├── Core Modules (编译时集成)
│   ├── kernel
│   ├── evolution
│   ├── context
│   ├── task
│   ├── memory
│   ├── security
│   └── quality
│
└── Data Layer
    ├── Room Database
    ├── DataStore
    └── File System
```

### 运行时架构

```
┌────────────────────────────────────────┐
│         Android Application            │
│  ┌──────────────────────────────────┐  │
│  │      Main Process                │  │
│  │  ┌────────────────────────────┐  │  │
│  │  │   UI Thread (Main)         │  │  │
│  │  │   - Compose UI             │  │  │
│  │  │   - State Updates          │  │  │
│  │  └────────────────────────────┘  │  │
│  │                                  │  │
│  │  ┌────────────────────────────┐  │  │
│  │  │   Worker Threads           │  │  │
│  │  │   - Database I/O           │  │  │
│  │  │   - Network I/O            │  │  │
│  │  │   - File I/O               │  │  │
│  │  │   - Embedding Computation  │  │  │
│  │  └────────────────────────────┘  │  │
│  │                                  │  │
│  │  ┌────────────────────────────┐  │  │
│  │  │   Background Services       │  │  │
│  │  │   - Sync Service           │  │  │
│  │  │   - Indexing Service       │  │  │
│  │  │   - Evolution Check        │  │  │
│  │  └────────────────────────────┘  │  │
│  └──────────────────────────────────┘  │
└────────────────────────────────────────┘
```

---

## 附录

### A. 相关文档

- [02-evolution.md](./02-evolution.md) - 自我进化机制详细设计
- [03-core-modules.md](./03-core-modules.md) - 核心模块详细设计
- [05-plugin-system.md](./05-plugin-system.md) - 插件系统详细设计

### B. 架构决策记录

| 决策 | 原因 | 替代方案 |
|------|------|----------|
| 微内核架构 | 高扩展性 | 单体架构 |
| Room + sqlite-vec | 原生向量搜索 | 外部向量数据库 |
| 事件驱动通信 | 模块解耦 | 直接调用 |
| Markdown存储 | 可读性高 | JSON/YAML |

---

**文档维护**: 本文档应随着架构演进持续更新
**审查周期**: 每月一次或重大架构变更时
