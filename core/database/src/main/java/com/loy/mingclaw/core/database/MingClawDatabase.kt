package com.loy.mingclaw.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.loy.mingclaw.core.database.dao.MessageDao
import com.loy.mingclaw.core.database.dao.SessionDao
import com.loy.mingclaw.core.database.dao.WorkspaceDao
import com.loy.mingclaw.core.database.entity.MessageEntity
import com.loy.mingclaw.core.database.entity.SessionEntity
import com.loy.mingclaw.core.database.entity.WorkspaceEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        WorkspaceEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MingClawDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun workspaceDao(): WorkspaceDao
}
