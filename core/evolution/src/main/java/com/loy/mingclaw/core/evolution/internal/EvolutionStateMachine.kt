package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.evolution.model.EvolutionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EvolutionStateMachine @Inject constructor() {

    private val state = MutableStateFlow<EvolutionState>(EvolutionState.Idle)

    fun currentState(): EvolutionState = state.value
    fun observeState(): Flow<EvolutionState> = state

    fun transitionTo(newState: EvolutionState): Result<EvolutionState> {
        val current = state.value
        val allowed = when (current) {
            is EvolutionState.Idle -> newState is EvolutionState.Analyzing
            is EvolutionState.Analyzing -> newState is EvolutionState.AwaitingApproval || newState is EvolutionState.Failed
            is EvolutionState.AwaitingApproval -> newState is EvolutionState.Applying || newState is EvolutionState.Idle
            is EvolutionState.Applying -> newState is EvolutionState.Completed || newState is EvolutionState.Failed
            is EvolutionState.Completed -> newState is EvolutionState.Idle || newState is EvolutionState.Analyzing
            is EvolutionState.Failed -> newState is EvolutionState.Idle || newState is EvolutionState.Analyzing
        }
        return if (allowed) {
            state.value = newState
            Result.success(newState)
        } else {
            Result.failure(IllegalStateException("Invalid transition: $current -> $newState"))
        }
    }
}
