package com.loy.mingclaw.core.model.workspace

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun workspace_serialization_roundTrip() {
        val now = Clock.System.now()
        val workspace = Workspace(
            id = "ws-1",
            name = "Test Workspace",
            path = "/data/workspaces/ws-1",
            createdAt = now,
            modifiedAt = now,
            isActive = true,
            metadata = WorkspaceMetadata(description = "A test workspace", tags = listOf("test")),
        )
        val encoded = json.encodeToString(Workspace.serializer(), workspace)
        val decoded = json.decodeFromString(Workspace.serializer(), encoded)
        assertEquals(workspace, decoded)
    }

    @Test
    fun workspaceMetadata_defaultValues() {
        val metadata = WorkspaceMetadata()
        assertEquals("", metadata.description)
        assertTrue(metadata.tags.isEmpty())
        assertEquals("1.0", metadata.version)
        assertEquals(null, metadata.templateId)
    }
}
