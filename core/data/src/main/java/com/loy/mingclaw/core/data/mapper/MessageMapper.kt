package com.loy.mingclaw.core.data.mapper

import com.loy.mingclaw.core.database.entity.MessageEntity
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.context.ToolCall
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

fun Message.asEntity(): MessageEntity = MessageEntity(
    id = id,
    sessionId = sessionId,
    role = role.name,
    content = content,
    timestamp = timestamp?.toEpochMilliseconds(),
    toolCalls = if (toolCalls.isNotEmpty()) {
        json.encodeToString(ListSerializer(ToolCall.serializer()), toolCalls)
    } else {
        null
    },
    editedAt = editedAt?.toEpochMilliseconds(),
)

fun MessageEntity.asDomain(): Message = Message(
    id = id,
    sessionId = sessionId,
    role = try {
        MessageRole.valueOf(role)
    } catch (_: Exception) {
        MessageRole.User
    },
    content = content,
    timestamp = timestamp?.let { Instant.fromEpochMilliseconds(it) },
    toolCalls = toolCalls?.let {
        try {
            json.decodeFromString(ListSerializer(ToolCall.serializer()), it)
        } catch (_: Exception) {
            emptyList()
        }
    } ?: emptyList(),
    editedAt = editedAt?.let { Instant.fromEpochMilliseconds(it) },
)
