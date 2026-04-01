package com.loy.mingclaw.core.data.di

import com.loy.mingclaw.core.data.repository.ChatRepository
import com.loy.mingclaw.core.data.repository.MemoryRepository
import com.loy.mingclaw.core.data.repository.SessionRepository
import com.loy.mingclaw.core.data.repository.WorkspaceRepository
import com.loy.mingclaw.core.data.repository.internal.OfflineFirstChatRepository
import com.loy.mingclaw.core.data.repository.internal.OfflineFirstMemoryRepository
import com.loy.mingclaw.core.data.repository.internal.OfflineFirstSessionRepository
import com.loy.mingclaw.core.data.repository.internal.OfflineFirstWorkspaceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        impl: OfflineFirstSessionRepository,
    ): SessionRepository

    @Binds
    @Singleton
    abstract fun bindWorkspaceRepository(
        impl: OfflineFirstWorkspaceRepository,
    ): WorkspaceRepository

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(
        impl: OfflineFirstMemoryRepository,
    ): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        impl: OfflineFirstChatRepository,
    ): ChatRepository
}
