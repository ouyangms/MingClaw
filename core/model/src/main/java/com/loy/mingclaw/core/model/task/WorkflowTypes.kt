package com.loy.mingclaw.core.model.task

import kotlinx.serialization.Serializable

@Serializable
data class Workflow(
    val id: String,
    val name: String,
    val description: String = "",
    val nodes: List<WorkflowNode> = emptyList(),
    val edges: List<WorkflowEdge> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class WorkflowNode(
    val id: String,
    val type: NodeType,
    val config: Map<String, String> = emptyMap(),
    val outgoingEdges: List<String> = emptyList(),
)

enum class NodeType {
    Start, End, Task, Condition, Loop, Parallel, Wait, Input,
}

@Serializable
data class WorkflowEdge(
    val id: String,
    val source: String,
    val target: String,
    val condition: String? = null,
)

sealed interface WorkflowResult {
    data class Success(val output: Map<String, String> = emptyMap()) : WorkflowResult
    data class Failure(val error: String) : WorkflowResult
    object Paused : WorkflowResult
}

enum class WorkflowStatus {
    Running, Paused, WaitingForInput, Completed, Failed, Cancelled,
}
