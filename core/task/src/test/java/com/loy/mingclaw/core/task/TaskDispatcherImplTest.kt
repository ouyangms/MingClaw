package com.loy.mingclaw.core.task

import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.TaskResult
import com.loy.mingclaw.core.task.internal.TaskDispatcherImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskDispatcherImplTest {

    private lateinit var dispatcher: TaskDispatcherImpl

    @Before
    fun setup() {
        dispatcher = TaskDispatcherImpl()
    }

    @Test
    fun dispatch_noHandler_returnsFailure() = runTest {
        val task = AgentTask(id = "1", type = "unknown")
        val result = dispatcher.dispatch(task)
        assertTrue(result is TaskResult.Failure)
        assertTrue((result as TaskResult.Failure).error.contains("No handler"))
    }

    @Test
    fun dispatch_withHandler_dispatchesCorrectly() = runTest {
        val handler = object : TaskHandler {
            override suspend fun handle(task: AgentTask) = TaskResult.Success(mapOf("result" to "ok"))
            override fun supportedType() = "test"
        }
        dispatcher.registerHandler("test", handler)
        val task = AgentTask(id = "1", type = "test")
        val result = dispatcher.dispatch(task)
        assertTrue(result is TaskResult.Success)
        assertEquals("ok", (result as TaskResult.Success).data["result"])
    }

    @Test
    fun unregisterHandler_removesHandler() = runTest {
        val handler = object : TaskHandler {
            override suspend fun handle(task: AgentTask) = TaskResult.Success()
            override fun supportedType() = "test"
        }
        dispatcher.registerHandler("test", handler)
        dispatcher.unregisterHandler("test")
        val task = AgentTask(id = "1", type = "test")
        val result = dispatcher.dispatch(task)
        assertTrue(result is TaskResult.Failure)
    }
}
