package com.loy.mingclaw.core.memory.di

import com.loy.mingclaw.core.memory.EmbeddingService
import com.loy.mingclaw.core.memory.MemoryManager
import com.loy.mingclaw.core.memory.MemoryStorage
import com.loy.mingclaw.core.memory.internal.EmbeddingServiceImpl
import com.loy.mingclaw.core.memory.internal.MemoryManagerImpl
import com.loy.mingclaw.core.memory.internal.MemoryStorageImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MemoryModule {
    @Binds @Singleton
    abstract fun bindEmbeddingService(impl: EmbeddingServiceImpl): EmbeddingService

    @Binds @Singleton
    abstract fun bindMemoryStorage(impl: MemoryStorageImpl): MemoryStorage

    @Binds @Singleton
    abstract fun bindMemoryManager(impl: MemoryManagerImpl): MemoryManager
}
