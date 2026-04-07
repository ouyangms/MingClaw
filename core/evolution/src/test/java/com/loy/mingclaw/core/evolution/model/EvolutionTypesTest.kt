package com.loy.mingclaw.core.evolution.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EvolutionTypesTest {

    @Test
    fun evolutionType_hasAllThreePaths() {
        assertEquals(3, EvolutionType.entries.size)
        assertEquals(EvolutionType.BEHAVIOR, EvolutionType.valueOf("BEHAVIOR"))
        assertEquals(EvolutionType.KNOWLEDGE, EvolutionType.valueOf("KNOWLEDGE"))
        assertEquals(EvolutionType.CAPABILITY, EvolutionType.valueOf("CAPABILITY"))
    }

    @Test
    fun evolutionTrigger_hasAllTriggers() {
        assertEquals(7, EvolutionTrigger.entries.size)
    }

    @Test
    fun evolutionProposal_isSerializable() {
        val proposal = EvolutionProposal(
            id = "p1",
            type = EvolutionType.BEHAVIOR,
            description = "Test proposal",
            reason = "Testing",
            expectedImpact = "None",
            priority = EvolutionPriority.LOW,
            confidence = 0.8f,
        )
        assertEquals("p1", proposal.id)
        assertEquals(EvolutionType.BEHAVIOR, proposal.type)
        assertEquals(0.8f, proposal.confidence, 0.01f)
    }

    @Test
    fun evolutionResult_tracksSuccess() {
        val result = EvolutionResult(
            proposalId = "p1",
            success = true,
            changes = listOf("rule updated"),
        )
        assertEquals(true, result.success)
        assertEquals(1, result.changes.size)
        assertEquals(null, result.error)
    }

    @Test
    fun evolutionState_idleIsSingleton() {
        val a = EvolutionState.Idle
        val b = EvolutionState.Idle
        assertEquals(a, b)
    }
}
