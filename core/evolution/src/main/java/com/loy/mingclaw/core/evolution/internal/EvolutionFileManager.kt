package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.evolution.model.AgentDecision
import com.loy.mingclaw.core.evolution.model.UserFeedback
import com.loy.mingclaw.core.workspace.WorkspaceManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EvolutionFileManager @Inject constructor(
    private val workspaceManager: WorkspaceManager,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "feedbackType"
    }

    private val workspacePath: String
        get() = workspaceManager.getCurrentWorkspace().path

    private val decisionsDir: File
        get() = File(workspacePath, "EXPERIENCE/decisions")

    private val feedbacksDir: File
        get() = File(workspacePath, "EXPERIENCE/feedbacks")

    private val rollbackDir: File
        get() = File(workspacePath, ".evolution/rollback")

    suspend fun ensureDirectories() = withContext(ioDispatcher) {
        decisionsDir.mkdirs()
        feedbacksDir.mkdirs()
        rollbackDir.mkdirs()
    }

    suspend fun readAgentRules(): String = withContext(ioDispatcher) {
        val file = File(workspacePath, "AGENTS.md")
        if (file.exists()) file.readText() else ""
    }

    suspend fun writeAgentRules(content: String) = withContext(ioDispatcher) {
        File(workspacePath, "AGENTS.md").writeText(content)
    }

    suspend fun readKnowledgeMemory(): String = withContext(ioDispatcher) {
        val file = File(workspacePath, "MEMORY.md")
        if (file.exists()) file.readText() else ""
    }

    suspend fun writeKnowledgeMemory(content: String) = withContext(ioDispatcher) {
        File(workspacePath, "MEMORY.md").writeText(content)
    }

    suspend fun writeDecision(decision: AgentDecision) = withContext(ioDispatcher) {
        ensureDirectories()
        val file = File(decisionsDir, "${decision.decisionId}.json")
        file.writeText(json.encodeToString(AgentDecision.serializer(), decision))
    }

    suspend fun writeFeedback(feedback: UserFeedback) = withContext(ioDispatcher) {
        ensureDirectories()
        val file = File(feedbacksDir, "${feedback.feedbackId}.json")
        file.writeText(json.encodeToString(UserFeedback.serializer(), feedback))
    }

    suspend fun readDecisions(since: Instant): List<AgentDecision> = withContext(ioDispatcher) {
        ensureDirectories()
        decisionsDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString(AgentDecision.serializer(), file.readText())
                } catch (_: Exception) {
                    null
                }
            }
            ?.filter { it.timestamp >= since }
            ?: emptyList()
    }

    suspend fun readFeedbacks(since: Instant): List<UserFeedback> = withContext(ioDispatcher) {
        ensureDirectories()
        feedbacksDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString(UserFeedback.serializer(), file.readText())
                } catch (_: Exception) {
                    null
                }
            }
            ?.filter { it.timestamp >= since }
            ?: emptyList()
    }

    suspend fun backupCurrent(version: String) = withContext(ioDispatcher) {
        val versionDir = File(rollbackDir, version)
        versionDir.mkdirs()
        val agentsFile = File(workspacePath, "AGENTS.md")
        if (agentsFile.exists()) {
            agentsFile.copyTo(File(versionDir, "AGENTS.md"), overwrite = true)
        }
        val memoryFile = File(workspacePath, "MEMORY.md")
        if (memoryFile.exists()) {
            memoryFile.copyTo(File(versionDir, "MEMORY.md"), overwrite = true)
        }
    }

    suspend fun restoreVersion(version: String) = withContext(ioDispatcher) {
        val versionDir = File(rollbackDir, version)
        val agentsBackup = File(versionDir, "AGENTS.md")
        if (agentsBackup.exists()) {
            agentsBackup.copyTo(File(workspacePath, "AGENTS.md"), overwrite = true)
        }
        val memoryBackup = File(versionDir, "MEMORY.md")
        if (memoryBackup.exists()) {
            memoryBackup.copyTo(File(workspacePath, "MEMORY.md"), overwrite = true)
        }
    }
}
