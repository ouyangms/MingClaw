package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.common.llm.CloudLlm
import com.loy.mingclaw.core.evolution.CapabilityEvolver
import com.loy.mingclaw.core.evolution.internal.prompts.CapabilityGapPrompt
import com.loy.mingclaw.core.evolution.model.CapabilityGap
import com.loy.mingclaw.core.evolution.model.Priority
import com.loy.mingclaw.core.evolution.model.SkillLevel
import com.loy.mingclaw.core.model.llm.LlmProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CapabilityEvolverImpl @Inject constructor(
    @CloudLlm private val llmProvider: LlmProvider,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : CapabilityEvolver {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun identifyCapabilityGaps(): Result<List<CapabilityGap>> =
        withContext(ioDispatcher) {
            try {
                val promptMessages = CapabilityGapPrompt.build("无失败任务记录，但请评估当前 Agent 的基础能力覆盖度")
                val llmResult = llmProvider.chat(
                    model = "qwen-plus",
                    messages = promptMessages,
                    temperature = 0.3,
                )

                val response = llmResult.getOrElse { return@withContext Result.success(emptyList()) }
                val gaps = parseGaps(response.content)
                Result.success(gaps)
            } catch (e: Exception) {
                Result.success(emptyList())
            }
        }

    private fun parseGaps(content: String): List<CapabilityGap> {
        val jsonContent = extractJsonArray(content)
        return try {
            val dtos = json.decodeFromString<List<GapDto>>(jsonContent)
            dtos.map { dto ->
                CapabilityGap(
                    id = UUID.randomUUID().toString(),
                    capability = dto.capability,
                    currentLevel = try {
                        SkillLevel.valueOf(dto.currentLevel)
                    } catch (_: Exception) {
                        SkillLevel.NONE
                    },
                    desiredLevel = try {
                        SkillLevel.valueOf(dto.desiredLevel)
                    } catch (_: Exception) {
                        SkillLevel.BASIC
                    },
                    priority = try {
                        Priority.valueOf(dto.priority)
                    } catch (_: Exception) {
                        Priority.MEDIUM
                    },
                    detectedAt = Clock.System.now(),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractJsonArray(text: String): String {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        return if (start >= 0 && end > start) {
            text.substring(start, end + 1)
        } else {
            "[]"
        }
    }

    @Serializable
    private data class GapDto(
        val capability: String,
        val currentLevel: String,
        val desiredLevel: String,
        val priority: String,
    )
}
