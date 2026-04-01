package com.loy.mingclaw.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id"), Index("timestamp")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val role: String,      // MessageRole.name
    val content: String,
    val timestamp: Long?,
    @ColumnInfo(name = "tool_calls") val toolCalls: String?, // JSON
    @ColumnInfo(name = "edited_at") val editedAt: Long?,
)
