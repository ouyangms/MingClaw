package com.loy.mingclaw.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenBudgetTest {

    @Test
    fun `calculate allocates tokens with default system reserve`() {
        val budget = TokenBudget.calculate(maxTokens = 10000)
        assertEquals(10000, budget.totalTokens)
        assertEquals(1000, budget.systemTokens)
        assertEquals(1800, budget.memoryTokens)
        assertEquals(1800, budget.toolTokens)
        assertEquals(5400, budget.conversationTokens)
    }

    @Test
    fun `calculate allocates tokens with custom system reserve`() {
        val budget = TokenBudget.calculate(maxTokens = 5000, systemReserved = 500)
        assertEquals(5000, budget.totalTokens)
        assertEquals(500, budget.systemTokens)
        assertEquals(900, budget.memoryTokens)
        assertEquals(900, budget.toolTokens)
        assertEquals(2700, budget.conversationTokens)
    }

    @Test
    fun `calculate handles minimum tokens`() {
        val budget = TokenBudget.calculate(maxTokens = 1000, systemReserved = 0)
        assertEquals(1000, budget.totalTokens)
        assertEquals(0, budget.systemTokens)
        assertEquals(200, budget.memoryTokens)
        assertEquals(200, budget.toolTokens)
        assertEquals(600, budget.conversationTokens)
    }
}
