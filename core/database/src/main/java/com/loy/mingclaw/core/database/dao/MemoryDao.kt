package com.loy.mingclaw.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.loy.mingclaw.core.database.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<MemoryEntity>)

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getById(id: String): MemoryEntity?

    @Query("SELECT * FROM memories ORDER BY created_at DESC")
    suspend fun getAll(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE type = :type ORDER BY importance DESC")
    suspend fun getByType(type: String): List<MemoryEntity>

    @Query("SELECT * FROM memories ORDER BY created_at DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' ORDER BY importance DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 20): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM memories WHERE type = :type")
    suspend fun countByType(type: String): Int

    @Query("SELECT AVG(importance) FROM memories")
    suspend fun getAverageImportance(): Float?

    @Query("DELETE FROM memories WHERE created_at < :beforeEpochMillis")
    suspend fun deleteBefore(beforeEpochMillis: Long): Int

    @Query("SELECT id FROM memories WHERE created_at < :beforeEpochMillis")
    suspend fun getIdsBefore(beforeEpochMillis: Long): List<String>
}
