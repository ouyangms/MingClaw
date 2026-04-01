package com.loy.mingclaw.core.task

import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.TaskEvent
import com.loy.mingclaw.core.model.task.TaskResult
import com.loy.mingclaw.core.model.task.TaskStatus
import com.loy.mingclaw.core.model.task.Workflow
import com.loy.mingclaw.core.model.task.WorkflowResult
import kotlinx.coroutines.flow.Flow

interface TaskOrchestrator {
    suspend fun executeTask(task: AgentTask): TaskResult
    suspend fun executeTasks(tasks: List<AgentTask>): List<TaskResult>
    suspend fun executeWorkflow(workflow: Workflow): WorkflowResult
    suspend fun cancelTask(taskId: String): Result<Unit>
    fun getTaskStatus(taskId: String): TaskStatus?
    fun watchTaskEvents(taskId: String): Flow<TaskEvent>
    fun registerHandler(taskType: String, handler: TaskHandler)
    fun unregisterHandler(taskType: String)
}
