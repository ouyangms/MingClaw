package com.loy.mingclaw.core.model.task

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTypesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun agentTask_serialization_roundTrip() {
        val task = AgentTask(
            id = "task-1",
            type = "llm_call",
            input = mapOf("prompt" to "hello"),
            dependencies = listOf("task-0"),
            priority = TaskPriority.High,
            timeoutMs = 30000L,
        )
        val encoded = json.encodeToString(AgentTask.serializer(), task)
        val decoded = json.decodeFromString(AgentTask.serializer(), encoded)
        assertEquals(task, decoded)
    }

    @Test
    fun taskPriority_allValues() {
        val priorities = TaskPriority.values()
        assertEquals(4, priorities.size)
        assertTrue(priorities.contains(TaskPriority.Critical))
    }

    @Test
    fun taskResult_success_holdsData() {
        val result = TaskResult.Success(mapOf("key" to "value"))
        assertEquals(mapOf("key" to "value"), result.data)
    }

    @Test
    fun taskResult_failure_holdsError() {
        val result = TaskResult.Failure("something went wrong")
        assertEquals("something went wrong", result.error)
    }

    @Test
    fun taskStatus_allVariants() {
        val statuses = listOf(
            TaskStatus.Pending,
            TaskStatus.Running,
            TaskStatus.Completed(TaskResult.Success()),
            TaskStatus.Failed("error"),
            TaskStatus.Cancelled,
        )
        assertEquals(5, statuses.size)
    }
}
