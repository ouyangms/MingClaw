package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.evolution.BehaviorEvolver
import com.loy.mingclaw.core.evolution.CapabilityEvolver
import com.loy.mingclaw.core.evolution.EvolutionEngine
import com.loy.mingclaw.core.evolution.EvolutionTriggerManager
import com.loy.mingclaw.core.evolution.KnowledgeEvolver
import com.loy.mingclaw.core.evolution.model.EvolutionContext
import com.loy.mingclaw.core.evolution.model.EvolutionProposal
import com.loy.mingclaw.core.evolution.model.EvolutionResult
import com.loy.mingclaw.core.evolution.model.EvolutionState
import com.loy.mingclaw.core.evolution.model.EvolutionTrigger
import com.loy.mingclaw.core.evolution.model.EvolutionType
import com.loy.mingclaw.core.kernel.EventBus
import com.loy.mingclaw.core.model.Event
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EvolutionEngineImpl @Inject constructor(
    private val stateMachine: EvolutionStateMachine,
    private val triggerManager: EvolutionTriggerManager,
    private val behaviorEvolver: BehaviorEvolver,
    private val knowledgeEvolver: KnowledgeEvolver,
    private val capabilityEvolver: CapabilityEvolver,
    private val eventBus: EventBus,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : EvolutionEngine {

    override fun observeState(): Flow<EvolutionState> = stateMachine.observeState()

    override suspend fun triggerEvolution(
        trigger: EvolutionTrigger,
        context: EvolutionContext,
    ): Result<List<EvolutionResult>> = withContext(ioDispatcher) {
        try {
            // Step 1: Transition to Analyzing
            stateMachine.transitionTo(EvolutionState.Analyzing(trigger))
                .getOrElse { return@withContext Result.failure(it) }

            eventBus.publishAsync(Event.EvolutionTriggered(trigger.name, "Evolution triggered"))

            // Step 2: Check if should trigger
            if (!triggerManager.shouldTrigger(trigger, context)) {
                // Analyzing -> AwaitingApproval(empty) -> Idle
                stateMachine.transitionTo(EvolutionState.AwaitingApproval(emptyList()))
                stateMachine.transitionTo(EvolutionState.Idle)
                return@withContext Result.success(emptyList())
            }

            // Step 3: Perform analysis
            val proposalsResult = triggerManager.performAnalysis()
            val proposals = proposalsResult.getOrElse { error ->
                stateMachine.transitionTo(
                    EvolutionState.Failed(error.message ?: "Analysis failed"),
                )
                eventBus.publishAsync(
                    Event.EvolutionFailed("", error.message ?: "Analysis failed"),
                )
                return@withContext Result.failure(error)
            }

            // Step 4: Transition to AwaitingApproval
            stateMachine.transitionTo(EvolutionState.AwaitingApproval(proposals))
                .getOrElse { return@withContext Result.failure(it) }

            if (proposals.isEmpty()) {
                stateMachine.transitionTo(EvolutionState.Idle)
                return@withContext Result.success(emptyList())
            }

            // Step 5: Transition to Applying
            stateMachine.transitionTo(EvolutionState.Applying(proposals))
                .getOrElse { return@withContext Result.failure(it) }

            // Step 6: Apply proposals automatically (MVP: auto-approve)
            val results = applyProposals(proposals, context.sessionId)

            // Step 7: Transition to Completed
            stateMachine.transitionTo(EvolutionState.Completed(results))

            val evolutionId = UUID.randomUUID().toString()
            eventBus.publishAsync(
                Event.EvolutionCompleted(
                    evolutionId = evolutionId,
                    changes = results.flatMap { it.changes },
                ),
            )

            Result.success(results)
        } catch (e: Exception) {
            stateMachine.transitionTo(EvolutionState.Failed(e.message ?: "Unknown error"))
            eventBus.publishAsync(Event.EvolutionFailed("", e.message ?: "Unknown error"))
            Result.failure(e)
        }
    }

    override suspend fun approveAndApply(proposals: List<EvolutionProposal>): Result<List<EvolutionResult>> =
        withContext(ioDispatcher) {
            try {
                stateMachine.transitionTo(EvolutionState.Applying(proposals))
                    .getOrElse { return@withContext Result.failure(it) }

                val results = applyProposals(proposals, "")

                stateMachine.transitionTo(EvolutionState.Completed(results))

                val evolutionId = UUID.randomUUID().toString()
                eventBus.publishAsync(
                    Event.EvolutionCompleted(
                        evolutionId = evolutionId,
                        changes = results.flatMap { it.changes },
                    ),
                )

                Result.success(results)
            } catch (e: Exception) {
                stateMachine.transitionTo(EvolutionState.Failed(e.message ?: "Unknown error"))
                eventBus.publishAsync(Event.EvolutionFailed("", e.message ?: "Unknown error"))
                Result.failure(e)
            }
        }

    override suspend fun rejectProposals() {
        stateMachine.transitionTo(EvolutionState.Idle)
    }

    private suspend fun applyProposals(
        proposals: List<EvolutionProposal>,
        sessionId: String,
    ): List<EvolutionResult> {
        val results = mutableListOf<EvolutionResult>()

        for (proposal in proposals) {
            val result = when (proposal.type) {
                EvolutionType.BEHAVIOR -> applyBehaviorProposal(proposal)
                EvolutionType.KNOWLEDGE -> applyKnowledgeProposal(proposal, sessionId)
                EvolutionType.CAPABILITY -> applyCapabilityProposal(proposal)
            }
            results.add(result)
        }

        return results
    }

    private suspend fun applyBehaviorProposal(proposal: EvolutionProposal): EvolutionResult {
        val updatesResult = behaviorEvolver.suggestRuleUpdates()
        return if (updatesResult.isSuccess) {
            val updates = updatesResult.getOrNull()!!
            if (updates.isEmpty()) {
                EvolutionResult(proposal.id, true, listOf("No rule updates needed"))
            } else {
                val applyResult = behaviorEvolver.applyRuleUpdates(updates)
                if (applyResult.isSuccess) {
                    EvolutionResult(
                        proposal.id,
                        true,
                        updates.map { "${it.updateType}: ${it.ruleId}" },
                    )
                } else {
                    EvolutionResult(
                        proposal.id,
                        false,
                        emptyList(),
                        applyResult.exceptionOrNull()?.message,
                    )
                }
            }
        } else {
            EvolutionResult(
                proposal.id,
                false,
                emptyList(),
                updatesResult.exceptionOrNull()?.message,
            )
        }
    }

    private suspend fun applyKnowledgeProposal(
        proposal: EvolutionProposal,
        sessionId: String,
    ): EvolutionResult {
        val extractResult = knowledgeEvolver.extractKnowledge(sessionId)
        return if (extractResult.isSuccess) {
            val knowledge = extractResult.getOrNull()!!
            if (knowledge.isEmpty()) {
                EvolutionResult(proposal.id, true, listOf("No knowledge extracted"))
            } else {
                val consolidateResult = knowledgeEvolver.consolidateToMemory(knowledge)
                if (consolidateResult.isSuccess) {
                    val cr = consolidateResult.getOrNull()!!
                    EvolutionResult(
                        proposal.id,
                        true,
                        listOf("Added: ${cr.added}, Skipped: ${cr.skipped}"),
                    )
                } else {
                    EvolutionResult(
                        proposal.id,
                        false,
                        emptyList(),
                        consolidateResult.exceptionOrNull()?.message,
                    )
                }
            }
        } else {
            EvolutionResult(
                proposal.id,
                false,
                emptyList(),
                extractResult.exceptionOrNull()?.message,
            )
        }
    }

    private suspend fun applyCapabilityProposal(proposal: EvolutionProposal): EvolutionResult {
        val gapsResult = capabilityEvolver.identifyCapabilityGaps()
        return if (gapsResult.isSuccess) {
            val gaps = gapsResult.getOrNull()!!
            EvolutionResult(proposal.id, true, gaps.map { "Gap: ${it.capability}" })
        } else {
            EvolutionResult(
                proposal.id,
                false,
                emptyList(),
                gapsResult.exceptionOrNull()?.message,
            )
        }
    }
}
