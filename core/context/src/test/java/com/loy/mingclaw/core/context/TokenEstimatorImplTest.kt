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
    fun `estimate returns zero for empty text`() {
        assertEquals(0, estimator.estimate(""))
    }

    @Test
    fun `estimate for ASCII text uses word-level heuristic`() {
        val result = estimator.estimate("Hello world")
        assertTrue("ASCII text should estimate > 0", result > 0)
    }

    @Test
    fun `estimate for CJK text uses character-level heuristic`() {
        val result = estimator.estimate("你好世界")
        assertTrue("CJK text should estimate > 0", result > 0)
    }

    @Test
    fun `estimate for CJK text is higher than same-length ASCII`() {
        val cjk = estimator.estimate("你好你好") // 4 CJK chars
        val ascii = estimator.estimate("abcd")   // 4 ASCII chars
        assertTrue("CJK should estimate more tokens than same-length ASCII", cjk > ascii)
    }

    @Test
    fun `estimate for mixed CJK and ASCII text`() {
        val result = estimator.estimate("Hello 你好 world 世界")
        assertTrue("Mixed text should estimate > 0", result > 0)
    }

    @Test
    fun `estimate scales with text length`() {
        val short = estimator.estimate("Hi")
        val long = estimator.estimate("This is a much longer sentence with many more words in it.")
        assertTrue(long > short)
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

    @Test
    fun `estimate for whitespace-only text returns zero`() {
        assertEquals(0, estimator.estimate("   "))
    }

    @Test
    fun `estimate for long CJK text scales proportionally`() {
        val short = estimator.estimate("你好")
        val long = estimator.estimate("你好世界这是一个测试文本用于验证中文token估算")
        assertTrue("Long CJK text should estimate more tokens", long > short)
    }
}
