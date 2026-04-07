package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackTypesTest {

    private val testInstant = Instant.fromEpochMilliseconds(1700000000000)

    @Test
    fun explicitFeedback_holdsAllFields() {
        val feedback = UserFeedback.Explicit(
            feedbackId = "fb1",
            timestamp = testInstant,
            sessionId = "s1",
            type = ExplicitFeedbackType.THUMBS_UP,
            rating = 5,
            comment = "Great",
            aspect = FeedbackAspect.ACCURACY,
        )
        assertEquals("fb1", feedback.feedbackId)
        assertEquals(ExplicitFeedbackType.THUMBS_UP, feedback.type)
        assertEquals(5, feedback.rating)
    }

    @Test
    fun implicitFeedback_holdsAllFields() {
        val feedback = UserFeedback.Implicit(
            feedbackId = "fb2",
            timestamp = testInstant,
            sessionId = "s1",
            action = ImplicitAction.REGENERATED,
            confidence = 0.9f,
        )
        assertEquals(ImplicitAction.REGENERATED, feedback.action)
        assertEquals(0.9f, feedback.confidence, 0.01f)
    }

    @Test
    fun allFeedbackEnumsHaveExpectedCount() {
        assertEquals(5, ExplicitFeedbackType.entries.size)
        assertEquals(7, FeedbackAspect.entries.size)
        assertEquals(6, ImplicitAction.entries.size)
    }
}
