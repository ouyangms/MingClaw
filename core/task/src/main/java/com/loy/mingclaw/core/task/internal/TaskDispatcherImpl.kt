package com.loy.mingclaw.core.task.internal

import com.loy.mingclaw.core.model.task.AgentTask
import com.loy.mingclaw.core.model.task.TaskResult
import com.loy.mingclaw.core.task.TaskDispatcher
import com.loy.mingclaw.core.task.TaskHandler
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TaskDispatcherImpl @Inject constructor() : TaskDispatcher {

    private val handlers = ConcurrentHashMap<String, TaskHandler>()

    override suspend fun dispatch(task: AgentTask): TaskResult {
        val handler = handlers[task.type]
            ?: return TaskResult.Failure("No handler registered for task type: ${task.type}")
        return handler.handle(task)
    }

    override fun registerHandler(type: String, handler: TaskHandler) {
        handlers[type] = handler
    }

    override fun unregisterHandler(type: String) {
        handlers.remove(type)
    }
}
