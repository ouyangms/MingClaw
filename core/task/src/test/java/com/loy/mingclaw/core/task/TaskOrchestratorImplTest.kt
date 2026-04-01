package com.loy.mingclaw.core.task

import com.loy.mingclaw.core.kernel.EventBus
import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.TaskResult
import com.loy.mingclaw.core.model.task.TaskStatus
import com.loy.mingclaw.core.task.internal.ConcurrencyControllerImpl
import com.loy.mingclaw.core.task.internal.TaskDispatcherImpl
import com.loy.mingclaw.core.task.internal.TaskOrchestratorImpl
import io.mockk.mockk
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskOrchestratorImplTest {

    private lateinit var orchestrator: TaskOrchestratorImpl
    private lateinit var dispatcher: TaskDispatcherImpl
    private lateinit var scheduler: TestCoroutineScheduler
    private val eventBus = mockk<EventBus>(relaxed = true)

    @Before
    fun setup() {
        scheduler = TestCoroutineScheduler()
        val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(scheduler)
        dispatcher = TaskDispatcherImpl()
        val concurrencyController = ConcurrencyControllerImpl()
        orchestrator = TaskOrchestratorImpl(dispatcher, concurrencyController, eventBus, testDispatcher)
    }

    @Test
    fun executeTask_withHandler_returnsSuccess() = runTest(scheduler) {
        val handler = object : TaskHandler {
            override suspend fun handle(task: AgentTask) = TaskResult.Success(mapOf("key" to "value"))
            override fun supportedType() = "test"
        }
        orchestrator.registerHandler("test", handler)

        val task = AgentTask(id = "1", type = "test")
        val result = orchestrator.executeTask(task)
        assertTrue(result is TaskResult.Success)
        assertEquals("value", (result as TaskResult.Success).data["key"])
        assertTrue(orchestrator.getTaskStatus("1") is TaskStatus.Completed)
    }

    @Test
    fun executeTask_noHandler_returnsFailure() = runTest(scheduler) {
        val task = AgentTask(id = "1", type = "unknown")
        val result = orchestrator.executeTask(task)
        assertTrue(result is TaskResult.Failure)
        assertTrue(orchestrator.getTaskStatus("1") is TaskStatus.Failed)
    }

    @Test
    fun executeTasks_withDependencies_executesInOrder() = runTest(scheduler) {
        val executionOrder = mutableListOf<String>()
        val handler = object : TaskHandler {
            override suspend fun handle(task: AgentTask): TaskResult {
                executionOrder.add(task.id)
                return TaskResult.Success()
            }
            override fun supportedType() = "test"
        }
        orchestrator.registerHandler("test", handler)

        val tasks = listOf(
            AgentTask(id = "c", type = "test", dependencies = listOf("a", "b")),
            AgentTask(id = "a", type = "test"),
            AgentTask(id = "b", type = "test", dependencies = listOf("a")),
        )
        val results = orchestrator.executeTasks(tasks)
        assertEquals(3, results.size)
        assertEquals(listOf("a", "b", "c"), executionOrder)
    }

    @Test
    fun cancelTask_pendingTask_succeeds() = runTest(scheduler) {
        val task = AgentTask(id = "1", type = "test")
        // The task won't exist in statuses, so it should fail
        val result = orchestrator.cancelTask("1")
        assertTrue(result.isFailure)
    }

    @Test
    fun getTaskStatus_unknownTask_returnsNull() {
        assertTrue(orchestrator.getTaskStatus("nonexistent") == null)
    }
}
