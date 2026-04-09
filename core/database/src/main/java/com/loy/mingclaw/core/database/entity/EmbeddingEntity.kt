package com.loy.mingclaw.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = MemoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EmbeddingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "embedding", typeAffinity = ColumnInfo.BLOB)
    val embedding: ByteArray,
    @ColumnInfo(name = "dimension") val dimension: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddingEntity
        if (id != other.id) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (dimension != other.dimension) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + dimension
        return result
    }
}
