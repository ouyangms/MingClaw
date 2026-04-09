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

    @Test
    fun handleError_retryStrategy_returnsRecoveredAfterDelay() = runTest(scheduler) {
        handler.registerStrategy("timeout", ErrorHandlingStrategy.Retry(maxRetries = 2, delayMs = 100))
        val task = AgentTask(id = "1", type = "test")
        val result = handler.handleError(task, "Connection timeout")
        assertTrue(result is ErrorHandlingResult.Recovered)
        val recovered = result as ErrorHandlingResult.Recovered
        assertEquals("1", recovered.data["taskId"])
        assertEquals("retry", recovered.data["strategy"])
        assertEquals("0", recovered.data["retryCount"])
    }

    @Test
    fun handleError_retryStrategy_recordsRetryInMetadata() = runTest(scheduler) {
        handler.registerStrategy("timeout", ErrorHandlingStrategy.Retry(maxRetries = 3, delayMs = 50))
        val task = AgentTask(id = "1", type = "test")
        handler.handleError(task, "Connection timeout")
        val history = handler.getErrorHistory("1")
        assertEquals(1, history.size)
        assertEquals("Connection timeout", history[0].error)
        assertEquals("retry", history[0].metadata["strategy"])
    }

    @Test
    fun handleError_fallbackStrategy_returnsRecovered() = runTest(scheduler) {
        handler.registerStrategy("api", ErrorHandlingStrategy.Fallback(fallbackType = "cache"))
        val task = AgentTask(id = "1", type = "test")
        val result = handler.handleError(task, "api call failed")
        assertTrue(result is ErrorHandlingResult.Recovered)
        val recovered = result as ErrorHandlingResult.Recovered
        assertEquals("1", recovered.data["taskId"])
        assertEquals("fallback", recovered.data["strategy"])
        assertEquals("cache", recovered.data["fallbackType"])
    }

    @Test
    fun handleError_fallbackStrategy_recordsFallbackInMetadata() = runTest(scheduler) {
        handler.registerStrategy("api", ErrorHandlingStrategy.Fallback(fallbackType = "cache"))
        val task = AgentTask(id = "1", type = "test")
        handler.handleError(task, "api call failed")
        val history = handler.getErrorHistory("1")
        assertEquals(1, history.size)
        assertEquals("fallback", history[0].metadata["strategy"])
        assertEquals("cache", history[0].metadata["fallbackType"])
    }
}
