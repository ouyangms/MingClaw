package com.loy.mingclaw.core.kernel.di

import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.kernel.EventBus
import com.loy.mingclaw.core.kernel.MingClawKernel
import com.loy.mingclaw.core.kernel.internal.ConfigManagerImpl
import com.loy.mingclaw.core.kernel.internal.EventBusImpl
import com.loy.mingclaw.core.kernel.internal.MingClawKernelImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class KernelModule {

    @Binds
    @Singleton
    abstract fun bindEventBus(impl: EventBusImpl): EventBus

    @Binds
    @Singleton
    abstract fun bindConfigManager(impl: ConfigManagerImpl): ConfigManager

    @Binds
    @Singleton
    abstract fun bindMingClawKernel(impl: MingClawKernelImpl): MingClawKernel
}
