package com.loy.mingclaw.core.task

import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.ErrorHandlingResult
import com.loy.mingclaw.core.model.task.ErrorHandlingStrategy
import com.loy.mingclaw.core.task.internal.ErrorHandlerImpl
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ErrorHandlerImplTest {

    private lateinit var handler: ErrorHandlerImpl
    private lateinit var scheduler: TestCoroutineScheduler

    @Before
    fun setup() {
        scheduler = TestCoroutineScheduler()
        val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(scheduler)
        handler = ErrorHandlerImpl(testDispatcher)
    }

    @Test
    fun handleError_noStrategy_returnsFailed() = runTest(scheduler) {
        val task = AgentTask(id = "1", type = "test")
        val result = handler.handleError(task, "something failed")
        assertTrue(result is ErrorHandlingResult.Failed)
        assertEquals("something failed", (result as ErrorHandlingResult.Failed).error)
    }

    @Test
    fun handleError_ignoreStrategy_returnsIgnored() = runTest(scheduler) {
        handler.registerStrategy("timeout", ErrorHandlingStrategy.Ignore)
        val task = AgentTask(id = "1", type = "test")
        val result = handler.handleError(task, "Connection timeout")
        assertTrue(result is ErrorHandlingResult.Ignored)
    }

    @Test
    fun handleError_recordsError() = runTest(scheduler) {
        val task = AgentTask(id = "1", type = "test")
        handler.handleError(task, "error 1")
        handler.handleError(task, "error 2")
        val history = handler.getErrorHistory("1")
        assertEquals(2, history.size)
    }

    @Test
    fun getErrorHistory_allTasks() = runTest(scheduler) {
        val task1 = AgentTask(id = "1", type = "test")
        val task2 = AgentTask(id = "2", type = "test")
        handler.handleError(task1, "error")
        handler.handleError(task2, "error")
        val history = handler.getErrorHistory(null)
        assertEquals(2, history.size)
    }
}
