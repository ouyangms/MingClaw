package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.model.context.Message
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TokenEstimatorImpl @Inject constructor() : TokenEstimator {
    private val charsPerToken = 4

    override fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length + charsPerToken - 1) / charsPerToken
    }

    override fun estimateMessages(messages: List<Message>): Int {
        return messages.sumOf { estimate(it.content) }
    }
}
