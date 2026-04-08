package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.context.internal.ContextWindowManagerImpl
import com.loy.mingclaw.core.context.internal.TokenEstimatorImpl
import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.model.KernelConfig
import com.loy.mingclaw.core.model.TokenBudget
import com.loy.mingclaw.core.model.context.ContextComponent
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextWindowManagerImplTest {
    private lateinit var windowManager: ContextWindowManagerImpl

    @Before
    fun setup() {
        val configManager = object : ConfigManager {
            override fun getConfig() = KernelConfig()
            override fun updateConfig(updates: Map<String, Any>) = Result.success(KernelConfig())
            override fun resetToDefault() = KernelConfig()
            override fun watchConfigChanges() = flowOf(KernelConfig())
        }
        windowManager = ContextWindowManagerImpl(TokenEstimatorImpl(), configManager)
    }

    @Test
    fun `calculateTokenBudget returns valid budget`() {
        val budget = windowManager.calculateTokenBudget()
        assertEquals(8192, budget.totalTokens)
        assertEquals(1000, budget.systemTokens)
        assertTrue(budget.memoryTokens > 0)
        assertTrue(budget.conversationTokens > 0)
    }

    @Test
    fun `allocateTokenBudget distributes by priority`() {
        val budget = TokenBudget.calculate(10000)
        val components = listOf(
            ContextComponent(id = "system", name = "System", requestedTokens = 1000, priority = 3),
            ContextComponent(id = "memory", name = "Memory", requestedTokens = 2000, priority = 2),
            ContextComponent(id = "chat", name = "Chat", requestedTokens = 5000, priority = 1),
        )
        val allocation = windowManager.allocateTokenBudget(budget, components)
        assertEquals(3, allocation.size)
        assertTrue(allocation["system"]!! > 0)
    }

    @Test
    fun `estimateTokens delegates to estimator`() {
        assertTrue(windowManager.estimateTokens("Hello, world!") > 0)
    }

    @Test
    fun `shouldCompress returns false for small messages`() {
        val budget = windowManager.calculateTokenBudget()
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "Hi"),
        )
        assertFalse(windowManager.shouldCompress(messages, budget))
    }

    @Test
    fun `shouldCompress returns true for large messages`() {
        val budget = windowManager.calculateTokenBudget()
        val largeMessages = (1..500).map { i ->
            Message(id = "$i", sessionId = "s1", role = MessageRole.User, content = "This is message number $i with some extra text to make it longer.")
        }
        assertTrue(windowManager.shouldCompress(largeMessages, budget))
    }

    @Test
    fun `getWindowStatistics returns default stats`() {
        val stats = windowManager.getWindowStatistics()
        assertEquals(0.0, stats.averageTokenUsage, 0.01)
        assertEquals(0, stats.peakTokenUsage)
    }
}
