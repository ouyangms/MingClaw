package com.loy.mingclaw.core.task

import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.ErrorHandlingResult
import com.loy.mingclaw.core.model.task.ErrorHandlingStrategy
import com.loy.mingclaw.core.model.task.ErrorRecord

interface ErrorHandler {
    suspend fun handleError(task: AgentTask, error: String): ErrorHandlingResult
    fun registerStrategy(errorType: String, strategy: ErrorHandlingStrategy)
    fun getErrorHistory(taskId: String?): List<ErrorRecord>
}
