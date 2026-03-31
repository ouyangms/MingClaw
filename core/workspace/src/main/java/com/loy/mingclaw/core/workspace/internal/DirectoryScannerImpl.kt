package com.loy.mingclaw.core.workspace.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.workspace.DirectoryInfo
import com.loy.mingclaw.core.model.workspace.FileTreeNode
import com.loy.mingclaw.core.model.workspace.ScanOptions
import com.loy.mingclaw.core.workspace.DirectoryScanner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DirectoryScannerImpl @Inject constructor(
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : DirectoryScanner {

    override suspend fun scanDirectory(
        path: Path,
        options: ScanOptions,
    ): Result<List<Path>> = withContext(ioDispatcher) {
        runCatching {
            if (!Files.exists(path)) return@runCatching emptyList()
            val results = mutableListOf<Path>()
            scanRecursive(path, options, results, 0)
            results
        }
    }

    private fun scanRecursive(
        dir: Path,
        options: ScanOptions,
        results: MutableList<Path>,
        depth: Int,
    ) {
        if (depth > options.maxDepth) return

        Files.list(dir).use { stream ->
            stream.forEach { childPath ->
                val name = childPath.fileName?.toString() ?: ""
                if (!options.includeHidden && name.startsWith(".")) return@forEach

                results.add(childPath)
                if (options.recursive && Files.isDirectory(childPath)) {
                    scanRecursive(childPath, options, results, depth + 1)
                }
            }
        }
    }

    override suspend fun scanByPattern(path: Path, pattern: Regex): Result<List<Path>> =
        withContext(ioDispatcher) {
            scanDirectory(path).map { paths ->
                paths.filter { pattern.matches(it.fileName?.toString() ?: "") }
            }
        }

    override suspend fun getFileTree(path: Path): Result<FileTreeNode> = withContext(ioDispatcher) {
        runCatching {
            if (!Files.exists(path)) {
                throw java.io.FileNotFoundException("Path not found: $path")
            }
            buildTree(path)
        }
    }

    private fun buildTree(path: Path): FileTreeNode {
        val name = path.fileName?.toString() ?: path.toString()
        if (!Files.isDirectory(path)) {
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
            return FileTreeNode.FileNode(
                path = path.toString(),
                name = name,
                size = attrs.size(),
                lastModified = attrs.lastModifiedTime().toMillis(),
            )
        }

        val children = mutableListOf<FileTreeNode>()
        Files.list(path).use { stream ->
            stream.forEach { child ->
                children.add(buildTree(child))
            }
        }

        return FileTreeNode.DirectoryNode(
            path = path.toString(),
            name = name,
            children = children,
        )
    }

    override suspend fun getDirectoryInfo(path: Path): Result<DirectoryInfo> = withContext(ioDispatcher) {
        runCatching {
            if (!Files.exists(path)) {
                throw java.io.FileNotFoundException("Path not found: $path")
            }

            var fileCount = 0
            var directoryCount = 0
            var totalSize = 0L
            var lastModified = 0L

            Files.walk(path).use { stream ->
                stream.forEach { child ->
                    val attrs = Files.readAttributes(child, BasicFileAttributes::class.java)
                    if (attrs.isDirectory) {
                        directoryCount++
                    } else {
                        fileCount++
                        totalSize += attrs.size()
                    }
                    lastModified = maxOf(lastModified, attrs.lastModifiedTime().toMillis())
                }
            }

            DirectoryInfo(
                path = path.toString(),
                name = path.fileName?.toString() ?: "",
                fileCount = fileCount,
                directoryCount = directoryCount,
                totalSize = totalSize,
                lastModified = lastModified,
                isReadable = Files.isReadable(path),
                isWritable = Files.isWritable(path),
            )
        }
    }
}
