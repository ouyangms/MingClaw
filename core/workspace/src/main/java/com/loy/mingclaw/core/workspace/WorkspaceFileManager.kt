package com.loy.mingclaw.core.workspace

import com.loy.mingclaw.core.model.workspace.FileChangeEvent
import kotlinx.coroutines.flow.Flow
import java.nio.file.Path

interface WorkspaceFileManager {
    suspend fun readFile(path: Path): Result<String>
    suspend fun writeFile(path: Path, content: String): Result<Unit>
    suspend fun appendFile(path: Path, content: String): Result<Unit>
    suspend fun deleteFile(path: Path): Result<Unit>
    fun exists(path: Path): Boolean
    suspend fun createBackup(path: Path): Result<Path>
    fun watchFile(path: Path): Flow<FileChangeEvent>
}
