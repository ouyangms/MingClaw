package com.loy.mingclaw.core.data.mapper

import com.loy.mingclaw.core.database.entity.WorkspaceEntity
import com.loy.mingclaw.core.model.workspace.Workspace
import com.loy.mingclaw.core.model.workspace.WorkspaceMetadata
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceMapperTest {

    private val testInstant = Instant.fromEpochMilliseconds(1700000000000)

    private val testWorkspace = Workspace(
        id = "ws-1",
        name = "Test Workspace",
        path = "/test/path",
        createdAt = testInstant,
        modifiedAt = testInstant,
        isActive = true,
        metadata = WorkspaceMetadata(
            description = "A test workspace",
            tags = listOf("test", "demo"),
            version = "2.0",
            templateId = "tpl-1",
        ),
    )

    @Test
    fun asEntity_mapsAllFields() {
        val entity = testWorkspace.asEntity()
        assertEquals(testWorkspace.id, entity.id)
        assertEquals(testWorkspace.name, entity.name)
        assertEquals(testWorkspace.path, entity.path)
        assertEquals(testWorkspace.createdAt.toEpochMilliseconds(), entity.createdAt)
        assertEquals(testWorkspace.modifiedAt.toEpochMilliseconds(), entity.modifiedAt)
        assertEquals(testWorkspace.isActive, entity.isActive)
        assertEquals("A test workspace", entity.description)
        assertEquals("2.0", entity.version)
        assertEquals("tpl-1", entity.templateId)
    }

    @Test
    fun asDomain_mapsAllFields() {
        val entity = WorkspaceEntity(
            id = "ws-1",
            name = "Test Workspace",
            path = "/test/path",
            createdAt = 1700000000000,
            modifiedAt = 1700000000000,
            isActive = true,
            description = "A test workspace",
            tags = """["test","demo"]""",
            version = "2.0",
            templateId = "tpl-1",
        )
        val domain = entity.asDomain()
        assertEquals(entity.id, domain.id)
        assertEquals(entity.name, domain.name)
        assertEquals(entity.path, domain.path)
        assertEquals(entity.isActive, domain.isActive)
        assertEquals("A test workspace", domain.metadata.description)
        assertEquals(listOf("test", "demo"), domain.metadata.tags)
        assertEquals("2.0", domain.metadata.version)
        assertEquals("tpl-1", domain.metadata.templateId)
    }

    @Test
    fun roundTrip_entityToDomainToEntity_preservesData() {
        val entity = WorkspaceEntity(
            id = "ws-rt",
            name = "RoundTrip WS",
            path = "/rt/path",
            createdAt = 1700000000000,
            modifiedAt = 1700000001000,
            isActive = false,
            description = "desc",
            tags = """["a","b"]""",
            version = "1.0",
            templateId = null,
        )
        val roundTripped = entity.asDomain().asEntity()
        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.name, roundTripped.name)
        assertEquals(entity.path, roundTripped.path)
        assertEquals(entity.createdAt, roundTripped.createdAt)
        assertEquals(entity.modifiedAt, roundTripped.modifiedAt)
        assertEquals(entity.isActive, roundTripped.isActive)
        assertEquals(entity.description, roundTripped.description)
        assertEquals(entity.version, roundTripped.version)
        assertEquals(entity.templateId, roundTripped.templateId)
    }

    @Test
    fun roundTrip_domainToEntityToDomain_preservesData() {
        val domain = testWorkspace
        val roundTripped = domain.asEntity().asDomain()
        assertEquals(domain.id, roundTripped.id)
        assertEquals(domain.name, roundTripped.name)
        assertEquals(domain.path, roundTripped.path)
        assertEquals(domain.isActive, roundTripped.isActive)
        assertEquals(domain.metadata.description, roundTripped.metadata.description)
        assertEquals(domain.metadata.tags, roundTripped.metadata.tags)
        assertEquals(domain.metadata.version, roundTripped.metadata.version)
        assertEquals(domain.metadata.templateId, roundTripped.metadata.templateId)
    }

    @Test
    fun asDomain_handlesInvalidTags() {
        val entity = WorkspaceEntity(
            id = "ws-bad",
            name = "Bad Tags",
            path = "/path",
            createdAt = 0,
            modifiedAt = 0,
            isActive = false,
            description = "",
            tags = "not valid json",
            version = "1.0",
            templateId = null,
        )
        val domain = entity.asDomain()
        assertEquals(emptyList<String>(), domain.metadata.tags)
    }
}
