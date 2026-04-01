package com.loy.mingclaw.core.data.repository

sealed class ChatStreamResult {
    data class Chunk(val content: String, val finishReason: String? = null) : ChatStreamResult()
    data class Complete(val fullContent: String) : ChatStreamResult()
    data class Error(val message: String) : ChatStreamResult()
}
