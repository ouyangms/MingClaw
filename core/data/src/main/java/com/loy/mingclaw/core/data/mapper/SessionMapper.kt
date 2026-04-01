package com.loy.mingclaw.core.data.mapper

import com.loy.mingclaw.core.database.entity.SessionEntity
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionStatus
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun Session.asEntity(): SessionEntity = SessionEntity(
    id = id,
    title = title,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
    metadata = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), metadata),
    status = status.name,
)

fun SessionEntity.asDomain(): Session = Session(
    id = id,
    title = title,
    createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(createdAt),
    updatedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(updatedAt),
    metadata = try {
        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), metadata)
    } catch (_: Exception) {
        emptyMap()
    },
    status = try {
        SessionStatus.valueOf(status)
    } catch (_: Exception) {
        SessionStatus.Active
    },
)
