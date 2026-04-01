package com.loy.mingclaw.core.task.internal

import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.CycleDetectedException
import com.loy.mingclaw.core.model.task.DependencyEdge
import com.loy.mingclaw.core.model.task.DependencyGraph
import com.loy.mingclaw.core.task.DependencyResolver
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DependencyResolverImpl @Inject constructor() : DependencyResolver {

    override fun resolve(tasks: List<AgentTask>): List<AgentTask> {
        val taskMap = tasks.associateBy { it.id }
        val inDegree = mutableMapOf<String, Int>()
        val dependents = mutableMapOf<String, MutableList<String>>()

        for (task in tasks) {
            inDegree[task.id] = 0
            dependents[task.id] = mutableListOf()
        }

        for (task in tasks) {
            for (depId in task.dependencies) {
                if (depId !in inDegree) continue
                inDegree[task.id] = inDegree[task.id]!! + 1
                dependents[depId]!!.add(task.id)
            }
        }

        val queue = ArrayDeque<String>()
        for ((id, degree) in inDegree) {
            if (degree == 0) queue.add(id)
        }

        val result = mutableListOf<AgentTask>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            taskMap[id]?.let { result.add(it) }
            for (dependent in dependents[id] ?: emptyList()) {
                inDegree[dependent] = inDegree[dependent]!! - 1
                if (inDegree[dependent] == 0) {
                    queue.add(dependent)
                }
            }
        }

        if (result.size != tasks.size) {
            throw CycleDetectedException("Cycle detected in task dependencies")
        }

        return result
    }

    override fun detectCycles(tasks: List<AgentTask>): List<List<String>> {
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(taskId: String) {
            if (taskId in path) {
                val cycleStart = path.indexOf(taskId)
                cycles.add(path.subList(cycleStart, path.size) + taskId)
                return
            }
            if (taskId in visited) return

            path.add(taskId)
            val task = tasks.find { it.id == taskId } ?: return
            for (depId in task.dependencies) {
                dfs(depId)
            }
            path.remove(taskId)
            visited.add(taskId)
        }

        for (task in tasks) {
            dfs(task.id)
        }

        return cycles
    }

    override fun getGraph(tasks: List<AgentTask>): DependencyGraph {
        val nodes = tasks.associateBy { it.id }
        val edges = tasks.flatMap { task ->
            task.dependencies.map { depId ->
                DependencyEdge(from = depId, to = task.id)
            }
        }
        return DependencyGraph(nodes = nodes, edges = edges)
    }
}
