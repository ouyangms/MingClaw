package com.loy.mingclaw.core.plugin

import com.loy.mingclaw.core.model.plugin.Tool
import com.loy.mingclaw.core.model.plugin.ToolCategory
import com.loy.mingclaw.core.model.plugin.ToolResult

interface ToolRegistry {
    fun registerTool(tool: Tool): Result<Unit>
    fun unregisterTool(toolId: String): Result<Unit>
    fun getTool(toolId: String): Tool?
    fun getAllTools(): List<Tool>
    fun getToolsByCategory(category: ToolCategory): List<Tool>
    fun searchTools(query: String): List<Tool>
    suspend fun executeTool(toolId: String, args: Map<String, Any>): ToolResult
}
