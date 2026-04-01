package com.loy.mingclaw.core.task.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.NodeType
import com.loy.mingclaw.core.model.task.TaskResult
import com.loy.mingclaw.core.model.task.Workflow
import com.loy.mingclaw.core.model.task.WorkflowResult
import com.loy.mingclaw.core.model.task.WorkflowStatus
import com.loy.mingclaw.core.task.TaskOrchestrator
import com.loy.mingclaw.core.task.WorkflowEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WorkflowEngineImpl @Inject constructor(
    private val taskOrchestrator: TaskOrchestrator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : WorkflowEngine {

    private val workflowStatuses = ConcurrentHashMap<String, WorkflowStatus>()

    override suspend fun execute(workflow: Workflow): WorkflowResult = withContext(ioDispatcher) {
        workflowStatuses[workflow.id] = WorkflowStatus.Running

        val startNode = workflow.nodes.find { it.type == NodeType.Start }
        if (startNode == null) {
            workflowStatuses[workflow.id] = WorkflowStatus.Failed
            return@withContext WorkflowResult.Failure("No start node found")
        }

        val variables = mutableMapOf<String, String>()
        var currentId: String? = startNode.id

        while (currentId != null) {
            val node = workflow.nodes.find { it.id == currentId }
                ?: break

            when (node.type) {
                NodeType.End -> {
                    workflowStatuses[workflow.id] = WorkflowStatus.Completed
                    return@withContext WorkflowResult.Success(variables.toMap())
                }
                NodeType.Task -> {
                    val task = AgentTask(
                        id = node.id,
                        type = node.config["taskType"] ?: "unknown",
                        input = node.config,
                    )
                    when (val result = taskOrchestrator.executeTask(task)) {
                        is TaskResult.Success -> variables.putAll(result.data)
                        is TaskResult.Failure -> {
                            workflowStatuses[workflow.id] = WorkflowStatus.Failed
                            return@withContext WorkflowResult.Failure(result.error)
                        }
                        is TaskResult.Partial -> {}
                    }
                }
                NodeType.Start -> { /* nothing to do */ }
                else -> { /* other node types not yet supported in MVP */ }
            }

            val edge = workflow.edges.find { it.source == currentId }
            currentId = edge?.target
        }

        workflowStatuses[workflow.id] = WorkflowStatus.Completed
        WorkflowResult.Success(variables.toMap())
    }

    override fun getStatus(workflowId: String): WorkflowStatus? = workflowStatuses[workflowId]
}
