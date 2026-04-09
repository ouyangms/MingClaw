package com.loy.mingclaw.core.task.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.ErrorHandlingResult
import com.loy.mingclaw.core.model.task.ErrorHandlingStrategy
import com.loy.mingclaw.core.model.task.ErrorRecord
import com.loy.mingclaw.core.task.ErrorHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ErrorHandlerImpl @Inject constructor(
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ErrorHandler {

    private val strategies = mutableMapOf<String, ErrorHandlingStrategy>()
    private val errorHistory = mutableListOf<ErrorRecord>()

    override suspend fun handleError(task: AgentTask, error: String): ErrorHandlingResult =
        withContext(ioDispatcher) {
            val strategy = findStrategy(error)
            val metadata = buildMetadata(strategy)
            val record = ErrorRecord(
                id = UUID.randomUUID().toString(),
                taskId = task.id,
                error = error,
                metadata = metadata,
            )
            errorHistory.add(record)

            when (strategy) {
                is ErrorHandlingStrategy.Retry -> {
                    delay(strategy.delayMs)
                    ErrorHandlingResult.Recovered(
                        mapOf(
                            "taskId" to task.id,
                            "strategy" to "retry",
                            "retryCount" to "0",
                            "maxRetries" to strategy.maxRetries.toString(),
                        )
                    )
                }
                is ErrorHandlingStrategy.Fallback -> ErrorHandlingResult.Recovered(
                    mapOf(
                        "taskId" to task.id,
                        "strategy" to "fallback",
                        "fallbackType" to strategy.fallbackType,
                    )
                )
                is ErrorHandlingStrategy.Ignore -> ErrorHandlingResult.Ignored
                is ErrorHandlingStrategy.Fail -> ErrorHandlingResult.Failed(error)
                null -> ErrorHandlingResult.Failed(error)
            }
        }

    override fun registerStrategy(errorType: String, strategy: ErrorHandlingStrategy) {
        strategies[errorType] = strategy
    }

    override fun getErrorHistory(taskId: String?): List<ErrorRecord> {
        return if (taskId != null) {
            errorHistory.filter { it.taskId == taskId }
        } else {
            errorHistory.toList()
        }
    }

    private fun buildMetadata(strategy: ErrorHandlingStrategy?): Map<String, String> = when (strategy) {
        is ErrorHandlingStrategy.Retry -> mapOf(
            "strategy" to "retry",
            "maxRetries" to strategy.maxRetries.toString(),
            "delayMs" to strategy.delayMs.toString(),
        )
        is ErrorHandlingStrategy.Fallback -> mapOf(
            "strategy" to "fallback",
            "fallbackType" to strategy.fallbackType,
        )
        is ErrorHandlingStrategy.Ignore -> mapOf("strategy" to "ignore")
        is ErrorHandlingStrategy.Fail -> mapOf("strategy" to "fail")
        null -> emptyMap()
    }

    private fun findStrategy(error: String): ErrorHandlingStrategy? {
        for ((errorType, strategy) in strategies) {
            if (error.contains(errorType, ignoreCase = true)) {
                return strategy
            }
        }
        return null
    }
}
