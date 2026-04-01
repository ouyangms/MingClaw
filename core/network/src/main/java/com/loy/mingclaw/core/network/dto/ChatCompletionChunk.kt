package com.loy.mingclaw.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionChunk(
    val id: String = "",
    val choices: List<ChunkChoiceDto> = emptyList(),
)

@Serializable
data class ChunkChoiceDto(
    val index: Int = 0,
    val delta: ChunkDeltaDto? = null,
    val finish_reason: String? = null,
)

@Serializable
data class ChunkDeltaDto(
    val role: String? = null,
    val content: String? = null,
)
