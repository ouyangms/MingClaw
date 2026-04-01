package com.loy.mingclaw.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionResponse(
    val id: String = "",
    val choices: List<ChatChoiceDto> = emptyList(),
    val usage: UsageDto? = null,
)

@Serializable
data class ChatChoiceDto(
    val index: Int = 0,
    val message: ChatMessageDto? = null,
    val finish_reason: String? = null,
)

@Serializable
data class UsageDto(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0,
)
