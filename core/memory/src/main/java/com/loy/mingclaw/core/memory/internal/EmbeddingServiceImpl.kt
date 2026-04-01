package com.loy.mingclaw.core.memory.internal

import com.loy.mingclaw.core.memory.EmbeddingService
import com.loy.mingclaw.core.network.LlmService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EmbeddingServiceImpl @Inject constructor(
    private val llmService: LlmService,
) : EmbeddingService {

    override suspend fun generateEmbedding(text: String): Result<List<Float>> {
        return generateEmbeddings(listOf(text)).map { it.firstOrNull() ?: emptyList() }
    }

    override suspend fun generateEmbeddings(texts: List<String>): Result<List<List<Float>>> {
        return llmService.generateEmbedding(texts = texts)
    }

    override fun similarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
}
