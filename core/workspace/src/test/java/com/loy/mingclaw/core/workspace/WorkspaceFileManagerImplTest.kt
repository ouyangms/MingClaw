package com.loy.mingclaw.core.workspace

import com.loy.mingclaw.core.workspace.internal.WorkspaceFileManagerImpl
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

class WorkspaceFileManagerImplTest {

    private val tempFolder = TemporaryFolder()
    private lateinit var fileManager: WorkspaceFileManagerImpl
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        tempFolder.create()
        testScope = TestScope(UnconfinedTestDispatcher())
        fileManager = WorkspaceFileManagerImpl(UnconfinedTestDispatcher(testScope.testScheduler))
    }

    @After
    fun tearDown() {
        tempFolder.delete()
    }

    @Test
    fun readFile_existingFile_returnsContent() = testScope.runTest {
        val file = tempFolder.newFile("test.txt").toPath()
        file.toFile().writeText("hello world")
        val result = fileManager.readFile(file)
        assertTrue(result.isSuccess)
        assertEquals("hello world", result.getOrThrow())
    }

    @Test
    fun readFile_nonExistentFile_returnsFailure() = testScope.runTest {
        val path = tempFolder.newFolder("subdir").toPath().resolve("missing.txt")
        val result = fileManager.readFile(path)
        assertTrue(result.isFailure)
    }

    @Test
    fun writeFile_createsFileAndWritesContent() = testScope.runTest {
        val file = tempFolder.newFolder("dir").toPath().resolve("output.txt")
        val result = fileManager.writeFile(file, "test content")
        assertTrue(result.isSuccess)
        assertEquals("test content", file.toFile().readText())
    }

    @Test
    fun writeFile_overwritesExistingContent() = testScope.runTest {
        val file = tempFolder.newFile("existing.txt").toPath()
        file.toFile().writeText("old content")
        fileManager.writeFile(file, "new content")
        assertEquals("new content", file.toFile().readText())
    }

    @Test
    fun appendFile_appendsToExistingContent() = testScope.runTest {
        val file = tempFolder.newFile("append.txt").toPath()
        file.toFile().writeText("hello ")
        fileManager.appendFile(file, "world")
        assertEquals("hello world", file.toFile().readText())
    }

    @Test
    fun deleteFile_existingFile_deletesFile() = testScope.runTest {
        val file = tempFolder.newFile("delete-me.txt").toPath()
        val result = fileManager.deleteFile(file)
        assertTrue(result.isSuccess)
        assertFalse(Files.exists(file))
    }

    @Test
    fun deleteFile_nonExistentFile_returnsSuccess() = testScope.runTest {
        val path = tempFolder.root.toPath().resolve("missing.txt")
        val result = fileManager.deleteFile(path)
        assertTrue(result.isSuccess)
    }

    @Test
    fun exists_existingFile_returnsTrue() {
        val file = tempFolder.newFile("exists.txt").toPath()
        assertTrue(fileManager.exists(file))
    }

    @Test
    fun exists_nonExistentFile_returnsFalse() {
        val path = tempFolder.root.toPath().resolve("nope.txt")
        assertFalse(fileManager.exists(path))
    }

    @Test
    fun createBackup_createsBackupFile() = testScope.runTest {
        val file = tempFolder.newFile("backup.txt").toPath()
        file.toFile().writeText("backup content")
        val result = fileManager.createBackup(file)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().fileName.toString().startsWith("backup.txt.backup."))
        assertEquals("backup content", result.getOrThrow().toFile().readText())
    }
}
