package com.loy.mingclaw.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class EmbeddingRequest(
    val model: String,
    val input: List<String>,
)

@Serializable
data class EmbeddingResponse(
    val data: List<EmbeddingDataDto> = emptyList(),
    val model: String = "",
    val usage: EmbeddingUsageDto? = null,
)

@Serializable
data class EmbeddingDataDto(
    val index: Int = 0,
    val embedding: List<Float> = emptyList(),
)

@Serializable
data class EmbeddingUsageDto(
    val prompt_tokens: Int = 0,
    val total_tokens: Int = 0,
)
