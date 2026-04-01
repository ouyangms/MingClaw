package com.loy.mingclaw.core.data.mapper

import com.loy.mingclaw.core.database.entity.WorkspaceEntity
import com.loy.mingclaw.core.model.workspace.Workspace
import com.loy.mingclaw.core.model.workspace.WorkspaceMetadata
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun Workspace.asEntity(): WorkspaceEntity = WorkspaceEntity(
    id = id,
    name = name,
    path = path,
    createdAt = createdAt.toEpochMilliseconds(),
    modifiedAt = modifiedAt.toEpochMilliseconds(),
    isActive = isActive,
    description = metadata.description,
    tags = json.encodeToString(ListSerializer(String.serializer()), metadata.tags),
    version = metadata.version,
    templateId = metadata.templateId,
)

fun WorkspaceEntity.asDomain(): Workspace = Workspace(
    id = id,
    name = name,
    path = path,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    modifiedAt = Instant.fromEpochMilliseconds(modifiedAt),
    isActive = isActive,
    metadata = WorkspaceMetadata(
        description = description,
        tags = try {
            json.decodeFromString(ListSerializer(String.serializer()), tags)
        } catch (_: Exception) {
            emptyList()
        },
        version = version,
        templateId = templateId,
    ),
)
