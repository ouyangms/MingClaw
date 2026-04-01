package com.loy.mingclaw.core.task

import com.loy.mingclaw.core.model.task.Workflow
import com.loy.mingclaw.core.model.task.WorkflowResult
import com.loy.mingclaw.core.model.task.WorkflowStatus

interface WorkflowEngine {
    suspend fun execute(workflow: Workflow): WorkflowResult
    fun getStatus(workflowId: String): WorkflowStatus?
}
