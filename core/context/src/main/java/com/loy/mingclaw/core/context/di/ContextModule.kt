package com.loy.mingclaw.core.context.di

import com.loy.mingclaw.core.context.ContextWindowManager
import com.loy.mingclaw.core.context.SessionContextManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.context.internal.ContextWindowManagerImpl
import com.loy.mingclaw.core.context.internal.SessionContextManagerImpl
import com.loy.mingclaw.core.context.internal.TokenEstimatorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ContextModule {
    @Binds @Singleton abstract fun bindTokenEstimator(impl: TokenEstimatorImpl): TokenEstimator
    @Binds @Singleton abstract fun bindSessionContextManager(impl: SessionContextManagerImpl): SessionContextManager
    @Binds @Singleton abstract fun bindContextWindowManager(impl: ContextWindowManagerImpl): ContextWindowManager
}
