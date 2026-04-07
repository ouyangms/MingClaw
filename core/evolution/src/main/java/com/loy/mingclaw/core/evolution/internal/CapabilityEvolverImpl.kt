package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.evolution.CapabilityEvolver
import com.loy.mingclaw.core.evolution.model.CapabilityGap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CapabilityEvolverImpl @Inject constructor() : CapabilityEvolver {

    override suspend fun identifyCapabilityGaps(): Result<List<CapabilityGap>> {
        // MVP stub: no capability gap detection yet
        return Result.success(emptyList())
    }
}
