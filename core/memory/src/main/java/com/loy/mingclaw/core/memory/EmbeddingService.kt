package com.loy.mingclaw.core.memory

interface EmbeddingService {
    suspend fun generateEmbedding(text: String): Result<List<Float>>
    suspend fun generateEmbeddings(texts: List<String>): Result<List<List<Float>>>
    fun similarity(a: List<Float>, b: List<Float>): Float
}
