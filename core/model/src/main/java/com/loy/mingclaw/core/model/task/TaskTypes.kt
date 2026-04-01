package com.loy.mingclaw.core.model.task

import kotlinx.serialization.Serializable

@Serializable
data class AgentTask(
    val id: String,
    val type: String,
    val input: Map<String, String> = emptyMap(),
    val dependencies: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val priority: TaskPriority = TaskPriority.Normal,
    val timeoutMs: Long? = null,
)

enum class TaskPriority {
    Critical, High, Normal, Low
}

sealed interface TaskResult {
    data class Success(val data: Map<String, String> = emptyMap()) : TaskResult
    data class Failure(val error: String) : TaskResult
    data class Partial(val progress: Float, val data: Map<String, String> = emptyMap()) : TaskResult
}

sealed interface TaskStatus {
    object Pending : TaskStatus
    object Running : TaskStatus
    data class Completed(val result: TaskResult) : TaskStatus
    data class Failed(val error: String) : TaskStatus
    object Cancelled : TaskStatus
}

sealed interface TaskEvent {
    data class Created(val taskId: String) : TaskEvent
    data class Started(val taskId: String) : TaskEvent
    data class Completed(val taskId: String, val result: TaskResult) : TaskEvent
    data class Failed(val taskId: String, val error: String) : TaskEvent
    data class Cancelled(val taskId: String) : TaskEvent
    data class Progress(val taskId: String, val progress: Float) : TaskEvent
}
