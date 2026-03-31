package com.loy.mingclaw.core.workspace.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.workspace.FileChangeEvent
import com.loy.mingclaw.core.workspace.WorkspaceFileManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WorkspaceFileManagerImpl @Inject constructor(
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : WorkspaceFileManager {

    private val fileChangeEvents = MutableSharedFlow<FileChangeEvent>(extraBufferCapacity = 10)

    override suspend fun readFile(path: Path): Result<String> = withContext(ioDispatcher) {
        runCatching {
            val file = path.toFile()
            if (!file.exists()) {
                throw java.io.FileNotFoundException("File not found: $path")
            }
            file.readText()
        }
    }

    override suspend fun writeFile(path: Path, content: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            Files.createDirectories(path.parent)
            val tempFile = path.resolveSibling("${path.fileName}.tmp")
            tempFile.toFile().writeText(content)
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            fileChangeEvents.tryEmit(FileChangeEvent.Modified(path.toString()))
            Unit
        }
    }

    override suspend fun appendFile(path: Path, content: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val file = path.toFile()
            val existing = if (file.exists()) file.readText() else ""
            Files.createDirectories(path.parent)
            file.writeText(existing + content)
            fileChangeEvents.tryEmit(FileChangeEvent.Modified(path.toString()))
            Unit
        }
    }

    override suspend fun deleteFile(path: Path): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            if (Files.exists(path)) {
                Files.delete(path)
                fileChangeEvents.tryEmit(FileChangeEvent.Deleted(path.toString()))
            }
        }
    }

    override fun exists(path: Path): Boolean = Files.exists(path)

    override suspend fun createBackup(path: Path): Result<Path> = withContext(ioDispatcher) {
        runCatching {
            if (!Files.exists(path)) {
                throw java.io.FileNotFoundException("File not found: $path")
            }
            val timestamp = System.currentTimeMillis()
            val backupPath = path.resolveSibling("${path.fileName}.backup.$timestamp")
            Files.copy(path, backupPath, StandardCopyOption.REPLACE_EXISTING)
            backupPath
        }
    }

    override fun watchFile(path: Path): Flow<FileChangeEvent> = fileChangeEvents
}
