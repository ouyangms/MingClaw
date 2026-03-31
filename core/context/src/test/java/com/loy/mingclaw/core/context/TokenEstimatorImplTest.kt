package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.context.internal.TokenEstimatorImpl
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenEstimatorImplTest {
    private lateinit var estimator: TokenEstimatorImpl

    @Before
    fun setup() { estimator = TokenEstimatorImpl() }

    @Test
    fun `estimate returns positive for non-empty text`() {
        assertTrue(estimator.estimate("Hello, how are you?") > 0)
    }

    @Test
    fun `estimate returns zero for empty text`() {
        assertEquals(0, estimator.estimate(""))
    }

    @Test
    fun `estimate scales with text length`() {
        val short = estimator.estimate("Hi")
        val long = estimator.estimate("This is a much longer sentence with many more words in it.")
        assertTrue(long > short)
    }

    @Test
    fun `estimate uses chars-per-token heuristic`() {
        assertEquals(1, estimator.estimate("abcd"))
    }

    @Test
    fun `estimateMessages sums message tokens`() {
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "Hello there"),
            Message(id = "2", sessionId = "s1", role = MessageRole.Assistant, content = "Hi! How can I help?"),
        )
        val total = estimator.estimateMessages(messages)
        val expected = estimator.estimate("Hello there") + estimator.estimate("Hi! How can I help?")
        assertEquals(expected, total)
    }
}
