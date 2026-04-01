package com.loy.mingclaw.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    indices = [
        Index("type"),
        Index("importance"),
        Index("created_at"),
    ],
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val content: String,
    val type: String,
    val importance: Float,
    val metadata: String, // JSON
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "accessed_at") val accessedAt: Long,
    @ColumnInfo(name = "access_count") val accessCount: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
)
