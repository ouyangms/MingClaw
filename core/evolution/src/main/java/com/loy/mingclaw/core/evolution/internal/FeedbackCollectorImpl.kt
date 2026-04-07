package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.evolution.FeedbackCollector
import com.loy.mingclaw.core.evolution.model.FeedbackSummary
import com.loy.mingclaw.core.evolution.model.ImplicitAction
import com.loy.mingclaw.core.evolution.model.UserFeedback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class FeedbackCollectorImpl @Inject constructor(
    private val fileManager: EvolutionFileManager,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : FeedbackCollector {

    private val feedbackFlow = MutableSharedFlow<UserFeedback>(extraBufferCapacity = 64)

    private val implicitConfidenceMap = mapOf(
        ImplicitAction.REGENERATED to 0.9f,
        ImplicitAction.EDITED to 0.8f,
        ImplicitAction.FOLLOWED_UP to 0.6f,
        ImplicitAction.COPIED to 0.3f,
        ImplicitAction.IGNORED to 0.2f,
        ImplicitAction.ABANDONED to 0.7f,
    )

    override suspend fun collectExplicitFeedback(feedback: UserFeedback.Explicit): Result<Unit> {
        if (feedback.rating !in 1..5) {
            return Result.failure(IllegalArgumentException("Rating must be between 1 and 5, got ${feedback.rating}"))
        }
        val toSave = if (feedback.feedbackId.isBlank()) {
            feedback.copy(feedbackId = UUID.randomUUID().toString())
        } else {
            feedback
        }
        return try {
            fileManager.writeFeedback(toSave)
            feedbackFlow.tryEmit(toSave)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun collectImplicitFeedback(action: ImplicitAction, sessionId: String): Result<Unit> {
        val confidence = implicitConfidenceMap[action] ?: 0.5f
        val feedback = UserFeedback.Implicit(
            feedbackId = UUID.randomUUID().toString(),
            timestamp = Clock.System.now(),
            sessionId = sessionId,
            action = action,
            confidence = confidence,
        )
        return try {
            fileManager.writeFeedback(feedback)
            feedbackFlow.tryEmit(feedback)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFeedbackSummary(since: Instant): FeedbackSummary {
        val feedbacks = fileManager.readFeedbacks(since)
        val explicitFeedbacks = feedbacks.filterIsInstance<UserFeedback.Explicit>()
        val implicitFeedbacks = feedbacks.filterIsInstance<UserFeedback.Implicit>()

        val averageRating = if (explicitFeedbacks.isNotEmpty()) {
            explicitFeedbacks.map { it.rating }.average().toFloat()
        } else {
            0f
        }

        val ratingDistribution = explicitFeedbacks
            .groupingBy { it.rating }
            .eachCount()

        return FeedbackSummary(
            periodStart = since,
            periodEnd = Clock.System.now(),
            totalFeedbacks = feedbacks.size,
            explicitCount = explicitFeedbacks.size,
            implicitCount = implicitFeedbacks.size,
            averageRating = averageRating,
            ratingDistribution = ratingDistribution,
        )
    }

    override fun observeFeedback(): Flow<UserFeedback> = feedbackFlow
}
