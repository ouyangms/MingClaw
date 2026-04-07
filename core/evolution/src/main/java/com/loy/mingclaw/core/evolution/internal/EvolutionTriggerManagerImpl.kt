package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.evolution.BehaviorEvolver
import com.loy.mingclaw.core.evolution.CapabilityEvolver
import com.loy.mingclaw.core.evolution.EvolutionTriggerManager
import com.loy.mingclaw.core.evolution.FeedbackCollector
import com.loy.mingclaw.core.evolution.KnowledgeEvolver
import com.loy.mingclaw.core.evolution.model.EvolutionContext
import com.loy.mingclaw.core.evolution.model.EvolutionPriority
import com.loy.mingclaw.core.evolution.model.EvolutionProposal
import com.loy.mingclaw.core.evolution.model.EvolutionTrigger
import com.loy.mingclaw.core.evolution.model.EvolutionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EvolutionTriggerManagerImpl @Inject constructor(
    private val feedbackCollector: FeedbackCollector,
    private val behaviorEvolver: BehaviorEvolver,
    private val knowledgeEvolver: KnowledgeEvolver,
    private val capabilityEvolver: CapabilityEvolver,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : EvolutionTriggerManager {

    override suspend fun shouldTrigger(
        trigger: EvolutionTrigger,
        context: EvolutionContext,
    ): Boolean = when (trigger) {
        EvolutionTrigger.USER_FEEDBACK -> context.feedbackScore < 0.3f
        EvolutionTrigger.TASK_FAILURE -> context.taskSuccessRate < 0.5f
        EvolutionTrigger.PERFORMANCE_DEGRADATION -> context.feedbackScore < 0.4f
        EvolutionTrigger.KNOWLEDGE_THRESHOLD -> context.memoryCount > 50 &&
            (context.lastEvolution == null ||
                context.lastEvolution < Clock.System.now()
                    .minus(DateTimePeriod(hours = 1), TimeZone.UTC))
        EvolutionTrigger.CAPABILITY_GAP -> false // MVP: no skill marketplace
        EvolutionTrigger.SCHEDULED -> true
        EvolutionTrigger.MANUAL -> true
    }

    override suspend fun performAnalysis(): Result<List<EvolutionProposal>> =
        withContext(ioDispatcher) {
            val proposals = mutableListOf<EvolutionProposal>()

            // Behavior analysis
            val behaviorResult = behaviorEvolver.analyzePatterns()
            if (behaviorResult.isSuccess) {
                val analysis = behaviorResult.getOrNull()!!
                if (analysis.improvementAreas.isNotEmpty()) {
                    proposals.add(
                        EvolutionProposal(
                            id = UUID.randomUUID().toString(),
                            type = EvolutionType.BEHAVIOR,
                            description = "Behavior optimization based on ${analysis.decisionCount} decisions",
                            reason = "Improvement areas: ${analysis.improvementAreas.joinToString(", ")}",
                            expectedImpact = "Success rate: ${(analysis.successRate * 100).toInt()}%",
                            priority = if (analysis.successRate < 0.5f) {
                                EvolutionPriority.HIGH
                            } else {
                                EvolutionPriority.MEDIUM
                            },
                            confidence = analysis.successRate.coerceIn(0f, 1f),
                        ),
                    )
                }
            }

            // Feedback summary
            val recent = Clock.System.now()
                .minus(DateTimePeriod(days = 7), TimeZone.UTC)
            val summary = feedbackCollector.getFeedbackSummary(recent)
            if (summary.averageRating < 3f && summary.totalFeedbacks > 0) {
                proposals.add(
                    EvolutionProposal(
                        id = UUID.randomUUID().toString(),
                        type = EvolutionType.BEHAVIOR,
                        description = "Address low feedback ratings",
                        reason = "Average rating: ${summary.averageRating} from ${summary.totalFeedbacks} feedbacks",
                        expectedImpact = "Improve user satisfaction",
                        priority = EvolutionPriority.HIGH,
                        confidence = 0.7f,
                    ),
                )
            }

            // Capability analysis (MVP - no gaps detected)
            val capabilityResult = capabilityEvolver.identifyCapabilityGaps()
            if (capabilityResult.isSuccess) {
                val gaps = capabilityResult.getOrNull()!!
                for (gap in gaps) {
                    proposals.add(
                        EvolutionProposal(
                            id = UUID.randomUUID().toString(),
                            type = EvolutionType.CAPABILITY,
                            description = "Address capability gap: ${gap.capability}",
                            reason = "Current level: ${gap.currentLevel}, desired: ${gap.desiredLevel}",
                            expectedImpact = "Fill capability gap",
                            priority = when (gap.priority) {
                                com.loy.mingclaw.core.evolution.model.Priority.CRITICAL -> EvolutionPriority.IMMEDIATE
                                com.loy.mingclaw.core.evolution.model.Priority.HIGH -> EvolutionPriority.HIGH
                                com.loy.mingclaw.core.evolution.model.Priority.MEDIUM -> EvolutionPriority.MEDIUM
                                com.loy.mingclaw.core.evolution.model.Priority.LOW -> EvolutionPriority.LOW
                            },
                            confidence = 0.6f,
                        ),
                    )
                }
            }

            Result.success(proposals)
        }
}
