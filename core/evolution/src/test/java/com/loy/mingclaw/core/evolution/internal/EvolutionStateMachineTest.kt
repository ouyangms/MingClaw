package com.loy.mingclaw.core.evolution.internal

import app.cash.turbine.test
import com.loy.mingclaw.core.evolution.model.EvolutionPriority
import com.loy.mingclaw.core.evolution.model.EvolutionProposal
import com.loy.mingclaw.core.evolution.model.EvolutionResult
import com.loy.mingclaw.core.evolution.model.EvolutionState
import com.loy.mingclaw.core.evolution.model.EvolutionTrigger
import com.loy.mingclaw.core.evolution.model.EvolutionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvolutionStateMachineTest {

    private lateinit var stateMachine: EvolutionStateMachine

    @Before
    fun setup() {
        stateMachine = EvolutionStateMachine()
    }

    @Test
    fun initialState_isIdle() {
        assertEquals(EvolutionState.Idle, stateMachine.currentState())
    }

    @Test
    fun idle_toAnalyzing_succeeds() {
        val result = stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        assertTrue(result.isSuccess)
        assertTrue(stateMachine.currentState() is EvolutionState.Analyzing)
    }

    @Test
    fun idle_toCompleted_fails() {
        val result = stateMachine.transitionTo(EvolutionState.Completed(emptyList()))
        assertTrue(result.isFailure)
        assertEquals(EvolutionState.Idle, stateMachine.currentState())
    }

    @Test
    fun analyzing_toAwaitingApproval_succeeds() {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        val result = stateMachine.transitionTo(EvolutionState.AwaitingApproval(emptyList()))
        assertTrue(result.isSuccess)
        assertTrue(stateMachine.currentState() is EvolutionState.AwaitingApproval)
    }

    @Test
    fun analyzing_toFailed_succeeds() {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        val result = stateMachine.transitionTo(EvolutionState.Failed("error"))
        assertTrue(result.isSuccess)
        assertTrue(stateMachine.currentState() is EvolutionState.Failed)
    }

    @Test
    fun analyzing_toIdle_fails() {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        val result = stateMachine.transitionTo(EvolutionState.Idle)
        assertTrue(result.isFailure)
    }

    @Test
    fun awaitingApproval_toApplying_succeeds() {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        stateMachine.transitionTo(EvolutionState.AwaitingApproval(emptyList()))
        val result = stateMachine.transitionTo(EvolutionState.Applying(emptyList()))
        assertTrue(result.isSuccess)
        assertTrue(stateMachine.currentState() is EvolutionState.Applying)
    }

    @Test
    fun awaitingApproval_toIdle_succeeds() {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        stateMachine.transitionTo(EvolutionState.AwaitingApproval(emptyList()))
        val result = stateMachine.transitionTo(EvolutionState.Idle)
        assertTrue(result.isSuccess)
        assertEquals(EvolutionState.Idle, stateMachine.currentState())
    }

    @Test
    fun awaitingApproval_toAnalyzing_fails() {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        stateMachine.transitionTo(EvolutionState.AwaitingApproval(emptyList()))
        val result = stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        assertTrue(result.isFailure)
    }

    @Test
    fun applying_toCompleted_succeeds() {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        stateMachine.transitionTo(EvolutionState.AwaitingApproval(emptyList()))
        stateMachine.transitionTo(EvolutionState.Applying(emptyList()))
        val result = stateMachine.transitionTo(EvolutionState.Completed(emptyList()))
        assertTrue(result.isSuccess)
        assertTrue(stateMachine.currentState() is EvolutionState.Completed)
    }

    @Test
    fun applying_toFailed_succeeds() {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        stateMachine.transitionTo(EvolutionState.AwaitingApproval(emptyList()))
        stateMachine.transitionTo(EvolutionState.Applying(emptyList()))
        val result = stateMachine.transitionTo(EvolutionState.Failed("error"))
        assertTrue(result.isSuccess)
        assertTrue(stateMachine.currentState() is EvolutionState.Failed)
    }

    @Test
    fun completed_toIdle_succeeds() {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        stateMachine.transitionTo(EvolutionState.AwaitingApproval(emptyList()))
        stateMachine.transitionTo(EvolutionState.Applying(emptyList()))
        stateMachine.transitionTo(EvolutionState.Completed(emptyList()))
        val result = stateMachine.transitionTo(EvolutionState.Idle)
        assertTrue(result.isSuccess)
        assertEquals(EvolutionState.Idle, stateMachine.currentState())
    }

    @Test
    fun completed_toAnalyzing_succeeds() {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        stateMachine.transitionTo(EvolutionState.AwaitingApproval(emptyList()))
        stateMachine.transitionTo(EvolutionState.Applying(emptyList()))
        stateMachine.transitionTo(EvolutionState.Completed(emptyList()))
        val result = stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.SCHEDULED))
        assertTrue(result.isSuccess)
    }

    @Test
    fun failed_toIdle_succeeds() {
        stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
        stateMachine.transitionTo(EvolutionState.Failed("error"))
        val result = stateMachine.transitionTo(EvolutionState.Idle)
        assertTrue(result.isSuccess)
        assertEquals(EvolutionState.Idle, stateMachine.currentState())
    }

    @Test
    fun observeState_emitsTransitions() = runTest {
        stateMachine.observeState().test {
            assertEquals(EvolutionState.Idle, awaitItem())
            stateMachine.transitionTo(EvolutionState.Analyzing(EvolutionTrigger.MANUAL))
            assertTrue(awaitItem() is EvolutionState.Analyzing)
            stateMachine.transitionTo(EvolutionState.AwaitingApproval(emptyList()))
            assertTrue(awaitItem() is EvolutionState.AwaitingApproval)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
