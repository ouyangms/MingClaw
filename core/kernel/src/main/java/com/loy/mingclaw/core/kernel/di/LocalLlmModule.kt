package com.loy.mingclaw.core.kernel.di

import com.loy.mingclaw.core.kernel.llm.LocalLlmProvider
import com.loy.mingclaw.core.common.llm.LocalLlm
import com.loy.mingclaw.core.model.llm.LlmProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class LocalLlmModule {
    @Binds
    @Singleton
    @LocalLlm
    abstract fun bindLocalLlmProvider(impl: LocalLlmProvider): LlmProvider
}
