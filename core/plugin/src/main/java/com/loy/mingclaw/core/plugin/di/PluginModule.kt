package com.loy.mingclaw.core.plugin.di

import com.loy.mingclaw.core.plugin.PluginLoader
import com.loy.mingclaw.core.plugin.PluginRegistry
import com.loy.mingclaw.core.plugin.SecurityManager
import com.loy.mingclaw.core.plugin.ToolRegistry
import com.loy.mingclaw.core.plugin.internal.PluginLoaderImpl
import com.loy.mingclaw.core.plugin.internal.PluginRegistryImpl
import com.loy.mingclaw.core.plugin.internal.SecurityManagerImpl
import com.loy.mingclaw.core.plugin.internal.ToolRegistryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PluginModule {

    @Binds
    @Singleton
    abstract fun bindToolRegistry(impl: ToolRegistryImpl): ToolRegistry

    @Binds
    @Singleton
    abstract fun bindSecurityManager(impl: SecurityManagerImpl): SecurityManager

    @Binds
    @Singleton
    abstract fun bindPluginRegistry(impl: PluginRegistryImpl): PluginRegistry

    @Binds
    @Singleton
    abstract fun bindPluginLoader(impl: PluginLoaderImpl): PluginLoader
}
