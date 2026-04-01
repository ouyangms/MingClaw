package com.loy.mingclaw.core.data.mapper

import com.loy.mingclaw.core.database.entity.SessionEntity
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionStatus
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionMapperTest {

    private val testInstant = Instant.fromEpochMilliseconds(1700000000000)

    private val testSession = Session(
        id = "session-1",
        title = "Test Session",
        createdAt = testInstant,
        updatedAt = testInstant,
        metadata = mapOf("key1" to "value1", "key2" to "value2"),
        status = SessionStatus.Active,
    )

    @Test
    fun asEntity_mapsAllFields() {
        val entity = testSession.asEntity()
        assertEquals(testSession.id, entity.id)
        assertEquals(testSession.title, entity.title)
        assertEquals(testSession.createdAt.toEpochMilliseconds(), entity.createdAt)
        assertEquals(testSession.updatedAt.toEpochMilliseconds(), entity.updatedAt)
        assertEquals(testSession.status.name, entity.status)
    }

    @Test
    fun asDomain_mapsAllFields() {
        val entity = SessionEntity(
            id = "session-1",
            title = "Test Session",
            createdAt = 1700000000000,
            updatedAt = 1700000000000,
            metadata = """{"key1":"value1","key2":"value2"}""",
            status = "Active",
        )
        val domain = entity.asDomain()
        assertEquals(entity.id, domain.id)
        assertEquals(entity.title, domain.title)
        assertEquals(entity.createdAt, domain.createdAt.toEpochMilliseconds())
        assertEquals(entity.updatedAt, domain.updatedAt.toEpochMilliseconds())
        assertEquals(SessionStatus.Active, domain.status)
        assertEquals(mapOf("key1" to "value1", "key2" to "value2"), domain.metadata)
    }

    @Test
    fun roundTrip_entityToDomainToEntity_preservesData() {
        val entity = SessionEntity(
            id = "session-rt",
            title = "RoundTrip",
            createdAt = 1700000000000,
            updatedAt = 1700000001000,
            metadata = """{"a":"b"}""",
            status = "Active",
        )
        val roundTripped = entity.asDomain().asEntity()
        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.title, roundTripped.title)
        assertEquals(entity.createdAt, roundTripped.createdAt)
        assertEquals(entity.updatedAt, roundTripped.updatedAt)
        assertEquals(entity.status, roundTripped.status)
    }

    @Test
    fun roundTrip_domainToEntityToDomain_preservesData() {
        val domain = testSession
        val roundTripped = domain.asEntity().asDomain()
        assertEquals(domain.id, roundTripped.id)
        assertEquals(domain.title, roundTripped.title)
        assertEquals(domain.createdAt, roundTripped.createdAt)
        assertEquals(domain.updatedAt, roundTripped.updatedAt)
        assertEquals(domain.status, roundTripped.status)
        assertEquals(domain.metadata, roundTripped.metadata)
    }

    @Test
    fun asDomain_handlesInvalidStatus() {
        val entity = SessionEntity(
            id = "s1",
            title = "t",
            createdAt = 0,
            updatedAt = 0,
            metadata = "{}",
            status = "InvalidStatus",
        )
        val domain = entity.asDomain()
        assertEquals(SessionStatus.Active, domain.status)
    }

    @Test
    fun asDomain_handlesInvalidMetadata() {
        val entity = SessionEntity(
            id = "s1",
            title = "t",
            createdAt = 0,
            updatedAt = 0,
            metadata = "not valid json",
            status = "Active",
        )
        val domain = entity.asDomain()
        assertEquals(emptyMap<String, String>(), domain.metadata)
    }
}
