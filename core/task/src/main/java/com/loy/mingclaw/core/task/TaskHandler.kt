package com.loy.mingclaw.core.task

import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.TaskResult

interface TaskHandler {
    suspend fun handle(task: AgentTask): TaskResult
    fun supportedType(): String
}
