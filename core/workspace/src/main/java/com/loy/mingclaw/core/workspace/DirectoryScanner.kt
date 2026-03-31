package com.loy.mingclaw.core.workspace

import com.loy.mingclaw.core.model.workspace.DirectoryInfo
import com.loy.mingclaw.core.model.workspace.FileTreeNode
import com.loy.mingclaw.core.model.workspace.ScanOptions
import java.nio.file.Path

interface DirectoryScanner {
    suspend fun scanDirectory(path: Path, options: ScanOptions = ScanOptions.Default): Result<List<Path>>
    suspend fun scanByPattern(path: Path, pattern: Regex): Result<List<Path>>
    suspend fun getFileTree(path: Path): Result<FileTreeNode>
    suspend fun getDirectoryInfo(path: Path): Result<DirectoryInfo>
}
