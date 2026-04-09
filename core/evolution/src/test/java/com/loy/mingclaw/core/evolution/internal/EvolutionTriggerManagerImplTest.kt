package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.evolution.BehaviorEvolver
import com.loy.mingclaw.core.evolution.CapabilityEvolver
import com.loy.mingclaw.core.evolution.FeedbackCollector
import com.loy.mingclaw.core.evolution.KnowledgeEvolver
import com.loy.mingclaw.core.evolution.model.BehaviorAnalysis
import com.loy.mingclaw.core.evolution.model.EvolutionContext
import com.loy.mingclaw.core.evolution.model.EvolutionTrigger
import com.loy.mingclaw.core.evolution.model.EvolutionType
import com.loy.mingclaw.core.evolution.model.FeedbackSummary
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EvolutionTriggerManagerImplTest {

    private lateinit var feedbackCollector: FeedbackCollector
    private lateinit var behaviorEvolver: BehaviorEvolver
    private lateinit var knowledgeEvolver: KnowledgeEvolver
    private lateinit var capabilityEvolver: CapabilityEvolver
    private lateinit var triggerManager: EvolutionTriggerManagerImpl

    @Before
    fun setup() {
        feedbackCollector = mockk()
        behaviorEvolver = mockk()
        knowledgeEvolver = mockk()
        capabilityEvolver = mockk()
        triggerManager = EvolutionTriggerManagerImpl(
            feedbackCollector, behaviorEvolver, knowledgeEvolver,
            capabilityEvolver, Dispatchers.Unconfined,
        )
    }

    private val baseContext = EvolutionContext(
        sessionId = "s1",
        feedbackScore = 0.5f,
        taskSuccessRate = 0.8f,
        memoryCount = 10,
        lastEvolution = null,
    )

    @Test
    fun shouldTrigger_userFeedback_lowScore_returnsTrue() = runTest {
        val context = baseContext.copy(feedbackScore = 0.2f)
        assertTrue(triggerManager.shouldTrigger(EvolutionTrigger.USER_FEEDBACK, context))
    }

    @Test
    fun shouldTrigger_userFeedback_highScore_returnsFalse() = runTest {
        val context = baseContext.copy(feedbackScore = 0.8f)
        assertFalse(triggerManager.shouldTrigger(EvolutionTrigger.USER_FEEDBACK, context))
    }

    @Test
    fun shouldTrigger_manual_alwaysTrue() = runTest {
        assertTrue(triggerManager.shouldTrigger(EvolutionTrigger.MANUAL, baseContext))
    }

    @Test
    fun shouldTrigger_capabilityGap_lowSuccessRate_returnsTrue() = runTest {
        val context = baseContext.copy(taskSuccessRate = 0.3f)
        assertTrue(triggerManager.shouldTrigger(EvolutionTrigger.CAPABILITY_GAP, context))
    }

    @Test
    fun shouldTrigger_capabilityGap_highSuccessRate_returnsFalse() = runTest {
        val context = baseContext.copy(taskSuccessRate = 0.8f)
        assertFalse(triggerManager.shouldTrigger(EvolutionTrigger.CAPABILITY_GAP, context))
    }

    @Test
    fun shouldTrigger_taskFailure_lowRate_returnsTrue() = runTest {
        val context = baseContext.copy(taskSuccessRate = 0.3f)
        assertTrue(triggerManager.shouldTrigger(EvolutionTrigger.TASK_FAILURE, context))
    }

    @Test
    fun shouldTrigger_performanceDegradation_returnsTrue() = runTest {
        val context = baseContext.copy(feedbackScore = 0.35f)
        assertTrue(triggerManager.shouldTrigger(EvolutionTrigger.PERFORMANCE_DEGRADATION, context))
    }

    @Test
    fun performAnalysis_returnsProposalsFromBehaviorAnalysis() = runTest {
        coEvery { behaviorEvolver.analyzePatterns() } returns Result.success(
            BehaviorAnalysis(
                decisionCount = 10,
                successRate = 0.6f,
                improvementAreas = listOf("response length"),
                suggestedRules = listOf("be concise"),
            ),
        )
        coEvery { feedbackCollector.getFeedbackSummary(any()) } returns FeedbackSummary(
            periodStart = Clock.System.now(),
            periodEnd = Clock.System.now(),
            totalFeedbacks = 0,
            explicitCount = 0,
            implicitCount = 0,
            averageRating = 4f,
            ratingDistribution = emptyMap(),
        )
        coEvery { capabilityEvolver.identifyCapabilityGaps() } returns Result.success(emptyList())

        val result = triggerManager.performAnalysis()
        assertTrue(result.isSuccess)
        val proposals = result.getOrNull()!!
        assertTrue(proposals.any { it.type == EvolutionType.BEHAVIOR })
    }

    @Test
    fun performAnalysis_handlesEvolverFailure() = runTest {
        coEvery { behaviorEvolver.analyzePatterns() } returns Result.failure(
            RuntimeException("error"),
        )
        coEvery { feedbackCollector.getFeedbackSummary(any()) } returns FeedbackSummary(
            periodStart = Clock.System.now(),
            periodEnd = Clock.System.now(),
            totalFeedbacks = 0,
            explicitCount = 0,
            implicitCount = 0,
            averageRating = 0f,
            ratingDistribution = emptyMap(),
        )
        coEvery { capabilityEvolver.identifyCapabilityGaps() } returns Result.success(emptyList())

        val result = triggerManager.performAnalysis()
        assertTrue(result.isSuccess)
        // Should still return proposals from other sources (none in this case)
        val proposals = result.getOrNull()!!
        assertTrue(proposals.isEmpty())
    }
}
