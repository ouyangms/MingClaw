package com.loy.mingclaw.core.plugin.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.plugin.Tool
import com.loy.mingclaw.core.model.plugin.ToolCategory
import com.loy.mingclaw.core.model.plugin.ToolResult
import com.loy.mingclaw.core.plugin.ToolRegistry
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ToolRegistryImpl @Inject constructor(
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : ToolRegistry {

    private val tools = ConcurrentHashMap<String, Tool>()
    private val toolsByCategory = ConcurrentHashMap<ToolCategory, MutableList<String>>()

    override fun registerTool(tool: Tool): Result<Unit> {
        val existing = tools.putIfAbsent(tool.toolId, tool)
        if (existing != null) {
            return Result.failure(IllegalArgumentException("Tool already registered: ${tool.toolId}"))
        }
        toolsByCategory.computeIfAbsent(tool.category) { mutableListOf() }.add(tool.toolId)
        return Result.success(Unit)
    }

    override fun unregisterTool(toolId: String): Result<Unit> {
        val tool = tools.remove(toolId)
            ?: return Result.failure(IllegalArgumentException("Tool not found: $toolId"))
        toolsByCategory[tool.category]?.remove(toolId)
        return Result.success(Unit)
    }

    override fun getTool(toolId: String): Tool? = tools[toolId]

    override fun getAllTools(): List<Tool> = tools.values.toList()

    override fun getToolsByCategory(category: ToolCategory): List<Tool> {
        val toolIds = toolsByCategory[category] ?: return emptyList()
        return toolIds.mapNotNull { tools[it] }
    }

    override fun searchTools(query: String): List<Tool> {
        val lowerQuery = query.lowercase()
        return tools.values.filter {
            it.name.lowercase().contains(lowerQuery) ||
                it.description.lowercase().contains(lowerQuery)
        }
    }

    override suspend fun executeTool(toolId: String, args: Map<String, Any>): ToolResult {
        val tool = tools[toolId]
            ?: return ToolResult.Error(message = "Tool not found: $toolId", code = "TOOL_NOT_FOUND")
        return try {
            tool.execute(args)
        } catch (e: Exception) {
            ToolResult.Error(message = e.message ?: "Tool execution failed", code = "EXECUTION_ERROR")
        }
    }
}
