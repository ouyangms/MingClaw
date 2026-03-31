package com.loy.mingclaw.core.workspace.di

import com.loy.mingclaw.core.workspace.ConfigValidator
import com.loy.mingclaw.core.workspace.DirectoryScanner
import com.loy.mingclaw.core.workspace.WorkspaceConfigManager
import com.loy.mingclaw.core.workspace.WorkspaceFileManager
import com.loy.mingclaw.core.workspace.WorkspaceManager
import com.loy.mingclaw.core.workspace.internal.ConfigValidatorImpl
import com.loy.mingclaw.core.workspace.internal.DirectoryScannerImpl
import com.loy.mingclaw.core.workspace.internal.WorkspaceConfigManagerImpl
import com.loy.mingclaw.core.workspace.internal.WorkspaceFileManagerImpl
import com.loy.mingclaw.core.workspace.internal.WorkspaceManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WorkspaceModule {
    @Binds
    @Singleton
    abstract fun bindWorkspaceFileManager(impl: WorkspaceFileManagerImpl): WorkspaceFileManager

    @Binds
    @Singleton
    abstract fun bindConfigValidator(impl: ConfigValidatorImpl): ConfigValidator

    @Binds
    @Singleton
    abstract fun bindWorkspaceConfigManager(impl: WorkspaceConfigManagerImpl): WorkspaceConfigManager

    @Binds
    @Singleton
    abstract fun bindDirectoryScanner(impl: DirectoryScannerImpl): DirectoryScanner

    @Binds
    @Singleton
    abstract fun bindWorkspaceManager(impl: WorkspaceManagerImpl): WorkspaceManager
}
