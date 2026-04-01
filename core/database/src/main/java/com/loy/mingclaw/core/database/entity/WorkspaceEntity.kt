package com.loy.mingclaw.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "modified_at") val modifiedAt: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    val description: String = "",
    val tags: String = "[]", // JSON array
    val version: String = "1.0",
    @ColumnInfo(name = "template_id") val templateId: String? = null,
)
