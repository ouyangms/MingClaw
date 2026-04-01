package com.loy.mingclaw.core.model.task

data class DependencyGraph(
    val nodes: Map<String, AgentTask>,
    val edges: List<DependencyEdge>,
)

data class DependencyEdge(
    val from: String,
    val to: String,
)

class CycleDetectedException(message: String) : Exception(message)
