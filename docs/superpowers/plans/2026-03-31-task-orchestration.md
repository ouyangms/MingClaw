# Task Orchestration (core:task) 实现计划

## 概述
实现任务编排引擎 `core:task`，提供任务分发、依赖解析、工作流执行和错误处理功能。

## MVP 范围
- TaskOrchestrator: 任务执行、取消、状态查询、事件流
- TaskDispatcher: 基于类型的任务路由
- DependencyResolver: 拓扑排序 + 循环检测
- ErrorHandler: 基本重试策略
- WorkflowEngine: 简化的线性工作流（Start -> Task -> End）
- ConcurrencyController: 信号量并发控制
- TaskHandler: 处理器接口

## 延迟实现
- 复杂的 DAG 工作流（Loop, Parallel, Condition 节点）
- 条件表达式引擎
- WorkflowStore 持久化
- 工作流暂停/恢复

---

## Task T1: 任务领域类型 (core:model)

在 `core/model/src/main/java/com/loy/mingclaw/core/model/task/` 下创建：

### TaskTypes.kt
- AgentTask (id, type, input, dependencies, metadata, priority, timeout)
- TaskPriority enum (Critical, High, Normal, Low)
- TaskResult sealed interface (Success, Failure, Partial)
- TaskStatus sealed interface (Pending, Running, Completed, Failed, Cancelled)
- TaskEvent sealed interface (Created, Started, Completed, Failed, Cancelled, Progress)

### WorkflowTypes.kt
- Workflow (id, name, description, nodes, edges, metadata)
- WorkflowNode (id, type, config, outgoingEdges)
- NodeType enum (Start, End, Task, Condition, Loop, Parallel, Wait, Input)
- WorkflowEdge (id, source, target, condition)
- WorkflowResult sealed interface (Success, Failure, Paused)
- WorkflowStatus enum

### DependencyTypes.kt
- DependencyGraph (nodes, edges)
- DependencyEdge (from, to)
- CycleDetectedException

### ErrorHandlerTypes.kt
- ErrorHandlingStrategy sealed interface (Retry, Fallback, Ignore, Fail)
- ErrorHandlingResult sealed interface (Ignored, Recovered, Failed)
- ErrorRecord

---

## Task T2: 模块基础设施
- core/task/build.gradle.kts (depends on :core:model, :core:common, :core:kernel)
- 接口定义: TaskOrchestrator, TaskDispatcher, DependencyResolver, ErrorHandler, WorkflowEngine, ConcurrencyController, TaskHandler
- settings.gradle.kts 添加 `include(":core:task")`

---

## Task T3: 实现 TaskDispatcher + DependencyResolver + ConcurrencyController
- TaskDispatcherImpl: 根据 task.type 路由到注册的 handler
- DependencyResolverImpl: 拓扑排序 + DFS 循环检测
- ConcurrencyControllerImpl: Semaphore 并发许可控制

---

## Task T4: 实现 ErrorHandler
- ErrorHandlerImpl: 错误记录、策略查找、重试执行

---

## Task T5: 实现 TaskOrchestrator
- TaskOrchestratorImpl: 任务生命周期管理、事件发布、并发控制

---

## Task T6: 实现 WorkflowEngine + DI
- WorkflowEngineImpl: 简化线性执行（Start -> nodes -> End）
- TaskModule: Hilt DI 绑定所有接口

---

## Task T7: 全量验证
