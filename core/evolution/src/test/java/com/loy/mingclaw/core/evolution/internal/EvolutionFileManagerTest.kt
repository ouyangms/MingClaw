package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.evolution.model.*
import com.loy.mingclaw.core.model.workspace.Workspace
import com.loy.mingclaw.core.workspace.WorkspaceManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EvolutionFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fileManager: EvolutionFileManager
    private lateinit var workspacePath: String

    @Before
    fun setup() {
        workspacePath = tempFolder.newFolder("workspace").absolutePath
        val workspace = Workspace(
            id = "test",
            name = "Test",
            path = workspacePath,
            createdAt = Clock.System.now(),
            modifiedAt = Clock.System.now(),
        )
        val workspaceManager = mockk<WorkspaceManager> {
            every { getCurrentWorkspace() } returns workspace
        }
        fileManager = EvolutionFileManager(workspaceManager, Dispatchers.Unconfined)
    }

    @Test
    fun writeAndReadDecision_roundTrip() = runTest {
        val decision = AgentDecision(
            decisionId = "d1",
            timestamp = Instant.fromEpochMilliseconds(1700000000000),
            ruleApplied = "rule-1",
            reasoning = "test reasoning",
            outcome = DecisionOutcome.SUCCESS,
        )
        fileManager.writeDecision(decision)
        val read = fileManager.readDecisions(Instant.DISTANT_PAST)
        assertEquals(1, read.size)
        assertEquals("d1", read[0].decisionId)
        assertEquals("rule-1", read[0].ruleApplied)
        assertEquals(DecisionOutcome.SUCCESS, read[0].outcome)
    }

    @Test
    fun writeAndReadExplicitFeedback_roundTrip() = runTest {
        val feedback = UserFeedback.Explicit(
            feedbackId = "fb1",
            timestamp = Instant.fromEpochMilliseconds(1700000000000),
            sessionId = "s1",
            type = ExplicitFeedbackType.THUMBS_UP,
            rating = 5,
            comment = "Great",
            aspect = FeedbackAspect.ACCURACY,
        )
        fileManager.writeFeedback(feedback)
        val read = fileManager.readFeedbacks(Instant.DISTANT_PAST)
        assertEquals(1, read.size)
        val explicit = read[0] as UserFeedback.Explicit
        assertEquals("fb1", explicit.feedbackId)
        assertEquals(ExplicitFeedbackType.THUMBS_UP, explicit.type)
        assertEquals(5, explicit.rating)
    }

    @Test
    fun writeAndReadImplicitFeedback_roundTrip() = runTest {
        val feedback = UserFeedback.Implicit(
            feedbackId = "fb2",
            timestamp = Instant.fromEpochMilliseconds(1700000000000),
            sessionId = "s1",
            action = ImplicitAction.REGENERATED,
            confidence = 0.9f,
        )
        fileManager.writeFeedback(feedback)
        val read = fileManager.readFeedbacks(Instant.DISTANT_PAST)
        assertEquals(1, read.size)
        val implicit = read[0] as UserFeedback.Implicit
        assertEquals(ImplicitAction.REGENERATED, implicit.action)
        assertEquals(0.9f, implicit.confidence, 0.01f)
    }

    @Test
    fun readDecisions_filtersByTimestamp() = runTest {
        val t1 = Instant.fromEpochMilliseconds(1700000000000)
        val t2 = Instant.fromEpochMilliseconds(1700001000000)
        val t3 = Instant.fromEpochMilliseconds(1700002000000)
        fileManager.writeDecision(AgentDecision("d1", t1, "r1", "test", DecisionOutcome.SUCCESS))
        fileManager.writeDecision(AgentDecision("d2", t2, "r2", "test", DecisionOutcome.FAILURE))
        fileManager.writeDecision(AgentDecision("d3", t3, "r3", "test", DecisionOutcome.PARTIAL))

        val read = fileManager.readDecisions(t2)
        assertEquals(2, read.size)
        assertEquals(listOf("d2", "d3"), read.map { it.decisionId })
    }

    @Test
    fun writeAndReadAgentRules_roundTrip() = runTest {
        val content = "# Agent Rules\n- Be concise\n- Be accurate"
        fileManager.writeAgentRules(content)
        assertEquals(content, fileManager.readAgentRules())
    }

    @Test
    fun writeAndReadKnowledgeMemory_roundTrip() = runTest {
        val content = "# Memory\n- User prefers Kotlin"
        fileManager.writeKnowledgeMemory(content)
        assertEquals(content, fileManager.readKnowledgeMemory())
    }

    @Test
    fun readAgentRules_returnsEmptyWhenFileNotExists() = runTest {
        assertEquals("", fileManager.readAgentRules())
    }

    @Test
    fun backupAndRestore_preservesContent() = runTest {
        val original = "# Original Rules"
        fileManager.writeAgentRules(original)
        fileManager.backupCurrent("v1")
        fileManager.writeAgentRules("# Modified Rules")
        assertEquals("# Modified Rules", fileManager.readAgentRules())
        fileManager.restoreVersion("v1")
        assertEquals(original, fileManager.readAgentRules())
    }
}
