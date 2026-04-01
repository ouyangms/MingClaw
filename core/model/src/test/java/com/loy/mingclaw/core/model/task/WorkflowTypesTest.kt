package com.loy.mingclaw.core.model.task

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowTypesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun workflow_serialization_roundTrip() {
        val workflow = Workflow(
            id = "wf-1",
            name = "Test Workflow",
            nodes = listOf(
                WorkflowNode(id = "start", type = NodeType.Start),
                WorkflowNode(id = "end", type = NodeType.End),
            ),
            edges = listOf(
                WorkflowEdge(id = "e1", source = "start", target = "end"),
            ),
        )
        val encoded = json.encodeToString(Workflow.serializer(), workflow)
        val decoded = json.decodeFromString(Workflow.serializer(), encoded)
        assertEquals(workflow, decoded)
    }

    @Test
    fun nodeType_allValues() {
        val types = NodeType.values()
        assertEquals(8, types.size)
    }

    @Test
    fun workflowEdge_withCondition() {
        val edge = WorkflowEdge(
            id = "e1",
            source = "n1",
            target = "n2",
            condition = "variable.x == true",
        )
        val encoded = json.encodeToString(WorkflowEdge.serializer(), edge)
        val decoded = json.decodeFromString(WorkflowEdge.serializer(), encoded)
        assertEquals(edge, decoded)
    }
}
