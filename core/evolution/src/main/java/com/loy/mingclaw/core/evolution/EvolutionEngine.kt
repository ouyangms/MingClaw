package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.EvolutionContext
import com.loy.mingclaw.core.evolution.model.EvolutionProposal
import com.loy.mingclaw.core.evolution.model.EvolutionResult
import com.loy.mingclaw.core.evolution.model.EvolutionState
import com.loy.mingclaw.core.evolution.model.EvolutionTrigger
import kotlinx.coroutines.flow.Flow

interface EvolutionEngine {
    fun observeState(): Flow<EvolutionState>

    /**
     * Triggers an evolution cycle: analysis → proposals → apply.
     *
     * In the current MVP, proposals are auto-applied without waiting for external approval.
     * Future versions will stop at AwaitingApproval and require explicit [approveAndApply] call.
     */
    suspend fun triggerEvolution(trigger: EvolutionTrigger, context: EvolutionContext): Result<List<EvolutionResult>>

    /** Applies pre-generated proposals. For use when UI-driven approval flow is implemented. */
    suspend fun approveAndApply(proposals: List<EvolutionProposal>): Result<List<EvolutionResult>>

    /** Rejects pending proposals and returns to Idle state. */
    suspend fun rejectProposals()
}
