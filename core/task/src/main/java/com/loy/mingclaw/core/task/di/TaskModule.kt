package com.loy.mingclaw.core.task.di

import com.loy.mingclaw.core.task.ConcurrencyController
import com.loy.mingclaw.core.task.DependencyResolver
import com.loy.mingclaw.core.task.ErrorHandler
import com.loy.mingclaw.core.task.TaskDispatcher
import com.loy.mingclaw.core.task.TaskOrchestrator
import com.loy.mingclaw.core.task.WorkflowEngine
import com.loy.mingclaw.core.task.internal.ConcurrencyControllerImpl
import com.loy.mingclaw.core.task.internal.DependencyResolverImpl
import com.loy.mingclaw.core.task.internal.ErrorHandlerImpl
import com.loy.mingclaw.core.task.internal.TaskDispatcherImpl
import com.loy.mingclaw.core.task.internal.TaskOrchestratorImpl
import com.loy.mingclaw.core.task.internal.WorkflowEngineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TaskModule {
    @Binds
    @Singleton
    abstract fun bindConcurrencyController(impl: ConcurrencyControllerImpl): ConcurrencyController

    @Binds
    @Singleton
    abstract fun bindDependencyResolver(impl: DependencyResolverImpl): DependencyResolver

    @Binds
    @Singleton
    abstract fun bindTaskDispatcher(impl: TaskDispatcherImpl): TaskDispatcher

    @Binds
    @Singleton
    abstract fun bindErrorHandler(impl: ErrorHandlerImpl): ErrorHandler

    @Binds
    @Singleton
    abstract fun bindTaskOrchestrator(impl: TaskOrchestratorImpl): TaskOrchestrator

    @Binds
    @Singleton
    abstract fun bindWorkflowEngine(impl: WorkflowEngineImpl): WorkflowEngine
}
