package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.CapabilityGap

interface CapabilityEvolver {
    suspend fun identifyCapabilityGaps(): Result<List<CapabilityGap>>
}
