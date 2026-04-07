package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.evolution.model.AgentDecision
import com.loy.mingclaw.core.evolution.model.DecisionOutcome
import com.loy.mingclaw.core.evolution.model.RuleUpdate
import com.loy.mingclaw.core.evolution.model.RuleUpdateType
import com.loy.mingclaw.core.model.llm.ChatResponse
import com.loy.mingclaw.core.model.llm.LlmProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BehaviorEvolverImplTest {

    private lateinit var llmProvider: LlmProvider
    private lateinit var fileManager: EvolutionFileManager
    private lateinit var evolver: BehaviorEvolverImpl

    @Before
    fun setup() {
        llmProvider = mockk()
        fileManager = mockk(relaxed = true)
        evolver = BehaviorEvolverImpl(llmProvider, fileManager, Dispatchers.Unconfined)
    }

    @Test
    fun recordDecision_delegatesToFileManager() = runTest {
        val decision = AgentDecision(
            "d1",
            Clock.System.now(),
            "rule-1",
            "test",
            DecisionOutcome.SUCCESS,
        )
        val result = evolver.recordDecision(decision)
        assertTrue(result.isSuccess)
        coVerify { fileManager.writeDecision(decision) }
    }

    @Test
    fun analyzePatterns_callsLlmAndParsesResponse() = runTest {
        coEvery { fileManager.readDecisions(any()) } returns listOf(
            AgentDecision("d1", Clock.System.now(), "rule-1", "applied successfully", DecisionOutcome.SUCCESS),
        )
        coEvery { fileManager.readAgentRules() } returns "# Agent Rules\n## rule-1\nBe concise"
        coEvery { llmProvider.chat(any(), any(), any(), any()) } returns Result.success(
            ChatResponse(
                content = """{"decisionCount":1,"successRate":1.0,"improvementAreas":[],"suggestedRules":["Be more detailed"]}""",
            ),
        )

        val result = evolver.analyzePatterns()
        assertTrue(result.isSuccess)
        val analysis = result.getOrNull()!!
        assertEquals(1, analysis.decisionCount)
        assertEquals(1.0f, analysis.successRate, 0.01f)
        assertEquals(listOf("Be more detailed"), analysis.suggestedRules)
    }

    @Test
    fun analyzePatterns_handlesLlmFailure() = runTest {
        coEvery { fileManager.readDecisions(any()) } returns emptyList()
        coEvery { fileManager.readAgentRules() } returns ""
        coEvery { llmProvider.chat(any(), any(), any(), any()) } returns Result.failure(
            RuntimeException("LLM error"),
        )

        val result = evolver.analyzePatterns()
        assertTrue(result.isFailure)
    }

    @Test
    fun suggestRuleUpdates_callsLlmAndParsesResponse() = runTest {
        coEvery { fileManager.readDecisions(any()) } returns listOf(
            AgentDecision("d1", Clock.System.now(), "rule-1", "test", DecisionOutcome.SUCCESS),
        )
        coEvery { fileManager.readAgentRules() } returns "# Rules"
        // First call: analyzePatterns returns BehaviorAnalysis JSON
        // Second call: suggestRules returns list of RuleUpdate JSON
        coEvery {
            llmProvider.chat(any(), any(), any(), any())
        } returns Result.success(
            ChatResponse(
                content = """{"decisionCount":1,"successRate":1.0,"improvementAreas":[],"suggestedRules":["Be concise"]}""",
            ),
        ) andThen Result.success(
            ChatResponse(
                content = """[{"ruleId":"conciseness","updateType":"ADD","currentRule":"","proposedRule":"Be concise","reason":"Improve clarity","confidence":0.8}]""",
            ),
        )

        val result = evolver.suggestRuleUpdates()
        assertTrue(result.isSuccess)
        val updates = result.getOrNull()!!
        assertEquals(1, updates.size)
        assertEquals("conciseness", updates[0].ruleId)
        assertEquals(RuleUpdateType.ADD, updates[0].updateType)
        assertEquals(0.8f, updates[0].confidence, 0.01f)
    }

    @Test
    fun applyRuleUpdates_backsUpAndModifiesRules() = runTest {
        coEvery { fileManager.readAgentRules() } returns "# Agent Rules\n## existing\nOld rule"
        coEvery { fileManager.writeAgentRules(any()) } returns Unit
        coEvery { fileManager.backupCurrent(any()) } returns Unit

        val updates = listOf(
            RuleUpdate("u1", "new-rule", RuleUpdateType.ADD, "", "New rule content", "Test", 0.9f),
        )

        val result = evolver.applyRuleUpdates(updates)
        assertTrue(result.isSuccess)
        coVerify { fileManager.backupCurrent(any()) }
        coVerify {
            fileManager.writeAgentRules(match {
                it.contains("new-rule") && it.contains("New rule content")
            })
        }
    }

    @Test
    fun rollbackToVersion_restoresFromFileManager() = runTest {
        coEvery { fileManager.restoreVersion("v123") } returns Unit

        val result = evolver.rollbackToVersion("v123")
        assertTrue(result.isSuccess)
        coVerify { fileManager.restoreVersion("v123") }
    }
}
