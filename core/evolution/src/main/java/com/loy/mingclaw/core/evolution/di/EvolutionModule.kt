package com.loy.mingclaw.core.evolution.di

import com.loy.mingclaw.core.evolution.BehaviorEvolver
import com.loy.mingclaw.core.evolution.CapabilityEvolver
import com.loy.mingclaw.core.evolution.EvolutionEngine
import com.loy.mingclaw.core.evolution.EvolutionTriggerManager
import com.loy.mingclaw.core.evolution.FeedbackCollector
import com.loy.mingclaw.core.evolution.KnowledgeEvolver
import com.loy.mingclaw.core.evolution.internal.BehaviorEvolverImpl
import com.loy.mingclaw.core.evolution.internal.CapabilityEvolverImpl
import com.loy.mingclaw.core.evolution.internal.EvolutionEngineImpl
import com.loy.mingclaw.core.evolution.internal.EvolutionTriggerManagerImpl
import com.loy.mingclaw.core.evolution.internal.FeedbackCollectorImpl
import com.loy.mingclaw.core.evolution.internal.KnowledgeEvolverImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class EvolutionModule {

    @Binds
    @Singleton
    abstract fun bindFeedbackCollector(impl: FeedbackCollectorImpl): FeedbackCollector

    @Binds
    @Singleton
    abstract fun bindKnowledgeEvolver(impl: KnowledgeEvolverImpl): KnowledgeEvolver

    @Binds
    @Singleton
    abstract fun bindBehaviorEvolver(impl: BehaviorEvolverImpl): BehaviorEvolver

    @Binds
    @Singleton
    abstract fun bindCapabilityEvolver(impl: CapabilityEvolverImpl): CapabilityEvolver

    @Binds
    @Singleton
    abstract fun bindEvolutionTriggerManager(impl: EvolutionTriggerManagerImpl): EvolutionTriggerManager

    @Binds
    @Singleton
    abstract fun bindEvolutionEngine(impl: EvolutionEngineImpl): EvolutionEngine
}
