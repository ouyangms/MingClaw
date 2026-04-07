package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.FeedbackSummary
import com.loy.mingclaw.core.evolution.model.ImplicitAction
import com.loy.mingclaw.core.evolution.model.UserFeedback
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface FeedbackCollector {
    suspend fun collectExplicitFeedback(feedback: UserFeedback.Explicit): Result<Unit>
    suspend fun collectImplicitFeedback(action: ImplicitAction, sessionId: String): Result<Unit>
    suspend fun getFeedbackSummary(since: Instant): FeedbackSummary
    fun observeFeedback(): Flow<UserFeedback>
}
