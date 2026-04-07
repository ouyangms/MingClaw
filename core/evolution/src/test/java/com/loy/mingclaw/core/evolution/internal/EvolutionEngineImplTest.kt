package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.evolution.BehaviorEvolver
import com.loy.mingclaw.core.evolution.CapabilityEvolver
import com.loy.mingclaw.core.evolution.EvolutionTriggerManager
import com.loy.mingclaw.core.evolution.KnowledgeEvolver
import com.loy.mingclaw.core.evolution.model.ConsolidationResult
import com.loy.mingclaw.core.evolution.model.EvolutionContext
import com.loy.mingclaw.core.evolution.model.EvolutionPriority
import com.loy.mingclaw.core.evolution.model.EvolutionProposal
import com.loy.mingclaw.core.evolution.model.EvolutionState
import com.loy.mingclaw.core.evolution.model.EvolutionTrigger
import com.loy.mingclaw.core.evolution.model.EvolutionType
import com.loy.mingclaw.core.evolution.model.RuleUpdate
import com.loy.mingclaw.core.evolution.model.RuleUpdateType
import com.loy.mingclaw.core.kernel.EventBus
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvolutionEngineImplTest {

    private lateinit var stateMachine: EvolutionStateMachine
    private lateinit var triggerManager: EvolutionTriggerManager
    private lateinit var behaviorEvolver: BehaviorEvolver
    private lateinit var knowledgeEvolver: KnowledgeEvolver
    private lateinit var capabilityEvolver: CapabilityEvolver
    private lateinit var eventBus: EventBus
    private lateinit var engine: EvolutionEngineImpl

    @Before
    fun setup() {
        stateMachine = EvolutionStateMachine()
        triggerManager = mockk()
        behaviorEvolver = mockk()
        knowledgeEvolver = mockk()
        capabilityEvolver = mockk()
        eventBus = mockk(relaxed = true)
        engine = EvolutionEngineImpl(
            stateMachine, triggerManager, behaviorEvolver,
            knowledgeEvolver, capabilityEvolver, eventBus, Dispatchers.Unconfined,
        )
    }

    private val testContext = EvolutionContext(
        sessionId = "s1",
        feedbackScore = 0.2f,
        taskSuccessRate = 0.3f,
        memoryCount = 10,
        lastEvolution = null,
    )

    private val behaviorProposal = EvolutionProposal(
        id = "p1",
        type = EvolutionType.BEHAVIOR,
        description = "Optimize behavior",
        reason = "Low success rate",
        expectedImpact = "Better outcomes",
        priority = EvolutionPriority.HIGH,
        confidence = 0.8f,
    )

    private val knowledgeProposal = EvolutionProposal(
        id = "p2",
        type = EvolutionType.KNOWLEDGE,
        description = "Extract knowledge",
        reason = "New session data",
        expectedImpact = "More knowledge",
        priority = EvolutionPriority.MEDIUM,
        confidence = 0.7f,
    )

    @Test
    fun triggerEvolution_fullFlow_success() = runTest {
        coEvery {
            triggerManager.shouldTrigger(EvolutionTrigger.MANUAL, testContext)
        } returns true
        coEvery { triggerManager.performAnalysis() } returns Result.success(listOf(behaviorProposal))
        coEvery { behaviorEvolver.suggestRuleUpdates() } returns Result.success(
            listOf(
                RuleUpdate("u1", "rule-1", RuleUpdateType.ADD, "", "New rule", "Test", 0.9f),
            ),
        )
        coEvery { behaviorEvolver.applyRuleUpdates(any()) } returns Result.success(Unit)

        val result = engine.triggerEvolution(EvolutionTrigger.MANUAL, testContext)
        assertTrue(result.isSuccess)
        val results = result.getOrNull()!!
        assertEquals(1, results.size)
        assertTrue(results[0].success)
        assertTrue(stateMachine.currentState() is EvolutionState.Completed)
        verify { eventBus.publishAsync(any()) }
    }

    @Test
    fun triggerEvolution_triggerNotActivated() = runTest {
        coEvery { triggerManager.shouldTrigger(any(), any()) } returns false

        val result = engine.triggerEvolution(EvolutionTrigger.USER_FEEDBACK, testContext)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.size)
        assertTrue(stateMachine.currentState() is EvolutionState.Idle)
    }

    @Test
    fun triggerEvolution_handlesAnalyzerFailure() = runTest {
        coEvery { triggerManager.shouldTrigger(any(), any()) } returns true
        coEvery { triggerManager.performAnalysis() } returns Result.failure(
            RuntimeException("Analysis error"),
        )

        val result = engine.triggerEvolution(EvolutionTrigger.MANUAL, testContext)
        assertTrue(result.isFailure)
        assertTrue(stateMachine.currentState() is EvolutionState.Failed)
    }

    @Test
    fun approveAndApply_behaviorProposal_success() = runTest {
        // First set state to AwaitingApproval
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        stateMachine.transitionTo(EvolutionState.AwaitingApproval(listOf(behaviorProposal)))

        coEvery { behaviorEvolver.suggestRuleUpdates() } returns Result.success(
            listOf(
                RuleUpdate("u1", "rule-1", RuleUpdateType.ADD, "", "New rule", "Test", 0.9f),
            ),
        )
        coEvery { behaviorEvolver.applyRuleUpdates(any()) } returns Result.success(Unit)

        val result = engine.approveAndApply(listOf(behaviorProposal))
        assertTrue(result.isSuccess)
        val results = result.getOrNull()!!
        assertEquals(1, results.size)
        assertTrue(results[0].success)
        assertTrue(stateMachine.currentState() is EvolutionState.Completed)
    }

    @Test
    fun approveAndApply_knowledgeProposal_success() = runTest {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        stateMachine.transitionTo(EvolutionState.AwaitingApproval(listOf(knowledgeProposal)))

        coEvery { knowledgeEvolver.extractKnowledge("") } returns Result.success(emptyList())

        val result = engine.approveAndApply(listOf(knowledgeProposal))
        assertTrue(result.isSuccess)
        assertTrue(stateMachine.currentState() is EvolutionState.Completed)
    }

    @Test
    fun rejectProposals_transitionsToIdle() = runTest {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        stateMachine.transitionTo(EvolutionState.AwaitingApproval(emptyList()))

        engine.rejectProposals()
        assertEquals(EvolutionState.Idle, stateMachine.currentState())
    }

    @Test
    fun observeState_delegatesToStateMachine() {
        val flow = engine.observeState()
        assertNotNull(flow)
        assertEquals(EvolutionState.Idle, stateMachine.currentState())
    }
}
