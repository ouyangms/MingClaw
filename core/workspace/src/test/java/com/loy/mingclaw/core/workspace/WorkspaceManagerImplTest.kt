package com.loy.mingclaw.core.workspace

import com.loy.mingclaw.core.workspace.internal.WorkspaceFileManagerImpl
import com.loy.mingclaw.core.workspace.internal.WorkspaceManagerImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class WorkspaceManagerImplTest {

    private val tempFolder = TemporaryFolder()
    private lateinit var manager: WorkspaceManagerImpl
    private lateinit var testWorkspacesRoot: Path
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        tempFolder.create()
        testWorkspacesRoot = tempFolder.newFolder("workspaces").toPath()
        testScope = TestScope(UnconfinedTestDispatcher())
        val testDispatcher = UnconfinedTestDispatcher(testScope.testScheduler)
        val fileManager = WorkspaceFileManagerImpl(testDispatcher)
        // Note: WorkspaceManagerImpl creates its own workspaces root dir.
        // For testing, we use the real implementation with test dispatcher.
        manager = WorkspaceManagerImpl(fileManager, testDispatcher)
    }

    @After
    fun tearDown() {
        tempFolder.delete()
    }

    @Test
    fun getCurrentWorkspace_createsDefaultWorkspace() {
        val workspace = manager.getCurrentWorkspace()
        assertEquals("default", workspace.id)
        assertEquals("Default", workspace.name)
    }

    @Test
    fun createWorkspace_returnsNewWorkspace() = testScope.runTest {
        val result = manager.createWorkspace("Test Project")
        assertTrue(result.isSuccess)
        val workspace = result.getOrThrow()
        assertEquals("test-project", workspace.id)
        assertEquals("Test Project", workspace.name)
        assertTrue(workspace.isActive)
    }

    @Test
    fun createWorkspace_createsDirectoryStructure() = testScope.runTest {
        val result = manager.createWorkspace("My Space")
        assertTrue(result.isSuccess)
        val workspace = result.getOrThrow()
        val path = Path.of(workspace.path)
        assertTrue(Files.exists(path.resolve("CONFIG")))
        assertTrue(Files.exists(path.resolve("SKILLS")))
        assertTrue(Files.exists(path.resolve("EXPERIENCE")))
        assertTrue(Files.exists(path.resolve("AGENTS.md")))
        assertTrue(Files.exists(path.resolve("MEMORY.md")))
    }

    @Test
    fun createWorkspace_duplicateName_returnsFailure() = testScope.runTest {
        manager.createWorkspace("duplicate")
        val result = manager.createWorkspace("duplicate")
        assertTrue(result.isFailure)
    }

    @Test
    fun switchWorkspace_changesCurrentWorkspace() = testScope.runTest {
        manager.createWorkspace("Workspace A")
        val wsB = manager.createWorkspace("Workspace B").getOrThrow()

        val result = manager.switchWorkspace(wsB.id)
        assertTrue(result.isSuccess)
        assertEquals(wsB.id, manager.getCurrentWorkspace().id)
    }

    @Test
    fun switchWorkspace_nonExistent_returnsFailure() = testScope.runTest {
        val result = manager.switchWorkspace("nonexistent")
        assertTrue(result.isFailure)
    }

    @Test
    fun deleteWorkspace_removesWorkspace() = testScope.runTest {
        val workspace = manager.createWorkspace("ToDelete").getOrThrow()
        val result = manager.deleteWorkspace(workspace.id)
        assertTrue(result.isSuccess)
        assertFalse(Files.exists(Path.of(workspace.path)))
    }

    @Test
    fun deleteWorkspace_nonExistent_returnsFailure() = testScope.runTest {
        val result = manager.deleteWorkspace("nonexistent")
        assertTrue(result.isFailure)
    }

    @Test
    fun listWorkspaces_returnsAllWorkspaces() = testScope.runTest {
        manager.createWorkspace("Workspace 1")
        manager.createWorkspace("Workspace 2")
        val workspaces = manager.listWorkspaces().first()
        // Includes default that may have been auto-created, plus the 2 we created
        assertTrue(workspaces.size >= 2)
    }
}
