package com.loy.mingclaw.core.evolution.model

sealed class EvolutionState {
    data object Idle : EvolutionState()
    data class Analyzing(val trigger: EvolutionTrigger) : EvolutionState()
    data class AwaitingApproval(val proposals: List<EvolutionProposal>) : EvolutionState()
    data class Applying(val proposals: List<EvolutionProposal>) : EvolutionState()
    data class Completed(val results: List<EvolutionResult>) : EvolutionState()
    data class Failed(val error: String) : EvolutionState()
}
