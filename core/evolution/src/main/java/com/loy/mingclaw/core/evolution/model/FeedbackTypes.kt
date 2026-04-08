package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class ExplicitFeedbackType { THUMBS_UP, THUMBS_DOWN, RATING, CORRECTION, SUGGESTION }

@Serializable
enum class FeedbackAspect { ACCURACY, RELEVANCE, COMPLETENESS, CLARITY, TIMELINESS, TONE, OVERALL }

@Serializable
enum class ImplicitAction { REGENERATED, EDITED, COPIED, IGNORED, FOLLOWED_UP, ABANDONED }

@Serializable
enum class Trend { IMPROVING, STABLE, DECLINING }

@Serializable
sealed class UserFeedback {
    abstract val feedbackId: String
    abstract val timestamp: Instant
    abstract val sessionId: String

    @Serializable
    data class Explicit(
        override val feedbackId: String,
        override val timestamp: Instant,
        override val sessionId: String,
        val type: ExplicitFeedbackType,
        val rating: Int,
        val comment: String,
        val aspect: FeedbackAspect,
    ) : UserFeedback()

    @Serializable
    data class Implicit(
        override val feedbackId: String,
        override val timestamp: Instant,
        override val sessionId: String,
        val action: ImplicitAction,
        val confidence: Float,
    ) : UserFeedback()
}

@Serializable
data class FeedbackSummary(
    val periodStart: Instant,
    val periodEnd: Instant,
    val totalFeedbacks: Int,
    val explicitCount: Int,
    val implicitCount: Int,
    val averageRating: Float,
    val ratingDistribution: Map<Int, Int>,
)
