package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.evolution.model.CapabilityGap
import com.loy.mingclaw.core.evolution.model.Priority
import com.loy.mingclaw.core.evolution.model.SkillLevel
import com.loy.mingclaw.core.model.llm.ChatResponse
import com.loy.mingclaw.core.model.llm.LlmProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CapabilityEvolverImplTest {

    private lateinit var llmProvider: LlmProvider
    private lateinit var evolver: CapabilityEvolverImpl

    @Before
    fun setup() {
        llmProvider = mockk()
        evolver = CapabilityEvolverImpl(llmProvider, Dispatchers.Unconfined)
    }

    @Test
    fun identifyCapabilityGaps_returnsEmptyList_whenNoGaps() = runTest {
        coEvery { llmProvider.chat(model = any(), messages = any(), temperature = any()) } returns Result.success(
            ChatResponse(content = "[]"),
        )
        val result = evolver.identifyCapabilityGaps()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<CapabilityGap>(), result.getOrNull())
    }

    @Test
    fun identifyCapabilityGaps_parsesGapsFromLlmResponse() = runTest {
        val json = """[{"capability":"web_search","currentLevel":"NONE","desiredLevel":"INTERMEDIATE","priority":"HIGH"}]"""
        coEvery { llmProvider.chat(model = any(), messages = any(), temperature = any()) } returns Result.success(
            ChatResponse(content = json),
        )
        val result = evolver.identifyCapabilityGaps()
        assertTrue(result.isSuccess)
        val gaps = result.getOrNull()!!
        assertEquals(1, gaps.size)
        assertEquals("web_search", gaps[0].capability)
        assertEquals(SkillLevel.NONE, gaps[0].currentLevel)
        assertEquals(SkillLevel.INTERMEDIATE, gaps[0].desiredLevel)
        assertEquals(Priority.HIGH, gaps[0].priority)
    }

    @Test
    fun identifyCapabilityGaps_returnsEmptyList_onLlmFailure() = runTest {
        coEvery { llmProvider.chat(model = any(), messages = any(), temperature = any()) } returns Result.failure(
            RuntimeException("LLM error"),
        )
        val result = evolver.identifyCapabilityGaps()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<CapabilityGap>(), result.getOrNull())
    }

    @Test
    fun identifyCapabilityGaps_handlesInvalidJson() = runTest {
        coEvery { llmProvider.chat(model = any(), messages = any(), temperature = any()) } returns Result.success(
            ChatResponse(content = "not valid json"),
        )
        val result = evolver.identifyCapabilityGaps()
        assertTrue(result.isSuccess)
        assertEquals(emptyList<CapabilityGap>(), result.getOrNull())
    }
}
