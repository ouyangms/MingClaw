package com.loy.mingclaw.core.data.repository.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.data.mapper.asDomain
import com.loy.mingclaw.core.data.mapper.asEntity
import com.loy.mingclaw.core.data.repository.WorkspaceRepository
import com.loy.mingclaw.core.database.dao.WorkspaceDao
import com.loy.mingclaw.core.model.workspace.Workspace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class OfflineFirstWorkspaceRepository @Inject constructor(
    private val workspaceDao: WorkspaceDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : WorkspaceRepository {

    override suspend fun createWorkspace(workspace: Workspace): Workspace = withContext(ioDispatcher) {
        workspaceDao.insert(workspace.asEntity())
        workspace
    }

    override suspend fun getWorkspace(workspaceId: String): Workspace? = withContext(ioDispatcher) {
        workspaceDao.getById(workspaceId)?.asDomain()
    }

    override suspend fun getActiveWorkspace(): Workspace? = withContext(ioDispatcher) {
        workspaceDao.getActive()?.asDomain()
    }

    override suspend fun getAllWorkspaces(): List<Workspace> = withContext(ioDispatcher) {
        workspaceDao.getAll().map { it.asDomain() }
    }

    override suspend fun updateWorkspace(workspace: Workspace): Workspace = withContext(ioDispatcher) {
        workspaceDao.update(workspace.asEntity())
        workspace
    }

    override suspend fun deleteWorkspace(workspaceId: String) = withContext(ioDispatcher) {
        workspaceDao.delete(workspaceId)
    }

    override suspend fun setActiveWorkspace(workspaceId: String): Workspace = withContext(ioDispatcher) {
        // Atomically deactivate all and activate the target
        workspaceDao.deactivateAll()
        val entity = workspaceDao.getById(workspaceId)
            ?: throw NoSuchElementException("Workspace not found: $workspaceId")
        val activated = entity.copy(isActive = true)
        workspaceDao.update(activated)
        activated.asDomain()
    }
}
