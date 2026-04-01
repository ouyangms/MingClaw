package com.loy.mingclaw.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "embeddings")
data class EmbeddingEntity(
    @PrimaryKey val id: String,
    val embedding: String, // JSON array of floats, e.g. "[0.1, 0.2, ...]"
    @ColumnInfo(name = "dimension") val dimension: Int,
)
