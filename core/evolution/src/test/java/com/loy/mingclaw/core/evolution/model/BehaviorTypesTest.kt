package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorTypesTest {

    private val testInstant = Instant.fromEpochMilliseconds(1700000000000)

    @Test
    fun agentDecision_roundTripViaCopy() {
        val decision = AgentDecision(
            decisionId = "d1",
            timestamp = testInstant,
            ruleApplied = "rule-1",
            reasoning = "test",
            outcome = DecisionOutcome.SUCCESS,
        )
        val copy = decision.copy(outcome = DecisionOutcome.FAILURE)
        assertEquals(DecisionOutcome.FAILURE, copy.outcome)
        assertEquals("d1", copy.decisionId)
    }

    @Test
    fun ruleUpdate_hasAllUpdateTypes() {
        assertEquals(5, RuleUpdateType.entries.size)
    }

    @Test
    fun behaviorAnalysis_summarizesCorrectly() {
        val analysis = BehaviorAnalysis(
            decisionCount = 10,
            successRate = 0.8f,
            improvementAreas = listOf("response length"),
            suggestedRules = listOf("be concise"),
        )
        assertEquals(10, analysis.decisionCount)
        assertEquals(0.8f, analysis.successRate, 0.01f)
    }
}
