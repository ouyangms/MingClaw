package com.loy.mingclaw.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.loy.mingclaw.core.database.entity.EmbeddingEntity

@Dao
interface EmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: EmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<EmbeddingEntity>)

    @Query("DELETE FROM embeddings WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM embeddings WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<String>)

    @Query("SELECT * FROM embeddings WHERE id = :id")
    suspend fun getById(id: String): EmbeddingEntity?

    @Query("SELECT id FROM embeddings")
    suspend fun getAllIds(): List<String>
}
