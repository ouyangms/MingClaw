package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.EvolutionContext
import com.loy.mingclaw.core.evolution.model.EvolutionProposal
import com.loy.mingclaw.core.evolution.model.EvolutionResult
import com.loy.mingclaw.core.evolution.model.EvolutionState
import com.loy.mingclaw.core.evolution.model.EvolutionTrigger
import kotlinx.coroutines.flow.Flow

interface EvolutionEngine {
    fun observeState(): Flow<EvolutionState>
    suspend fun triggerEvolution(trigger: EvolutionTrigger, context: EvolutionContext): Result<List<EvolutionResult>>
    suspend fun approveAndApply(proposals: List<EvolutionProposal>): Result<List<EvolutionResult>>
    suspend fun rejectProposals()
}
