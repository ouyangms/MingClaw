# MingClaw 任务编排引擎设计

**文档版本**: 1.0
**创建日期**: 2025-03-31
**状态**: 设计中
**作者**: MingClaw Team

---

## 目录

1. [任务编排概述](#任务编排概述)
2. [任务执行](#任务执行)
3. [工作流定义](#工作流定义)
4. [依赖管理](#依赖管理)
5. [错误处理](#错误处理)
6. [依赖关系](#依赖关系)
7. [附录](#附录)

---

## 任务编排概述

### 设计目标

MingClaw 任务编排引擎实现：

| 目标 | 说明 | 实现方式 |
|------|------|----------|
| **灵活执行** | 支持多种任务类型 | 任务分发器 |
| **工作流编排** | 定义复杂任务流程 | DAG 工作流 |
| **依赖解析** | 自动处理任务依赖 | 拓扑排序 |
| **错误恢复** | 优雅处理失败 | 重试 + 回滚 |
| **并发控制** | 优化资源使用 | 并发限制器 |

### 架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Task Orchestration Layer                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                      Task Orchestrator                            │  │
│  │  - 协调任务执行                                                    │  │
│  │  - 管理工作流                                                      │  │
│  │  - 处理错误恢复                                                    │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                    │                                     │
│  ┌───────────────┬─────────────────┼─────────────────┬─────────────────┐ │
│  │               │                 │                 │                 │ │
│  ▼               ▼                 ▼                 ▼                 │ │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────┐ │
│  │   Task    │ │ Workflow  │ │Dependency │    Error   │ Concurrency│ │
│  │ Dispatcher│ │   Engine  │ │  Resolver │   Handler  │ Controller │ │
│  └───────────┘ └───────────┘ └───────────┘ └───────────┘ └───────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                          Task Handlers                                 │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐  │
│  │   LLM        │ │   Tool       │ │   Workflow   │ │    Custom    │  │
│  │   Handler    │ │   Handler    │ │   Handler    │ │   Handler    │  │
│  └──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 任务执行

### 核心接口

```kotlin
/**
 * 任务编排器接口
 *
 * 职责：
 * - 分发任务到合适的处理器
 * - 管理任务生命周期
 * - 处理任务依赖
 */
interface TaskOrchestrator {

    /**
     * 执行任务
     */
    suspend fun executeTask(task: AgentTask): TaskResult

    /**
     * 批量执行任务
     */
    suspend fun executeTasks(tasks: List<AgentTask>): List<TaskResult>

    /**
     * 执行工作流
     */
    suspend fun executeWorkflow(
        workflow: Workflow,
        input: Map<String, Any>
    ): WorkflowResult

    /**
     * 取消任务
     */
    suspend fun cancelTask(taskId: String): Result<Unit>

    /**
     * 获取任务状态
     */
    fun getTaskStatus(taskId: String): TaskStatus?

    /**
     * 监听任务事件
     */
    fun watchTaskEvents(taskId: String): Flow<TaskEvent>

    /**
     * 注册任务处理器
     */
    fun registerHandler(
        taskType: String,
        handler: TaskHandler
    ): Result<Unit>

    /**
     * 注销任务处理器
     */
    fun unregisterHandler(taskType: String): Result<Unit>
}
```

### 实现类

```kotlin
/**
 * 任务编排器实现
 */
@Singleton
internal class TaskOrchestratorImpl @Inject constructor(
    private val taskDispatcher: TaskDispatcher,
    private val workflowEngine: WorkflowEngine,
    private val errorHandler: ErrorHandler,
    private val eventBus: EventBus,
    private val concurrencyController: ConcurrencyController,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : TaskOrchestrator {

    // 任务存储
    private val tasks = mutableMapOf<String, AgentTask>()

    // 任务状态
    private val taskStatus = mutableMapOf<String, TaskStatus>()

    // 任务事件流
    private val taskEvents = mutableMapOf<String, MutableSharedFlow<TaskEvent>>()

    // 任务处理器
    private val handlers = mutableMapOf<String, TaskHandler>()

    // 任务作用域
    private val taskScope = CoroutineScope(
        SupervisorJob() +
        ioDispatcher +
        CoroutineName("TaskOrchestrator")
    )

    override suspend fun executeTask(task: AgentTask): TaskResult {
        return withContext(ioDispatcher) {
            // 1. 记录任务
            tasks[task.id] = task
            updateTaskStatus(task.id, TaskStatus.Pending)

            // 2. 发布任务创建事件
            eventBus.publish(Event.TaskCreated(task))
            emitTaskEvent(task.id, TaskEvent.Created(task))

            try {
                // 3. 检查并发限制
                val permit = concurrencyController.acquirePermit(task).await()

                // 4. 更新状态
                updateTaskStatus(task.id, TaskStatus.Running)
                emitTaskEvent(task.id, TaskEvent.Started(task.id))

                // 5. 分发任务
                val result = taskDispatcher.dispatch(task, handlers)

                // 6. 处理结果
                when (result) {
                    is TaskResult.Success -> {
                        updateTaskStatus(task.id, TaskStatus.Completed)
                        emitTaskEvent(task.id, TaskEvent.Completed(task.id, result.data))
                    }
                    is TaskResult.Failure -> {
                        updateTaskStatus(task.id, TaskStatus.Failed(result.error))
                        emitTaskEvent(task.id, TaskEvent.Failed(task.id, result.error))

                        // 尝试错误恢复
                        errorHandler.handleError(task, result)
                    }
                    is TaskResult.Partial -> {
                        updateTaskStatus(task.id, TaskStatus.Running)
                        emitTaskEvent(task.id, TaskEvent.Progress(task.id, result.progress))
                    }
                }

                // 7. 释放许可
                permit.release()

                result
            } catch (e: CancellationException) {
                updateTaskStatus(task.id, TaskStatus.Cancelled)
                emitTaskEvent(task.id, TaskEvent.Cancelled(task.id))
                TaskResult.Failure("Task cancelled: ${e.message}")
            } catch (e: Exception) {
                val error = "Task execution failed: ${e.message}"
                updateTaskStatus(task.id, TaskStatus.Failed(error))
                emitTaskEvent(task.id, TaskEvent.Failed(task.id, error))
                TaskResult.Failure(error)
            }
        }
    }

    override suspend fun executeTasks(
        tasks: List<AgentTask>
    ): List<TaskResult> = withContext(ioDispatcher) {
        // 解析任务依赖
        val sorted = resolveDependencies(tasks)

        // 按依赖顺序执行
        val results = mutableListOf<TaskResult>()
        val completed = mutableMapOf<String, TaskResult>()

        for (task in sorted) {
            // 检查依赖是否完成
            val dependenciesMet = task.dependencies.all { depId ->
                completed[depId]?.let { it is TaskResult.Success } ?: false
            }

            if (!dependenciesMet) {
                results.add(
                    TaskResult.Failure("Dependencies not met for task ${task.id}")
                )
                continue
            }

            // 执行任务
            val result = executeTask(task)
            results.add(result)
            completed[task.id] = result
        }

        results
    }

    override suspend fun executeWorkflow(
        workflow: Workflow,
        input: Map<String, Any>
    ): WorkflowResult = withContext(ioDispatcher) {
        try {
            // 发布工作流开始事件
            eventBus.publish(Event.WorkflowStarted(workflow.id))

            // 执行工作流
            val result = workflowEngine.execute(workflow, input)

            // 发布工作流完成事件
            when (result) {
                is WorkflowResult.Success -> {
                    eventBus.publish(Event.WorkflowCompleted(workflow.id))
                }
                is WorkflowResult.Failure -> {
                    eventBus.publish(Event.WorkflowFailed(workflow.id, result.error))
                }
            }

            result
        } catch (e: Exception) {
            val error = "Workflow execution failed: ${e.message}"
            eventBus.publish(Event.WorkflowFailed(workflow.id, error))
            WorkflowResult.Failure(error)
        }
    }

    override suspend fun cancelTask(taskId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val status = taskStatus[taskId]
                    ?: return@withContext Result.failure(
                        TaskNotFoundException(taskId)
                    )

                when (status) {
                    is TaskStatus.Running -> {
                        // 取消正在运行的任务
                        // 实现略
                        updateTaskStatus(taskId, TaskStatus.Cancelled)
                        emitTaskEvent(taskId, TaskEvent.Cancelled(taskId))
                        Result.success(Unit)
                    }
                    is TaskStatus.Pending -> {
                        // 取消等待中的任务
                        updateTaskStatus(taskId, TaskStatus.Cancelled)
                        emitTaskEvent(taskId, TaskEvent.Cancelled(taskId))
                        Result.success(Unit)
                    }
                    else -> {
                        Result.failure(
                            IllegalStateException("Cannot cancel task in status: $status")
                        )
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun getTaskStatus(taskId: String): TaskStatus? {
        return taskStatus[taskId]
    }

    override fun watchTaskEvents(taskId: String): Flow<TaskEvent> {
        return taskEvents.getOrPut(taskId) {
            MutableSharedFlow(
                replay = 50,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }.asSharedFlow()
    }

    override fun registerHandler(
        taskType: String,
        handler: TaskHandler
    ): Result<Unit> {
        return try {
            handlers[taskType] = handler
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun unregisterHandler(taskType: String): Result<Unit> {
        return try {
            handlers.remove(taskType)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 私有辅助方法
    private fun updateTaskStatus(taskId: String, status: TaskStatus) {
        taskStatus[taskId] = status
        eventBus.publish(Event.TaskStatusChanged(taskId, status))
    }

    private suspend fun emitTaskEvent(taskId: String, event: TaskEvent) {
        taskEvents.getOrPut(taskId) {
            MutableSharedFlow(
                replay = 50,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }.emit(event)
    }

    private fun resolveDependencies(
        tasks: List<AgentTask>
    ): List<AgentTask> {
        // 构建依赖图
        val graph = mutableMapOf<String, MutableList<String>>()
        val taskMap = tasks.associateBy { it.id }

        for (task in tasks) {
            graph[task.id] = task.dependencies.toMutableList()
        }

        // 拓扑排序
        val sorted = mutableListOf<AgentTask>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()

        fun visit(taskId: String) {
            if (taskId in visited) return
            if (taskId in visiting) {
                throw CycleDetectedException("Cycle detected in task dependencies")
            }

            visiting.add(taskId)

            graph[taskId]?.forEach { depId ->
                visit(depId)
            }

            visiting.remove(taskId)
            visited.add(taskId)

            taskMap[taskId]?.let { sorted.add(it) }
        }

        for (taskId in graph.keys) {
            visit(taskId)
        }

        return sorted
    }
}
```

---

## 工作流定义

### 核心接口

```kotlin
/**
 * 工作流引擎接口
 *
 * 职责：
 * - 执行工作流定义
 * - 管理工作流状态
 * - 处理工作流错误
 */
interface WorkflowEngine {

    /**
     * 执行工作流
     */
    suspend fun execute(
        workflow: Workflow,
        input: Map<String, Any>
    ): WorkflowResult

    /**
     * 验证工作流
     */
    fun validate(workflow: Workflow): ValidationResult

    /**
     * 获取工作流状态
     */
    fun getStatus(workflowId: String): WorkflowStatus?

    /**
     * 暂停工作流
     */
    suspend fun pause(workflowId: String): Result<Unit>

    /**
     * 恢复工作流
     */
    suspend fun resume(workflowId: String): Result<Unit>

    /**
     * 取消工作流
     */
    suspend fun cancel(workflowId: String): Result<Unit>
}
```

### 实现类

```kotlin
/**
 * 工作流引擎实现
 */
@Singleton
internal class WorkflowEngineImpl @Inject constructor(
    private val taskOrchestrator: TaskOrchestrator,
    private val workflowStore: WorkflowStore,
    private val eventBus: EventBus,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : WorkflowEngine {

    // 工作流状态
    private val workflowStates = mutableMapOf<String, WorkflowStatus>()

    override suspend fun execute(
        workflow: Workflow,
        input: Map<String, Any>
    ): WorkflowResult = withContext(ioDispatcher) {
        try {
            // 1. 验证工作流
            val validation = validate(workflow)
            if (!validation.isValid) {
                return@withContext WorkflowResult.Failure(
                    "Invalid workflow: ${validation.errors.joinToString()}"
                )
            }

            // 2. 初始化状态
            val state = WorkflowExecutionState(
                workflowId = workflow.id,
                input = input,
                variables = mutableMapOf(),
                completedNodes = mutableSetOf(),
                currentNode = null,
                status = WorkflowStatus.Running
            )

            workflowStates[workflow.id] = WorkflowStatus.Running

            // 3. 执行工作流
            val result = executeWorkflow(workflow, state)

            // 4. 更新状态
            workflowStates[workflow.id] = when (result) {
                is WorkflowResult.Success -> WorkflowStatus.Completed
                is WorkflowResult.Failure -> WorkflowStatus.Failed(result.error)
                is WorkflowResult.Paused -> WorkflowStatus.Paused
            }

            result
        } catch (e: Exception) {
            WorkflowResult.Failure("Workflow execution failed: ${e.message}")
        }
    }

    override fun validate(workflow: Workflow): ValidationResult {
        val errors = mutableListOf<String>()

        // 1. 验证工作流结构
        if (workflow.nodes.isEmpty()) {
            errors.add("Workflow must have at least one node")
        }

        // 2. 验证节点
        for (node in workflow.nodes) {
            // 验证节点 ID
            if (node.id.isBlank()) {
                errors.add("Node ID cannot be blank")
            }

            // 验证节点类型
            if (!isValidNodeType(node.type)) {
                errors.add("Invalid node type: ${node.type}")
            }

            // 验证边引用
            for (edgeId in node.outgoingEdges) {
                if (workflow.edges.none { it.id == edgeId }) {
                    errors.add("Edge $edgeId not found")
                }
            }
        }

        // 3. 验证边
        for (edge in workflow.edges) {
            // 验证源和目标节点
            if (workflow.nodes.none { it.id == edge.source }) {
                errors.add("Source node ${edge.source} not found for edge ${edge.id}")
            }
            if (workflow.nodes.none { it.id == edge.target }) {
                errors.add("Target node ${edge.target} not found for edge ${edge.id}")
            }

            // 验证条件
            if (edge.condition != null) {
                try {
                    // 验证条件表达式
                    validateExpression(edge.condition)
                } catch (e: Exception) {
                    errors.add("Invalid condition in edge ${edge.id}: ${e.message}")
                }
            }
        }

        // 4. 验证起始节点
        val startNodes = workflow.nodes.filter { it.type == NodeType.Start }
        if (startNodes.size != 1) {
            errors.add("Workflow must have exactly one start node")
        }

        // 5. 验证终止节点
        val endNodes = workflow.nodes.filter { it.type == NodeType.End }
        if (endNodes.isEmpty()) {
            errors.add("Workflow must have at least one end node")
        }

        // 6. 验证无循环
        if (hasCycle(workflow)) {
            errors.add("Workflow contains cycles")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    override fun getStatus(workflowId: String): WorkflowStatus? {
        return workflowStates[workflowId]
    }

    override suspend fun pause(workflowId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val state = workflowStore.getState(workflowId)
                    ?: return@withContext Result.failure(
                        WorkflowNotFoundException(workflowId)
                    )

                if (state.status != WorkflowStatus.Running) {
                    return@withContext Result.failure(
                        IllegalStateException("Workflow is not running")
                    )
                }

                workflowStore.updateState(
                    state.copy(status = WorkflowStatus.Paused)
                )

                eventBus.publish(Event.WorkflowPaused(workflowId))

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun resume(workflowId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val state = workflowStore.getState(workflowId)
                    ?: return@withContext Result.failure(
                        WorkflowNotFoundException(workflowId)
                    )

                if (state.status != WorkflowStatus.Paused) {
                    return@withContext Result.failure(
                        IllegalStateException("Workflow is not paused")
                    )
                }

                workflowStore.updateState(
                    state.copy(status = WorkflowStatus.Running)
                )

                eventBus.publish(Event.WorkflowResumed(workflowId))

                // 继续执行
                val workflow = workflowStore.getWorkflow(workflowId)
                execute(workflow, state.input)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun cancel(workflowId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val state = workflowStore.getState(workflowId)
                    ?: return@withContext Result.failure(
                        WorkflowNotFoundException(workflowId)
                    )

                if (state.status == WorkflowStatus.Completed ||
                    state.status == WorkflowStatus.Failed ||
                    state.status == WorkflowStatus.Cancelled
                ) {
                    return@withContext Result.failure(
                        IllegalStateException("Workflow already terminated")
                    )
                }

                workflowStore.updateState(
                    state.copy(status = WorkflowStatus.Cancelled)
                )

                eventBus.publish(Event.WorkflowCancelled(workflowId))

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // 私有辅助方法
    private suspend fun executeWorkflow(
        workflow: Workflow,
        state: WorkflowExecutionState
    ): WorkflowResult {
        // 查找起始节点
        val startNode = workflow.nodes.find { it.type == NodeType.Start }
            ?: return WorkflowResult.Failure("No start node found")

        var currentNode = startNode
        state.variables.putAll(state.input)

        while (currentNode != null && state.status == WorkflowStatus.Running) {
            state.currentNode = currentNode.id

            // 执行节点
            val result = executeNode(currentNode, state)

            when (result) {
                is NodeResult.Success -> {
                    state.completedNodes.add(currentNode.id)
                    state.variables.putAll(result.output)

                    // 如果是终止节点，结束工作流
                    if (currentNode.type == NodeType.End) {
                        return WorkflowResult.Success(state.variables.toMap())
                    }

                    // 查找下一个节点
                    currentNode = findNextNode(currentNode, workflow, state)
                }
                is NodeResult.Failure -> {
                    return WorkflowResult.Failure(
                        "Node ${currentNode.id} failed: ${result.error}"
                    )
                }
                is NodeResult.WaitForInput -> {
                    // 暂停工作流等待输入
                    workflowStore.updateState(state.copy(
                        status = WorkflowStatus.WaitingForInput,
                        waitingForInput = result.inputRequest
                    ))
                    return WorkflowResult.Paused
                }
            }
        }

        return WorkflowResult.Success(state.variables.toMap())
    }

    private suspend fun executeNode(
        node: WorkflowNode,
        state: WorkflowExecutionState
    ): NodeResult {
        return when (node.type) {
            NodeType.Start -> NodeResult.Success(emptyMap())
            NodeType.End -> NodeResult.Success(state.variables.toMap())
            NodeType.Task -> {
                val task = node.config["task"] as? AgentTask
                    ?: return NodeResult.Failure("Task not found in node config")

                val taskResult = taskOrchestrator.executeTask(task)

                when (taskResult) {
                    is TaskResult.Success -> {
                        val output = node.config["outputMapping"] as? Map<*, *>
                            ?.mapKeys { it.key.toString() }
                            ?.mapValues { state.variables[it.value.toString()] }
                            ?: emptyMap()

                        NodeResult.Success(output)
                    }
                    is TaskResult.Failure -> {
                        NodeResult.Failure(taskResult.error)
                    }
                    else -> NodeResult.Failure("Unexpected task result")
                }
            }
            NodeType.Condition -> {
                val condition = node.config["condition"] as? String
                    ?: return NodeResult.Failure("Condition not found")

                val result = evaluateCondition(condition, state.variables)
                NodeResult.Success(mapOf("conditionResult" to result))
            }
            NodeType.Loop -> {
                val loopConfig = node.config["loop"] as? LoopConfig
                    ?: return NodeResult.Failure("Loop config not found")

                executeLoop(loopConfig, state)
            }
            NodeType.Parallel -> {
                val tasks = node.config["tasks"] as? List<AgentTask>
                    ?: return NodeResult.Failure("Tasks not found")

                executeParallel(tasks, state)
            }
            NodeType.Wait -> {
                val duration = node.config["duration"] as? Duration
                    ?: return NodeResult.Failure("Duration not found")

                delay(duration)
                NodeResult.Success(emptyMap())
            }
            NodeType.Input -> {
                val inputRequest = node.config["inputRequest"] as? InputRequest
                    ?: return NodeResult.Failure("Input request not found")

                NodeResult.WaitForInput(inputRequest)
            }
        }
    }

    private fun findNextNode(
        currentNode: WorkflowNode,
        workflow: Workflow,
        state: WorkflowExecutionState
    ): WorkflowNode? {
        // 获取出边
        val edges = workflow.edges.filter { it.source == currentNode.id }

        for (edge in edges) {
            // 检查条件
            if (edge.condition != null) {
                val result = evaluateExpression(edge.condition, state.variables)
                if (result == true) {
                    return workflow.nodes.find { it.id == edge.target }
                }
            } else {
                // 默认边
                return workflow.nodes.find { it.id == edge.target }
            }
        }

        return null
    }

    private fun evaluateCondition(
        condition: String,
        variables: Map<String, Any>
    ): Boolean {
        // 简单的条件求值
        // 实际实现应该使用更安全的表达式求值器
        return when {
            condition == "true" -> true
            condition == "false" -> false
            condition.startsWith("variable.") -> {
                val varName = condition.substringAfter("variable.")
                variables[varName] == true
            }
            else -> false
        }
    }

    private fun evaluateExpression(
        expression: String,
        variables: Map<String, Any>
    ): Any {
        // 简单的表达式求值
        // 实际实现应该使用更安全的表达式求值器
        return if (expression.startsWith("variable.")) {
            val varName = expression.substringAfter("variable.")
            variables[varName] ?: throw IllegalArgumentException("Variable not found: $varName")
        } else {
            expression
        }
    }

    private suspend fun executeLoop(
        config: LoopConfig,
        state: WorkflowExecutionState
    ): NodeResult {
        var result = NodeResult.Success(emptyMap())

        repeat(config.maxIterations) { iteration ->
            val loopState = state.copy(
                variables = state.variables.toMutableMap().apply {
                    put("iteration", iteration)
                }
            )

            val iterationResult = executeNode(config.node, loopState)

            when (iterationResult) {
                is NodeResult.Success -> {
                    state.variables.putAll(iterationResult.output)

                    if (config.breakCondition != null) {
                        val shouldBreak = evaluateCondition(config.breakCondition, state.variables)
                        if (shouldBreak) break
                    }
                }
                is NodeResult.Failure -> {
                    return NodeResult.Failure(iterationResult.error)
                }
                else -> {
                    // 其他情况不处理
                }
            }
        }

        return result
    }

    private suspend fun executeParallel(
        tasks: List<AgentTask>,
        state: WorkflowExecutionState
    ): NodeResult = withContext(ioDispatcher) {
        val results = tasks.map { task ->
            async { taskOrchestrator.executeTask(task) }
        }.awaitAll()

        val failures = results.filterIsInstance<TaskResult.Failure>()
        if (failures.isNotEmpty()) {
            return@withContext NodeResult.Failure(
                "Parallel tasks failed: ${failures.map { it.error }.joinToString()}"
            )
        }

        val outputs = results
            .filterIsInstance<TaskResult.Success>()
            .map { it.data }
            .flatMap { it as? Map<*, *> ?: emptyMap<Any, Any>() }
            .mapKeys { it.key.toString() }
            .toMap()

        NodeResult.Success(outputs)
    }

    private fun isValidNodeType(type: NodeType): Boolean {
        return NodeType.entries.contains(type)
    }

    private fun validateExpression(expression: String) {
        // 验证表达式语法
        // 实现略
    }

    private fun hasCycle(workflow: Workflow): Boolean {
        // 使用 DFS 检测循环
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()

        fun dfs(nodeId: String): Boolean {
            if (nodeId in visiting) return true
            if (nodeId in visited) return false

            visiting.add(nodeId)

            val node = workflow.nodes.find { it.id == nodeId } ?: return false
            for (edgeId in node.outgoingEdges) {
                val edge = workflow.edges.find { it.id == edgeId } ?: continue
                if (dfs(edge.target)) return true
            }

            visiting.remove(nodeId)
            visited.add(nodeId)
            return false
        }

        for (node in workflow.nodes) {
            if (dfs(node.id)) return true
        }

        return false
    }
}
```

---

## 依赖管理

### 核心接口

```kotlin
/**
 * 依赖解析器接口
 */
interface DependencyResolver {

    /**
     * 解析任务依赖
     */
    fun resolveDependencies(
        tasks: List<AgentTask>
    ): List<AgentTask>

    /**
     * 检测循环依赖
     */
    fun detectCycles(
        tasks: List<AgentTask>
    ): List<List<String>>

    /**
     * 获取依赖图
     */
    fun getDependencyGraph(
        tasks: List<AgentTask>
    ): DependencyGraph
}
```

### 实现类

```kotlin
/**
 * 依赖解析器实现
 */
@Singleton
internal class DependencyResolverImpl : DependencyResolver {

    override fun resolveDependencies(
        tasks: List<AgentTask>
    ): List<AgentTask> {
        // 构建依赖图
        val graph = buildDependencyGraph(tasks)

        // 拓扑排序
        return topologicalSort(graph, tasks)
    }

    override fun detectCycles(
        tasks: List<AgentTask>
    ): List<List<String>> {
        val graph = buildDependencyGraph(tasks)
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(nodeId: String) {
            if (nodeId in path) {
                // 检测到循环
                val cycleStart = path.indexOf(nodeId)
                val cycle = path.subList(cycleStart, path.size) + nodeId
                cycles.add(cycle)
                return
            }

            if (nodeId in visited) return

            path.add(nodeId)
            visited.add(nodeId)

            graph[nodeId]?.forEach { depId ->
                dfs(depId)
            }

            path.remove(nodeId)
        }

        for (task in tasks) {
            dfs(task.id)
        }

        return cycles
    }

    override fun getDependencyGraph(
        tasks: List<AgentTask>
    ): DependencyGraph {
        val graph = buildDependencyGraph(tasks)

        return DependencyGraph(
            nodes = tasks.associate { it.id to it },
            edges = graph.flatMap { (taskId, deps) ->
                deps.map { depId ->
                    DependencyEdge(taskId, depId)
                }
            }
        )
    }

    // 私有辅助方法
    private fun buildDependencyGraph(
        tasks: List<AgentTask>
    ): Map<String, List<String>> {
        val taskMap = tasks.associateBy { it.id }
        val graph = mutableMapOf<String, List<String>>()

        for (task in tasks) {
            graph[task.id] = task.dependencies
        }

        return graph
    }

    private fun topologicalSort(
        graph: Map<String, List<String>>,
        tasks: List<AgentTask>
    ): List<AgentTask> {
        val taskMap = tasks.associateBy { it.id }
        val inDegree = mutableMapOf<String, Int>()
        val queue = ArrayDeque<String>()
        val result = mutableListOf<AgentTask>()

        // 计算入度
        for (task in tasks) {
            inDegree[task.id] = 0
        }

        for ((taskId, deps) in graph) {
            for (depId in deps) {
                inDegree[depId] = inDegree.getOrDefault(depId, 0) + 1
            }
        }

        // 找到所有入度为 0 的节点
        for ((taskId, degree) in inDegree) {
            if (degree == 0) {
                queue.add(taskId)
            }
        }

        // 拓扑排序
        while (queue.isNotEmpty()) {
            val taskId = queue.removeFirst()
            taskMap[taskId]?.let { result.add(it) }

            // 减少依赖此节点的其他节点的入度
            for ((otherId, deps) in graph) {
                if (taskId in deps) {
                    inDegree[otherId] = inDegree.getOrDefault(otherId, 0) - 1
                    if (inDegree[otherId] == 0) {
                        queue.add(otherId)
                    }
                }
            }
        }

        // 检查是否所有节点都被访问（检测循环）
        if (result.size != tasks.size) {
            throw CycleDetectedException("Cycle detected in task dependencies")
        }

        return result
    }
}
```

---

## 错误处理

### 核心接口

```kotlin
/**
 * 错误处理器接口
 */
interface ErrorHandler {

    /**
     * 处理任务错误
     */
    suspend fun handleError(
        task: AgentTask,
        error: TaskResult.Failure
    ): ErrorHandlingResult

    /**
     * 注册错误处理策略
     */
    fun registerStrategy(
        errorType: String,
        strategy: ErrorHandlingStrategy
    ): Result<Unit>

    /**
     * 获取错误历史
     */
    fun getErrorHistory(
        taskId: String?
    ): List<ErrorRecord>
}
```

### 实现类

```kotlin
/**
 * 错误处理器实现
 */
@Singleton
internal class ErrorHandlerImpl @Inject constructor(
    private val errorStore: ErrorStore,
    private val eventBus: EventBus,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher
) : ErrorHandler {

    // 错误处理策略
    private val strategies = mutableMapOf<String, ErrorHandlingStrategy>()

    override suspend fun handleError(
        task: AgentTask,
        error: TaskResult.Failure
    ): ErrorHandlingResult = withContext(ioDispatcher) {
        try {
            // 1. 记录错误
            val errorRecord = ErrorRecord(
                id = UUID.randomUUID().toString(),
                taskId = task.id,
                error = error.error,
                timestamp = Clock.System.now(),
                metadata = task.metadata
            )

            errorStore.add(errorRecord)

            // 2. 发布错误事件
            eventBus.publish(Event.TaskError(task.id, Exception(error.error)))

            // 3. 查找合适的处理策略
            val strategy = findStrategy(error)

            // 4. 执行策略
            val result = when (strategy) {
                is ErrorHandlingStrategy.Retry -> {
                    executeRetry(task, strategy)
                }
                is ErrorHandlingStrategy.Fallback -> {
                    executeFallback(task, strategy)
                }
                is ErrorHandlingStrategy.Ignore -> {
                    ErrorHandlingResult.Ignored
                }
                is ErrorHandlingStrategy.Fail -> {
                    ErrorHandlingResult.Failed(error.error)
                }
                null -> {
                    // 默认：失败
                    ErrorHandlingResult.Failed(error.error)
                }
            }

            result
        } catch (e: Exception) {
            ErrorHandlingResult.Failed("Error handling failed: ${e.message}")
        }
    }

    override fun registerStrategy(
        errorType: String,
        strategy: ErrorHandlingStrategy
    ): Result<Unit> {
        return try {
            strategies[errorType] = strategy
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getErrorHistory(taskId: String?): List<ErrorRecord> {
        return if (taskId != null) {
            errorStore.getByTaskId(taskId)
        } else {
            errorStore.getAll()
        }
    }

    // 私有辅助方法
    private fun findStrategy(
        error: TaskResult.Failure
    ): ErrorHandlingStrategy? {
        // 根据错误类型查找策略
        for ((errorType, strategy) in strategies) {
            if (error.error.contains(errorType, ignoreCase = true)) {
                return strategy
            }
        }
        return null
    }

    private suspend fun executeRetry(
        task: AgentTask,
        strategy: ErrorHandlingStrategy.Retry
    ): ErrorHandlingResult {
        var lastError = task.metadata.get("lastError") as? String ?: ""

        repeat(strategy.maxRetries) { attempt ->
            delay(strategy.delay)

            // 重新执行任务
            // 注意：这里需要通过 TaskOrchestrator 执行
            // 实际实现应该注入 TaskOrchestrator

            lastError = "Retry $attempt failed"
        }

        return ErrorHandlingResult.Failed("All retries failed: $lastError")
    }

    private suspend fun executeFallback(
        task: AgentTask,
        strategy: ErrorHandlingStrategy.Fallback
    ): ErrorHandlingResult {
        // 执行回退任务
        val fallbackResult = strategy.fallbackTask()

        return when (fallbackResult) {
            is TaskResult.Success -> {
                ErrorHandlingResult.Recovered(fallbackResult.data)
            }
            else -> {
                ErrorHandlingResult.Failed("Fallback task failed")
            }
        }
    }
}

/**
 * 错误处理策略
 */
sealed interface ErrorHandlingStrategy {
    data class Retry(
        val maxRetries: Int,
        val delay: Duration
    ) : ErrorHandlingStrategy

    data class Fallback(
        val fallbackTask: suspend () -> TaskResult
    ) : ErrorHandlingStrategy

    object Ignore : ErrorHandlingStrategy
    object Fail : ErrorHandlingStrategy
}

/**
 * 错误处理结果
 */
sealed interface ErrorHandlingResult {
    object Ignored : ErrorHandlingResult
    data class Recovered(val data: Any?) : ErrorHandlingResult
    data class Failed(val error: String) : ErrorHandlingResult
}
```

---

## 依赖关系

### 模块依赖图

```
┌─────────────────────────────────────────────────────────────────┐
│                      Task Orchestrator                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   Task       │  │   Workflow   │  │ Dependency   │         │
│  │ Dispatcher   │  │    Engine    │  │   Resolver   │         │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
│         │                  │                  │                  │
└─────────┼──────────────────┼──────────────────┼──────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Shared Dependencies                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │     Event    │  │    Error     │  │ Concurrency  │         │
│  │     Bus      │  │   Handler    │  │  Controller  │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Task Handlers                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │   LLM        │  │   Tool       │  │    Plugin    │         │
│  │   Handler    │  │   Handler    │  │   Handler    │         │
│  └──────────────┘  └──────────────┘  └──────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

---

## 附录

### A. 数据类定义

```kotlin
/**
 * 任务数据类
 */
@Serializable
data class AgentTask(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val input: Map<String, Any>,
    val dependencies: List<String> = emptyList(),
    val metadata: Map<String, Any> = emptyMap(),
    val priority: TaskPriority = TaskPriority.Normal,
    val timeout: Duration? = null
)

/**
 * 任务优先级
 */
enum class TaskPriority {
    Critical,
    High,
    Normal,
    Low
}

/**
 * 任务结果
 */
sealed interface TaskResult {
    data class Success(val data: Any?) : TaskResult
    data class Failure(val error: String) : TaskResult
    data class Partial(val progress: Float, val data: Any? = null) : TaskResult
}

/**
 * 任务状态
 */
sealed interface TaskStatus {
    object Pending : TaskStatus
    object Running : TaskStatus
    data class Completed(val result: TaskResult) : TaskStatus
    data class Failed(val error: String) : TaskStatus
    object Cancelled : TaskStatus
}

/**
 * 工作流定义
 */
@Serializable
data class Workflow(
    val id: String,
    val name: String,
    val description: String,
    val nodes: List<WorkflowNode>,
    val edges: List<WorkflowEdge>,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 工作流节点
 */
@Serializable
data class WorkflowNode(
    val id: String,
    val type: NodeType,
    val config: Map<String, Any>,
    val outgoingEdges: List<String>
)

/**
 * 节点类型
 */
enum class NodeType {
    Start,
    End,
    Task,
    Condition,
    Loop,
    Parallel,
    Wait,
    Input
}

/**
 * 工作流边
 */
@Serializable
data class WorkflowEdge(
    val id: String,
    val source: String,
    val target: String,
    val condition: String? = null
)

/**
 * 工作流结果
 */
sealed interface WorkflowResult {
    data class Success(val output: Map<String, Any>) : WorkflowResult
    data class Failure(val error: String) : WorkflowResult
    object Paused : WorkflowResult
}

/**
 * 工作流状态
 */
enum class WorkflowStatus {
    Running,
    Paused,
    WaitingForInput,
    Completed,
    Failed,
    Cancelled
}
```

### B. 事件定义

```kotlin
/**
 * 任务事件
 */
sealed interface TaskEvent {
    data class Created(val task: AgentTask) : TaskEvent
    data class Started(val taskId: String) : TaskEvent
    data class Completed(val taskId: String, val result: Any?) : TaskEvent
    data class Failed(val taskId: String, val error: String) : TaskEvent
    data class Cancelled(val taskId: String) : TaskEvent
    data class Progress(val taskId: String, val progress: Float) : TaskEvent
}
```

### C. 相关文档

- [01-architecture.md](./01-architecture.md) - 整体架构设计
- [03-core-modules.md](./03-core-modules.md) - 核心模块设计
- [05-plugin-system.md](./05-plugin-system.md) - 插件系统

---

**文档维护**: 本文档应随着任务编排功能的实现持续更新
**审查周期**: 每两周一次或重大变更时
