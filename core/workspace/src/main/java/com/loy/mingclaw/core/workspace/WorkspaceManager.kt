package com.loy.mingclaw.core.workspace

import com.loy.mingclaw.core.model.workspace.Workspace
import kotlinx.coroutines.flow.Flow

interface WorkspaceManager {
    fun getCurrentWorkspace(): Workspace
    fun listWorkspaces(): Flow<List<Workspace>>
    suspend fun createWorkspace(name: String): Result<Workspace>
    suspend fun switchWorkspace(workspaceId: String): Result<Unit>
    suspend fun deleteWorkspace(workspaceId: String): Result<Unit>
}
