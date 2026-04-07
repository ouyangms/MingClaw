package com.loy.mingclaw.core.evolution.internal

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CapabilityEvolverImplTest {

    private val evolver = CapabilityEvolverImpl()

    @Test
    fun identifyCapabilityGaps_returnsEmptyList() = runTest {
        val result = evolver.identifyCapabilityGaps()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<com.loy.mingclaw.core.evolution.model.CapabilityGap>(), result.getOrNull())
    }
}
