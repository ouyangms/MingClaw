package com.loy.mingclaw.core.data.repository

import com.loy.mingclaw.core.data.repository.internal.OfflineFirstWorkspaceRepository
import com.loy.mingclaw.core.database.dao.WorkspaceDao
import com.loy.mingclaw.core.database.entity.WorkspaceEntity
import com.loy.mingclaw.core.model.workspace.Workspace
import com.loy.mingclaw.core.model.workspace.WorkspaceMetadata
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OfflineFirstWorkspaceRepositoryTest {

    private val workspaceDao = mockk<WorkspaceDao>()
    private lateinit var repository: OfflineFirstWorkspaceRepository

    @Before
    fun setup() {
        repository = OfflineFirstWorkspaceRepository(
            workspaceDao = workspaceDao,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun createWorkspace_insertsAndReturns() = runTest {
        coEvery { workspaceDao.insert(any()) } returns Unit

        val workspace = Workspace(
            id = "ws1", name = "Test", path = "/test",
            createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
            modifiedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(0),
        )
        val result = repository.createWorkspace(workspace)
        assertEquals("ws1", result.id)
        coVerify { workspaceDao.insert(any()) }
    }

    @Test
    fun getWorkspace_returnsWorkspaceIfExists() = runTest {
        val entity = WorkspaceEntity(
            id = "ws1", name = "Test", path = "/test",
            createdAt = 0, modifiedAt = 0, isActive = false,
            description = "", tags = "[]", version = "1.0", templateId = null,
        )
        coEvery { workspaceDao.getById("ws1") } returns entity

        val result = repository.getWorkspace("ws1")
        assertNotNull(result)
        assertEquals("ws1", result!!.id)
    }

    @Test
    fun getWorkspace_returnsNullIfNotExists() = runTest {
        coEvery { workspaceDao.getById("missing") } returns null

        val result = repository.getWorkspace("missing")
        assertNull(result)
    }

    @Test
    fun getActiveWorkspace_returnsActiveIfAny() = runTest {
        val entity = WorkspaceEntity(
            id = "ws-active", name = "Active", path = "/active",
            createdAt = 0, modifiedAt = 0, isActive = true,
            description = "", tags = "[]", version = "1.0", templateId = null,
        )
        coEvery { workspaceDao.getActive() } returns entity

        val result = repository.getActiveWorkspace()
        assertNotNull(result)
        assertEquals("ws-active", result!!.id)
        assertEquals(true, result.isActive)
    }

    @Test
    fun getAllWorkspaces_returnsMappedList() = runTest {
        val entities = listOf(
            WorkspaceEntity("ws1", "WS1", "/p1", 0, 0, false, "", "[]", "1.0", null),
            WorkspaceEntity("ws2", "WS2", "/p2", 0, 0, false, "", "[]", "1.0", null),
        )
        coEvery { workspaceDao.getAll() } returns entities

        val result = repository.getAllWorkspaces()
        assertEquals(2, result.size)
        assertEquals("ws1", result[0].id)
        assertEquals("ws2", result[1].id)
    }

    @Test
    fun setActiveWorkspace_deactivatesAllAndActivatesTarget() = runTest {
        coEvery { workspaceDao.deactivateAll() } returns Unit
        val entity = WorkspaceEntity(
            id = "ws1", name = "WS1", path = "/p1",
            createdAt = 0, modifiedAt = 0, isActive = false,
            description = "", tags = "[]", version = "1.0", templateId = null,
        )
        coEvery { workspaceDao.getById("ws1") } returns entity
        coEvery { workspaceDao.update(any()) } returns Unit

        val result = repository.setActiveWorkspace("ws1")

        assertEquals("ws1", result.id)
        assertEquals(true, result.isActive)
        coVerify { workspaceDao.deactivateAll() }
        coVerify { workspaceDao.update(match { it.isActive }) }
    }

    @Test(expected = NoSuchElementException::class)
    fun setActiveWorkspace_throwsIfNotFound() = runTest {
        coEvery { workspaceDao.deactivateAll() } returns Unit
        coEvery { workspaceDao.getById("missing") } returns null

        repository.setActiveWorkspace("missing")
    }

    @Test
    fun deleteWorkspace_delegatesToDao() = runTest {
        coEvery { workspaceDao.delete("ws1") } returns Unit

        repository.deleteWorkspace("ws1")

        coVerify { workspaceDao.delete("ws1") }
    }
}
