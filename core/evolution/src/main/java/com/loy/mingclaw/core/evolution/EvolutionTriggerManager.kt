package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.EvolutionContext
import com.loy.mingclaw.core.evolution.model.EvolutionProposal
import com.loy.mingclaw.core.evolution.model.EvolutionTrigger

interface EvolutionTriggerManager {
    suspend fun shouldTrigger(trigger: EvolutionTrigger, context: EvolutionContext): Boolean
    suspend fun performAnalysis(): Result<List<EvolutionProposal>>
}
