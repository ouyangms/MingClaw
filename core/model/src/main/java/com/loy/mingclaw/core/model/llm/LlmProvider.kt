package com.loy.mingclaw.core.model.llm

import kotlinx.coroutines.flow.Flow

/**
 * LLM 提供者抽象接口，兼容本地和云端模型。
 * 云端实现通过 core:network 模块提供。
 * 本地实现预留空壳，未来可接入端侧模型。
 */
interface LlmProvider {
    /** 同步对话补全，返回完整响应文本 */
    suspend fun chat(
        model: String,
        messages: List<ChatMessage>,
        temperature: Double = 0.7,
        maxTokens: Int = 4096,
    ): Result<ChatResponse>

    /** 流式对话补全 */
    suspend fun chatStream(
        model: String,
        messages: List<ChatMessage>,
        temperature: Double = 0.7,
        maxTokens: Int = 4096,
    ): Result<Flow<ChatChunk>>

    /** 生成文本嵌入向量 */
    suspend fun embed(
        model: String,
        texts: List<String>,
    ): Result<List<List<Float>>>

    /** 当前提供者类型标识 */
    fun providerName(): String
}

/** 聊天消息 */
data class ChatMessage(
    val role: String,
    val content: String,
)

/** 聊天响应 */
data class ChatResponse(
    val id: String = "",
    val content: String = "",
    val model: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val finishReason: String? = null,
)

/** 流式聊天块 */
data class ChatChunk(
    val id: String = "",
    val delta: String = "",
    val finishReason: String? = null,
)
