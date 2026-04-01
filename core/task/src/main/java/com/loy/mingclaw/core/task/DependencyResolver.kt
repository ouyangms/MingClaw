package com.loy.mingclaw.core.task

import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.DependencyGraph

interface DependencyResolver {
    fun resolve(tasks: List<AgentTask>): List<AgentTask>
    fun detectCycles(tasks: List<AgentTask>): List<List<String>>
    fun getGraph(tasks: List<AgentTask>): DependencyGraph
}
