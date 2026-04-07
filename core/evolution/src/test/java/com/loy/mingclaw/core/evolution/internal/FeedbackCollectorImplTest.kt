package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.evolution.model.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FeedbackCollectorImplTest {

    private lateinit var fileManager: EvolutionFileManager
    private lateinit var collector: FeedbackCollectorImpl

    @Before
    fun setup() {
        fileManager = mockk(relaxed = true)
        collector = FeedbackCollectorImpl(fileManager, Dispatchers.Unconfined)
    }

    @Test
    fun collectExplicitFeedback_delegatesToFileManager() = runTest {
        val feedback = UserFeedback.Explicit(
            feedbackId = "fb1",
            timestamp = Clock.System.now(),
            sessionId = "s1",
            type = ExplicitFeedbackType.THUMBS_UP,
            rating = 5,
            comment = "Great",
            aspect = FeedbackAspect.ACCURACY,
        )
        val result = collector.collectExplicitFeedback(feedback)
        assertTrue(result.isSuccess)
        coVerify { fileManager.writeFeedback(match { it.feedbackId == "fb1" }) }
    }

    @Test
    fun collectExplicitFeedback_validatesRating() = runTest {
        val lowRating = UserFeedback.Explicit(
            feedbackId = "fb1", timestamp = Clock.System.now(), sessionId = "s1",
            type = ExplicitFeedbackType.RATING, rating = 0, comment = "", aspect = FeedbackAspect.OVERALL,
        )
        assertTrue(collector.collectExplicitFeedback(lowRating).isFailure)

        val highRating = lowRating.copy(rating = 6)
        assertTrue(collector.collectExplicitFeedback(highRating).isFailure)
    }

    @Test
    fun collectImplicitFeedback_mapsConfidence() = runTest {
        val result = collector.collectImplicitFeedback(ImplicitAction.REGENERATED, "s1")
        assertTrue(result.isSuccess)
        coVerify { fileManager.writeFeedback(match {
            (it as UserFeedback.Implicit).action == ImplicitAction.REGENERATED &&
            it.confidence == 0.9f
        }) }
    }

    @Test
    fun collectImplicitFeedback_delegatesToFileManager() = runTest {
        val result = collector.collectImplicitFeedback(ImplicitAction.EDITED, "s1")
        assertTrue(result.isSuccess)
        coVerify { fileManager.writeFeedback(any()) }
    }

    @Test
    fun getFeedbackSummary_aggregatesCorrectly() = runTest {
        val now = Clock.System.now()
        val feedbacks: List<UserFeedback> = listOf(
            UserFeedback.Explicit("fb1", now, "s1", ExplicitFeedbackType.RATING, 4, "Good", FeedbackAspect.ACCURACY),
            UserFeedback.Explicit("fb2", now, "s1", ExplicitFeedbackType.RATING, 5, "Great", FeedbackAspect.OVERALL),
            UserFeedback.Implicit("fb3", now, "s1", ImplicitAction.COPIED, 0.3f),
        )
        coEvery { fileManager.readFeedbacks(any()) } returns feedbacks

        val summary = collector.getFeedbackSummary(Instant.DISTANT_PAST)
        assertEquals(3, summary.totalFeedbacks)
        assertEquals(2, summary.explicitCount)
        assertEquals(1, summary.implicitCount)
        assertEquals(4.5f, summary.averageRating, 0.01f)
    }

    @Test
    fun getFeedbackSummary_emptyPeriod() = runTest {
        coEvery { fileManager.readFeedbacks(any()) } returns emptyList()

        val summary = collector.getFeedbackSummary(Instant.DISTANT_PAST)
        assertEquals(0, summary.totalFeedbacks)
        assertEquals(0, summary.explicitCount)
        assertEquals(0, summary.implicitCount)
        assertEquals(0f, summary.averageRating, 0.01f)
    }

    @Test
    fun observeFeedback_emitsCollectedFeedback() = runTest {
        var collected: UserFeedback? = null
        val job = launch {
            collector.observeFeedback().collect { collected = it }
        }
        // Give the collector coroutine time to start subscribing
        delay(50)

        collector.collectExplicitFeedback(
            UserFeedback.Explicit("fb1", Clock.System.now(), "s1", ExplicitFeedbackType.THUMBS_UP, 5, "", FeedbackAspect.OVERALL)
        )

        delay(50)
        job.cancel()

        assertNotNull(collected)
        assertEquals("fb1", collected!!.feedbackId)
    }
}
