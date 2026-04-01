package com.loy.mingclaw.core.task

import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.TaskResult

interface TaskDispatcher {
    suspend fun dispatch(task: AgentTask): TaskResult
    fun registerHandler(type: String, handler: TaskHandler)
    fun unregisterHandler(type: String)
}
