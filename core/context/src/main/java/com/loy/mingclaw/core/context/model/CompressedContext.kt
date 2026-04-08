package com.loy.mingclaw.core.context.model

import com.loy.mingclaw.core.model.context.Message

data class CompressedContext(
    val summary: String,
    val summaryTokenCount: Int,
    val retainedMessages: List<Message>,
)
