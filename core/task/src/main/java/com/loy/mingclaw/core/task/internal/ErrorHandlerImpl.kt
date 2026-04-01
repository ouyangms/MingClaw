package com.loy.mingclaw.core.task.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.ErrorHandlingResult
import com.loy.mingclaw.core.model.task.ErrorHandlingStrategy
import com.loy.mingclaw.core.model.task.ErrorRecord
import com.loy.mingclaw.core.task.ErrorHandler
import kotlinx.coroutines.CoroutineDispatcher
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
            val record = ErrorRecord(
                id = UUID.randomUUID().toString(),
                taskId = task.id,
                error = error,
            )
            errorHistory.add(record)

            val strategy = findStrategy(error)
            when (strategy) {
                is ErrorHandlingStrategy.Retry -> ErrorHandlingResult.Failed("Retry not yet supported in MVP: $error")
                is ErrorHandlingStrategy.Ignore -> ErrorHandlingResult.Ignored
                is ErrorHandlingStrategy.Fail -> ErrorHandlingResult.Failed(error)
                null -> ErrorHandlingResult.Failed(error)
                is ErrorHandlingStrategy.Fallback -> ErrorHandlingResult.Failed("Fallback not yet supported in MVP: $error")
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

    private fun findStrategy(error: String): ErrorHandlingStrategy? {
        for ((errorType, strategy) in strategies) {
            if (error.contains(errorType, ignoreCase = true)) {
                return strategy
            }
        }
        return null
    }
}
