package com.loy.mingclaw.core.model.plugin

interface Tool {
    val toolId: String
    val name: String
    val description: String
    val category: ToolCategory
    val parameters: Map<String, ToolParameter>
    val requiresConfirmation: Boolean

    suspend fun execute(args: Map<String, Any>): ToolResult
}

sealed interface ToolResult {
    data class Success(val data: Any, val format: ResultFormat = ResultFormat.Text) : ToolResult
    data class Error(val message: String, val code: String? = null) : ToolResult
    data class Partial(val progress: Float, val message: String? = null, val data: Any? = null) : ToolResult
}

enum class ResultFormat {
    Text, Json, Markdown, Html, Binary
}
