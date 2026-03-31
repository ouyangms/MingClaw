package com.loy.mingclaw.core.kernel.internal

import com.loy.mingclaw.core.model.KernelConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultKernelConfigProvider @Inject constructor() {
    fun provide(): KernelConfig = KernelConfig()
}
