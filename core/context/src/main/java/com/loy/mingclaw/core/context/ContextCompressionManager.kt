package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.context.model.CompressedContext
import com.loy.mingclaw.core.model.context.Message

interface ContextCompressionManager {
    suspend fun compressHistory(messages: List<Message>, maxTokens: Int): Result<CompressedContext>
}
