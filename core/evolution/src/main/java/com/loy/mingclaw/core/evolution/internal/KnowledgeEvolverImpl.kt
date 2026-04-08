package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.common.llm.CloudLlm
import com.loy.mingclaw.core.data.repository.MemoryRepository
import com.loy.mingclaw.core.data.repository.SessionRepository
import com.loy.mingclaw.core.evolution.KnowledgeEvolver
import com.loy.mingclaw.core.evolution.internal.prompts.KnowledgeExtractionPrompt
import com.loy.mingclaw.core.evolution.model.ConsolidationResult
import com.loy.mingclaw.core.evolution.model.KnowledgeCategory
import com.loy.mingclaw.core.evolution.model.KnowledgePoint
import com.loy.mingclaw.core.evolution.model.KnowledgeType
import com.loy.mingclaw.core.memory.EmbeddingService
import com.loy.mingclaw.core.model.llm.LlmProvider
import com.loy.mingclaw.core.model.memory.Memory
import com.loy.mingclaw.core.model.memory.MemoryType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class KnowledgeEvolverImpl @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val memoryRepository: MemoryRepository,
    private val embeddingService: EmbeddingService,
    @CloudLlm private val llmProvider: LlmProvider,
    private val fileManager: EvolutionFileManager,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : KnowledgeEvolver {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val DEFAULT_MODEL = "qwen-plus"
    }

    @Serializable
    private data class RawKnowledgePoint(
        val type: String = "FACT",
        val content: String = "",
        val confidence: Float = 0.5f,
        val importance: Float = 0.5f,
        val categories: List<String> = emptyList(),
        val tags: List<String> = emptyList(),
    )

    override suspend fun extractKnowledge(sessionId: String): Result<List<KnowledgePoint>> =
        withContext(ioDispatcher) {
            try {
                val messages = sessionRepository.getMessages(sessionId)
                if (messages.isEmpty()) {
                    return@withContext Result.success(emptyList())
                }

                val conversationContent = messages.joinToString("\n") { msg ->
                    "[${msg.role}] ${msg.content}"
                }

                val promptMessages = KnowledgeExtractionPrompt.build(conversationContent)
                val llmResult = llmProvider.chat(
                    model = DEFAULT_MODEL,
                    messages = promptMessages,
                    temperature = 0.3,
                )

                val response = llmResult.getOrElse { return@withContext Result.failure(it) }
                val rawPoints = parseKnowledgeResponse(response.content)

                val knowledgePoints = rawPoints.map { raw ->
                    KnowledgePoint(
                        id = UUID.randomUUID().toString(),
                        type = try {
                            KnowledgeType.valueOf(raw.type)
                        } catch (_: Exception) {
                            KnowledgeType.FACT
                        },
                        content = raw.content,
                        confidence = raw.confidence.coerceIn(0f, 1f),
                        importance = raw.importance.coerceIn(0f, 1f),
                        categories = raw.categories.mapNotNull {
                            try {
                                KnowledgeCategory.valueOf(it)
                            } catch (_: Exception) {
                                null
                            }
                        }.toSet(),
                        tags = raw.tags.toSet(),
                        extractedAt = Clock.System.now(),
                    )
                }

                Result.success(knowledgePoints)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun consolidateToMemory(knowledge: List<KnowledgePoint>): Result<ConsolidationResult> =
        withContext(ioDispatcher) {
            var added = 0
            var skipped = 0
            val addedPoints = mutableListOf<KnowledgePoint>()

            for (kp in knowledge) {
                val outcome = consolidateOne(kp)
                if (outcome) {
                    added++
                    addedPoints.add(kp)
                } else {
                    skipped++
                }
            }

            // Update human-readable MEMORY.md
            if (addedPoints.isNotEmpty()) {
                val existing = fileManager.readKnowledgeMemory()
                val appendix = addedPoints.joinToString("\n") { kp ->
                    "- [${kp.type}] ${kp.content} (confidence: ${"%.2f".format(kp.confidence)})"
                }
                val updated = if (existing.isBlank()) {
                    "# Memory\n\n$appendix\n"
                } else {
                    "${existing.trimEnd('\n')}\n\n$appendix\n"
                }
                fileManager.writeKnowledgeMemory(updated)
            }

            Result.success(
                ConsolidationResult(
                    added = added,
                    updated = 0,
                    merged = 0,
                    skipped = skipped,
                ),
            )
        }

    /**
     * Consolidates a single knowledge point into memory.
     * Returns true if the point was added, false if it was skipped.
     */
    private suspend fun consolidateOne(kp: KnowledgePoint): Boolean {
        val embedding = embeddingService.generateEmbedding(kp.content)
            .getOrElse { return false }

        val existing = memoryRepository.vectorSearch(
            queryEmbedding = embedding,
            limit = 1,
            threshold = 0.85f,
        ).getOrElse { return false }

        if (existing.isNotEmpty()) return false

        val memory = Memory(
            id = UUID.randomUUID().toString(),
            content = kp.content,
            type = MemoryType.Semantic,
            importance = kp.importance,
            metadata = mapOf(
                "knowledgeType" to kp.type.name,
                "confidence" to kp.confidence.toString(),
                "source" to "evolution",
            ),
            embedding = embedding,
            createdAt = Clock.System.now(),
            accessedAt = Clock.System.now(),
        )

        return memoryRepository.save(memory).isSuccess
    }

    override suspend fun searchMemory(query: String): Result<List<KnowledgePoint>> =
        withContext(ioDispatcher) {
            try {
                val embedding = embeddingService.generateEmbedding(query).getOrElse {
                    return@withContext Result.failure(it)
                }

                val memories = memoryRepository.vectorSearch(
                    queryEmbedding = embedding,
                    limit = 10,
                    threshold = 0.5f,
                ).getOrElse {
                    return@withContext Result.failure(it)
                }

                val knowledgePoints = memories.map { memory ->
                    val type = try {
                        KnowledgeType.valueOf(memory.metadata["knowledgeType"] ?: "FACT")
                    } catch (_: Exception) {
                        KnowledgeType.FACT
                    }
                    KnowledgePoint(
                        id = memory.id,
                        type = type,
                        content = memory.content,
                        confidence = memory.metadata["confidence"]?.toFloatOrNull() ?: 0.5f,
                        importance = memory.importance,
                        categories = emptySet(),
                        tags = emptySet(),
                        extractedAt = memory.createdAt,
                    )
                }

                Result.success(knowledgePoints)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseKnowledgeResponse(response: String): List<RawKnowledgePoint> {
        return try {
            val cleaned = response.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            json.decodeFromString<List<RawKnowledgePoint>>(cleaned)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
