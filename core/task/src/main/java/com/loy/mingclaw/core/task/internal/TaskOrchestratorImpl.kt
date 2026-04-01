package com.loy.mingclaw.core.task.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.kernel.EventBus
import com.loy.mingclaw.core.model.Event
import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.NodeType
import com.loy.mingclaw.core.model.task.TaskEvent
import com.loy.mingclaw.core.model.task.TaskResult
import com.loy.mingclaw.core.model.task.TaskStatus
import com.loy.mingclaw.core.model.task.Workflow
import com.loy.mingclaw.core.model.task.WorkflowResult
import com.loy.mingclaw.core.task.ConcurrencyController
import com.loy.mingclaw.core.task.TaskDispatcher
import com.loy.mingclaw.core.task.TaskHandler
import com.loy.mingclaw.core.task.TaskOrchestrator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TaskOrchestratorImpl @Inject constructor(
    private val dispatcher: TaskDispatcher,
    private val concurrencyController: ConcurrencyController,
    private val eventBus: EventBus,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : TaskOrchestrator {

    private val taskStatuses = ConcurrentHashMap<String, TaskStatus>()
    private val taskEvents = ConcurrentHashMap<String, MutableSharedFlow<TaskEvent>>()

    override suspend fun executeTask(task: AgentTask): TaskResult = withContext(ioDispatcher) {
        taskStatuses[task.id] = TaskStatus.Pending
        emitEvent(task.id, TaskEvent.Created(task.id))
        eventBus.publishAsync(Event.TaskStarted(task.id))

        try {
            concurrencyController.acquire()
            taskStatuses[task.id] = TaskStatus.Running
            emitEvent(task.id, TaskEvent.Started(task.id))

            val result = dispatcher.dispatch(task)

            when (result) {
                is TaskResult.Success -> {
                    taskStatuses[task.id] = TaskStatus.Completed(result)
                    emitEvent(task.id, TaskEvent.Completed(task.id, result))
                    eventBus.publishAsync(Event.TaskCompleted(task.id))
                }
                is TaskResult.Failure -> {
                    taskStatuses[task.id] = TaskStatus.Failed(result.error)
                    emitEvent(task.id, TaskEvent.Failed(task.id, result.error))
                    eventBus.publishAsync(Event.TaskFailed(task.id, result.error))
                }
                is TaskResult.Partial -> {
                    taskStatuses[task.id] = TaskStatus.Running
                    emitEvent(task.id, TaskEvent.Progress(task.id, result.progress))
                }
            }

            concurrencyController.release()
            result
        } catch (e: Exception) {
            concurrencyController.release()
            val error = e.message ?: "Unknown error"
            taskStatuses[task.id] = TaskStatus.Failed(error)
            emitEvent(task.id, TaskEvent.Failed(task.id, error))
            eventBus.publishAsync(Event.TaskFailed(task.id, error))
            TaskResult.Failure(error)
        }
    }

    override suspend fun executeTasks(tasks: List<AgentTask>): List<TaskResult> =
        withContext(ioDispatcher) {
            val sorted = try {
                DependencyResolverImpl().resolve(tasks)
            } catch (e: Exception) {
                // If cycle detected, try original order
                tasks
            }

            val results = mutableListOf<TaskResult>()
            val completed = mutableMapOf<String, TaskResult>()

            for (task in sorted) {
                val depsMet = task.dependencies.all { depId ->
                    completed[depId]?.let { it is TaskResult.Success } ?: false
                }

                if (!depsMet) {
                    results.add(TaskResult.Failure("Dependencies not met for task ${task.id}"))
                    completed[task.id] = TaskResult.Failure("Dependencies not met")
                    continue
                }

                val result = executeTask(task)
                results.add(result)
                completed[task.id] = result
            }

            results
        }

    override suspend fun executeWorkflow(workflow: Workflow): WorkflowResult =
        withContext(ioDispatcher) {
            // Simplified linear workflow execution: find start node, follow edges to end
            val startNode = workflow.nodes.find { it.type == NodeType.Start }
                ?: return@withContext WorkflowResult.Failure("No start node found")

            val variables = mutableMapOf<String, String>()
            var currentId: String? = startNode.id

            while (currentId != null) {
                val node = workflow.nodes.find { it.id == currentId }
                    ?: return@withContext WorkflowResult.Failure("Node not found: $currentId")

                if (node.type == NodeType.End) {
                    return@withContext WorkflowResult.Success(variables.toMap())
                }

                if (node.type == NodeType.Task) {
                    val task = AgentTask(
                        id = node.id,
                        type = node.config["taskType"] ?: "unknown",
                        input = node.config,
                    )
                    val result = executeTask(task)
                    when (result) {
                        is TaskResult.Success -> variables.putAll(result.data)
                        is TaskResult.Failure -> return@withContext WorkflowResult.Failure(result.error)
                        is TaskResult.Partial -> {}
                    }
                }

                // Find next node via edges
                val edge = workflow.edges.find { it.source == currentId }
                currentId = edge?.target
            }

            WorkflowResult.Success(variables.toMap())
        }

    override suspend fun cancelTask(taskId: String): Result<Unit> = withContext(ioDispatcher) {
        val status = taskStatuses[taskId]
            ?: return@withContext Result.failure(IllegalArgumentException("Task not found: $taskId"))

        when (status) {
            is TaskStatus.Pending, is TaskStatus.Running -> {
                taskStatuses[taskId] = TaskStatus.Cancelled
                emitEvent(taskId, TaskEvent.Cancelled(taskId))
                Result.success(Unit)
            }
            else -> Result.failure(IllegalStateException("Cannot cancel task in status: $status"))
        }
    }

    override fun getTaskStatus(taskId: String): TaskStatus? = taskStatuses[taskId]

    override fun watchTaskEvents(taskId: String): Flow<TaskEvent> {
        return taskEvents.getOrPut(taskId) {
            MutableSharedFlow(replay = 50)
        }.asSharedFlow()
    }

    override fun registerHandler(taskType: String, handler: TaskHandler) {
        dispatcher.registerHandler(taskType, handler)
    }

    override fun unregisterHandler(taskType: String) {
        dispatcher.unregisterHandler(taskType)
    }

    private suspend fun emitEvent(taskId: String, event: TaskEvent) {
        taskEvents.getOrPut(taskId) {
            MutableSharedFlow(replay = 50)
        }.emit(event)
    }
}
