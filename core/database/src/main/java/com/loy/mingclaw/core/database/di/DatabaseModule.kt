package com.loy.mingclaw.core.database.di

import android.content.Context
import androidx.room.Room
import com.loy.mingclaw.core.database.MingClawDatabase
import com.loy.mingclaw.core.database.dao.EmbeddingDao
import com.loy.mingclaw.core.database.dao.MemoryDao
import com.loy.mingclaw.core.database.dao.MessageDao
import com.loy.mingclaw.core.database.dao.SessionDao
import com.loy.mingclaw.core.database.dao.WorkspaceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MingClawDatabase = Room.databaseBuilder(
        context,
        MingClawDatabase::class.java,
        "mingclaw_db",
    // TODO: Replace fallbackToDestructiveMigration with proper migrations before production
    ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideSessionDao(database: MingClawDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideMessageDao(database: MingClawDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideWorkspaceDao(database: MingClawDatabase): WorkspaceDao = database.workspaceDao()

    @Provides
    fun provideMemoryDao(database: MingClawDatabase): MemoryDao = database.memoryDao()

    @Provides
    fun provideEmbeddingDao(database: MingClawDatabase): EmbeddingDao = database.embeddingDao()
}
