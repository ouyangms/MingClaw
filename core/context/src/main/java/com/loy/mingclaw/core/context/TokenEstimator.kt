package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.context.Message

interface TokenEstimator {
    fun estimate(text: String): Int
    fun estimateMessages(messages: List<Message>): Int
}
