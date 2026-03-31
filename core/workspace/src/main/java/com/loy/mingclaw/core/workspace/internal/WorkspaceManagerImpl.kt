package com.loy.mingclaw.core.workspace.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.model.workspace.Workspace
import com.loy.mingclaw.core.workspace.WorkspaceFileManager
import com.loy.mingclaw.core.workspace.WorkspaceManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class WorkspaceManagerImpl @Inject constructor(
    private val fileManager: WorkspaceFileManager,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : WorkspaceManager {

    private val workspacesRoot: Path by lazy {
        Path.of(System.getProperty("user.home"), ".mingclaw", "workspaces")
    }

    private val workspaces = MutableStateFlow<Map<String, Workspace>>(emptyMap())
    private var currentWorkspaceId: String? = null

    init {
        // Ensure root directory exists
        Files.createDirectories(workspacesRoot)
    }

    override fun getCurrentWorkspace(): Workspace {
        val id = currentWorkspaceId ?: run {
            // Auto-create default workspace if none exists
            val defaultId = "default"
            if (!workspaces.value.containsKey(defaultId)) {
                val now = Clock.System.now()
                val workspace = Workspace(
                    id = defaultId,
                    name = "Default",
                    path = workspacesRoot.resolve(defaultId).toString(),
                    createdAt = now,
                    modifiedAt = now,
                    isActive = true,
                )
                val updated = workspaces.value.toMutableMap()
                updated[defaultId] = workspace
                workspaces.value = updated
                currentWorkspaceId = defaultId
                Files.createDirectories(workspacesRoot.resolve(defaultId))
            }
            defaultId
        }
        return workspaces.value[id] ?: throw IllegalStateException("Current workspace not found: $id")
    }

    override fun listWorkspaces(): Flow<List<Workspace>> =
        workspaces.map { it.values.toList() }

    override suspend fun createWorkspace(name: String): Result<Workspace> = withContext(ioDispatcher) {
        runCatching {
            val id = name.lowercase().replace(Regex("[^a-z0-9-]"), "-").trimEnd('-')
            if (id.isBlank()) throw IllegalArgumentException("Invalid workspace name: $name")
            if (workspaces.value.containsKey(id)) throw IllegalArgumentException("Workspace already exists: $id")

            val workspacePath = workspacesRoot.resolve(id)
            Files.createDirectories(workspacePath)

            // Create standard workspace structure
            createWorkspaceStructure(workspacePath)

            val now = Clock.System.now()
            val workspace = Workspace(
                id = id,
                name = name,
                path = workspacePath.toString(),
                createdAt = now,
                modifiedAt = now,
                isActive = true,
            )

            val updated = workspaces.value.toMutableMap()
            updated[id] = workspace
            workspaces.value = updated
            currentWorkspaceId = id

            workspace
        }
    }

    override suspend fun switchWorkspace(workspaceId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val workspace = workspaces.value[workspaceId]
                ?: throw IllegalArgumentException("Workspace not found: $workspaceId")
            currentWorkspaceId = workspaceId
        }
    }

    override suspend fun deleteWorkspace(workspaceId: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            if (!workspaces.value.containsKey(workspaceId)) {
                throw IllegalArgumentException("Workspace not found: $workspaceId")
            }
            val workspacePath = workspacesRoot.resolve(workspaceId)
            if (Files.exists(workspacePath)) {
                Files.walk(workspacePath)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
            val updated = workspaces.value.toMutableMap()
            updated.remove(workspaceId)
            workspaces.value = updated
            if (currentWorkspaceId == workspaceId) {
                currentWorkspaceId = null
            }
        }
    }

    private fun createWorkspaceStructure(workspacePath: Path) {
        Files.createDirectories(workspacePath.resolve("CONFIG"))
        Files.createDirectories(workspacePath.resolve("SKILLS"))
        Files.createDirectories(workspacePath.resolve("EXPERIENCE"))
        Files.createDirectories(workspacePath.resolve("EXPERIENCE/feedback"))
        Files.createDirectories(workspacePath.resolve("EXPERIENCE/patterns"))
        Files.createDirectories(workspacePath.resolve("EXPERIENCE/metrics"))
        val agentsFile = workspacePath.resolve("AGENTS.md")
        if (!Files.exists(agentsFile)) {
            agentsFile.toFile().writeText("# Agents Rules\n")
        }
        val memoryFile = workspacePath.resolve("MEMORY.md")
        if (!Files.exists(memoryFile)) {
            memoryFile.toFile().writeText("# Memory\n")
        }
    }
}
