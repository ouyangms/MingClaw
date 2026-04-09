package com.loy.mingclaw.core.database.dao

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VectorSearchDao @Inject constructor(
    private val embeddingDao: EmbeddingDao,
) {
    /**
     * Performs vector similarity search.
     * Attempts sqlite-vec if available, falls back to in-memory cosine similarity.
     */
    suspend fun searchBySimilarity(
        queryEmbedding: List<Float>,
        limit: Int,
        threshold: Float,
    ): List<Pair<String, Float>> {
        // TODO: When sqlite-vec native library is integrated, use:
        //  SELECT id, vec_distance_cosine(embedding, ?) as distance
        //  FROM vec_embeddings WHERE distance <= ? ORDER BY distance LIMIT ?
        // For now, use improved in-memory search via BLOB-optimized loading
        return inMemorySearch(queryEmbedding, limit, threshold)
    }

    private suspend fun inMemorySearch(
        queryEmbedding: List<Float>,
        limit: Int,
        threshold: Float,
    ): List<Pair<String, Float>> {
        val allIds = embeddingDao.getAllIds()
        val results = mutableListOf<Pair<String, Float>>()

        for (id in allIds) {
            val entity = embeddingDao.getById(id) ?: continue
            val embedding = blobToFloats(entity.embedding)
            if (embedding.isEmpty()) continue
            val sim = cosineSimilarity(queryEmbedding, embedding)
            if (sim >= threshold) {
                results.add(id to sim)
            }
        }

        return results
            .sortedByDescending { it.second }
            .take(limit)
    }

    companion object {
        fun floatsToBlob(floats: List<Float>): ByteArray {
            val buffer = ByteBuffer.allocate(floats.size * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
            floats.forEach { buffer.putFloat(it) }
            return buffer.array()
        }

        fun blobToFloats(blob: ByteArray): List<Float> {
            if (blob.isEmpty()) return emptyList()
            val buffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
            val floats = mutableListOf<Float>()
            while (buffer.remaining() >= 4) {
                floats.add(buffer.getFloat())
            }
            return floats
        }

        fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
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
}
