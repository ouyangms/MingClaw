package com.loy.mingclaw.core.task

import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.CycleDetectedException
import com.loy.mingclaw.core.task.internal.DependencyResolverImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DependencyResolverImplTest {

    private lateinit var resolver: DependencyResolverImpl

    @Before
    fun setup() {
        resolver = DependencyResolverImpl()
    }

    @Test
    fun resolve_noDependencies_returnsOriginalOrder() {
        val tasks = listOf(
            AgentTask(id = "a", type = "test"),
            AgentTask(id = "b", type = "test"),
            AgentTask(id = "c", type = "test"),
        )
        val result = resolver.resolve(tasks)
        assertEquals(3, result.size)
    }

    @Test
    fun resolve_withDependencies_returnsTopologicalOrder() {
        val tasks = listOf(
            AgentTask(id = "c", type = "test", dependencies = listOf("a", "b")),
            AgentTask(id = "a", type = "test"),
            AgentTask(id = "b", type = "test", dependencies = listOf("a")),
        )
        val result = resolver.resolve(tasks)
        assertEquals(3, result.size)
        val aIdx = result.indexOfFirst { it.id == "a" }
        val bIdx = result.indexOfFirst { it.id == "b" }
        val cIdx = result.indexOfFirst { it.id == "c" }
        assertTrue(aIdx < bIdx)
        assertTrue(aIdx < cIdx)
        assertTrue(bIdx < cIdx)
    }

    @Test(expected = CycleDetectedException::class)
    fun resolve_withCycle_throwsException() {
        val tasks = listOf(
            AgentTask(id = "a", type = "test", dependencies = listOf("b")),
            AgentTask(id = "b", type = "test", dependencies = listOf("a")),
        )
        resolver.resolve(tasks)
    }

    @Test
    fun detectCycles_noCycle_returnsEmpty() {
        val tasks = listOf(
            AgentTask(id = "a", type = "test"),
            AgentTask(id = "b", type = "test", dependencies = listOf("a")),
        )
        val cycles = resolver.detectCycles(tasks)
        assertTrue(cycles.isEmpty())
    }

    @Test
    fun getGraph_returnsCorrectEdges() {
        val tasks = listOf(
            AgentTask(id = "b", type = "test", dependencies = listOf("a")),
            AgentTask(id = "a", type = "test"),
        )
        val graph = resolver.getGraph(tasks)
        assertEquals(2, graph.nodes.size)
        assertEquals(1, graph.edges.size)
        assertEquals("a", graph.edges[0].from)
        assertEquals("b", graph.edges[0].to)
    }
}
