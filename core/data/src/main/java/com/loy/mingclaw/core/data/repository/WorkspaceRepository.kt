package com.loy.mingclaw.core.data.repository

import com.loy.mingclaw.core.model.workspace.Workspace
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {

    suspend fun createWorkspace(workspace: Workspace): Workspace

    suspend fun getWorkspace(workspaceId: String): Workspace?

    suspend fun getActiveWorkspace(): Workspace?

    suspend fun getAllWorkspaces(): List<Workspace>

    suspend fun updateWorkspace(workspace: Workspace): Workspace

    suspend fun deleteWorkspace(workspaceId: String)

    suspend fun setActiveWorkspace(workspaceId: String): Workspace
}
