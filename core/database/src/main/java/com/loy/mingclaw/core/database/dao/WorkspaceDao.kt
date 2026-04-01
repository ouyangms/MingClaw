package com.loy.mingclaw.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.loy.mingclaw.core.database.entity.WorkspaceEntity

@Dao
interface WorkspaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workspace: WorkspaceEntity)

    @Update
    suspend fun update(workspace: WorkspaceEntity)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM workspaces WHERE id = :id")
    suspend fun getById(id: String): WorkspaceEntity?

    @Query("SELECT * FROM workspaces")
    suspend fun getAll(): List<WorkspaceEntity>

    @Query("SELECT * FROM workspaces WHERE is_active = 1")
    suspend fun getActive(): WorkspaceEntity?

    @Query("UPDATE workspaces SET is_active = 0")
    suspend fun deactivateAll()
}
