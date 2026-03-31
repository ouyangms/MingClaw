package com.loy.mingclaw.core.workspace

import com.loy.mingclaw.core.model.workspace.FileTreeNode
import com.loy.mingclaw.core.model.workspace.ScanOptions
import com.loy.mingclaw.core.workspace.internal.DirectoryScannerImpl
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class DirectoryScannerImplTest {

    private val tempFolder = TemporaryFolder()
    private lateinit var scanner: DirectoryScannerImpl
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        tempFolder.create()
        testScope = TestScope(UnconfinedTestDispatcher())
        scanner = DirectoryScannerImpl(UnconfinedTestDispatcher(testScope.testScheduler))
    }

    @After
    fun tearDown() {
        tempFolder.delete()
    }

    @Test
    fun scanDirectory_returnsAllFiles() = testScope.runTest {
        val dir = tempFolder.newFolder("scan").toPath()
        dir.resolve("a.txt").toFile().writeText("a")
        dir.resolve("b.txt").toFile().writeText("b")
        Files.createDirectory(dir.resolve("subdir"))
        dir.resolve("subdir/c.txt").toFile().writeText("c")

        val result = scanner.scanDirectory(dir)
        assertTrue(result.isSuccess)
        val paths = result.getOrThrow()
        assertEquals(4, paths.size) // a.txt, b.txt, subdir, c.txt
    }

    @Test
    fun scanDirectory_nonRecursive_returnsOnlyTopLevel() = testScope.runTest {
        val dir = tempFolder.newFolder("scan2").toPath()
        dir.resolve("a.txt").toFile().writeText("a")
        Files.createDirectory(dir.resolve("subdir"))
        dir.resolve("subdir/b.txt").toFile().writeText("b")

        val options = ScanOptions(recursive = false)
        val result = scanner.scanDirectory(dir, options)
        assertTrue(result.isSuccess)
        val paths = result.getOrThrow()
        assertEquals(2, paths.size) // a.txt, subdir only
    }

    @Test
    fun scanByPattern_returnsMatchingFiles() = testScope.runTest {
        val dir = tempFolder.newFolder("pattern").toPath()
        dir.resolve("test.txt").toFile().writeText("t")
        dir.resolve("test.md").toFile().writeText("t")
        dir.resolve("other.txt").toFile().writeText("o")

        val result = scanner.scanByPattern(dir, Regex(".*\\.md"))
        assertTrue(result.isSuccess)
        val paths = result.getOrThrow()
        assertEquals(1, paths.size)
        assertTrue(paths[0].fileName.toString().endsWith(".md"))
    }

    @Test
    fun getFileTree_returnsCorrectStructure() = testScope.runTest {
        val dir = tempFolder.newFolder("tree").toPath()
        dir.resolve("file1.txt").toFile().writeText("content1")
        Files.createDirectory(dir.resolve("subdir"))
        dir.resolve("subdir/file2.txt").toFile().writeText("content2")

        val result = scanner.getFileTree(dir)
        assertTrue(result.isSuccess)
        val tree = result.getOrThrow()
        assertTrue(tree is FileTreeNode.DirectoryNode)
        val dirNode = tree as FileTreeNode.DirectoryNode
        assertEquals(2, dirNode.children.size)
    }

    @Test
    fun getDirectoryInfo_returnsCorrectCounts() = testScope.runTest {
        val dir = tempFolder.newFolder("info").toPath()
        dir.resolve("a.txt").toFile().writeText("12345")
        dir.resolve("b.txt").toFile().writeText("12")
        Files.createDirectory(dir.resolve("sub"))

        val result = scanner.getDirectoryInfo(dir)
        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals(2, info.fileCount)
        assertEquals(2, info.directoryCount) // root "info" dir + "sub"
        assertEquals(7L, info.totalSize) // 5 + 2 bytes
        assertTrue(info.isReadable)
        assertTrue(info.isWritable)
    }
}
