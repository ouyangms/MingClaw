package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.evolution.BehaviorEvolver
import com.loy.mingclaw.core.evolution.internal.prompts.BehaviorAnalysisPrompt
import com.loy.mingclaw.core.evolution.model.AgentDecision
import com.loy.mingclaw.core.evolution.model.BehaviorAnalysis
import com.loy.mingclaw.core.evolution.model.RuleUpdate
import com.loy.mingclaw.core.evolution.model.RuleUpdateType
import com.loy.mingclaw.core.model.llm.LlmProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BehaviorEvolverImpl @Inject constructor(
    private val llmProvider: LlmProvider,
    private val fileManager: EvolutionFileManager,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : BehaviorEvolver {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class RawBehaviorAnalysis(
        val decisionCount: Int = 0,
        val successRate: Float = 0f,
        val improvementAreas: List<String> = emptyList(),
        val suggestedRules: List<String> = emptyList(),
    )

    @Serializable
    private data class RawRuleUpdate(
        val ruleId: String = "",
        val updateType: String = "ADD",
        val currentRule: String = "",
        val proposedRule: String = "",
        val reason: String = "",
        val confidence: Float = 0.5f,
    )

    override suspend fun recordDecision(decision: AgentDecision): Result<Unit> = try {
        fileManager.writeDecision(decision)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun analyzePatterns(): Result<BehaviorAnalysis> =
        withContext(ioDispatcher) {
            try {
                val decisions = fileManager.readDecisions(Instant.DISTANT_PAST)
                val history = decisions.joinToString("\n") { d ->
                    "[${d.outcome}] ${d.ruleApplied}: ${d.reasoning}"
                }
                val currentRules = fileManager.readAgentRules()

                val promptMessages = BehaviorAnalysisPrompt.analyzePatterns(history, currentRules)
                val llmResult = llmProvider.chat(
                    model = "qwen-plus",
                    messages = promptMessages,
                    temperature = 0.3,
                )

                val response = llmResult.getOrElse { return@withContext Result.failure(it) }
                val raw = parseResponse<RawBehaviorAnalysis>(response.content)

                Result.success(
                    BehaviorAnalysis(
                        decisionCount = raw.decisionCount,
                        successRate = raw.successRate,
                        improvementAreas = raw.improvementAreas,
                        suggestedRules = raw.suggestedRules,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun suggestRuleUpdates(): Result<List<RuleUpdate>> =
        withContext(ioDispatcher) {
            try {
                val analysisResult = analyzePatterns()
                val analysis = analysisResult.getOrElse { return@withContext Result.failure(it) }

                val analysisJson = json.encodeToString(
                    RawBehaviorAnalysis.serializer(),
                    RawBehaviorAnalysis(
                        decisionCount = analysis.decisionCount,
                        successRate = analysis.successRate,
                        improvementAreas = analysis.improvementAreas,
                        suggestedRules = analysis.suggestedRules,
                    ),
                )
                val currentRules = fileManager.readAgentRules()

                val promptMessages = BehaviorAnalysisPrompt.suggestRules(analysisJson, currentRules)
                val llmResult = llmProvider.chat(
                    model = "qwen-plus",
                    messages = promptMessages,
                    temperature = 0.3,
                )

                val response = llmResult.getOrElse { return@withContext Result.failure(it) }
                val rawUpdates = parseListResponse<RawRuleUpdate>(response.content)

                val updates = rawUpdates.map { raw ->
                    RuleUpdate(
                        id = UUID.randomUUID().toString(),
                        ruleId = raw.ruleId,
                        updateType = try {
                            RuleUpdateType.valueOf(raw.updateType)
                        } catch (_: Exception) {
                            RuleUpdateType.ADD
                        },
                        currentRule = raw.currentRule,
                        proposedRule = raw.proposedRule,
                        reason = raw.reason,
                        confidence = raw.confidence,
                    )
                }

                Result.success(updates)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun applyRuleUpdates(updates: List<RuleUpdate>): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                fileManager.backupCurrent("v${Clock.System.now().toEpochMilliseconds()}")
                var content = fileManager.readAgentRules()

                for (update in updates) {
                    content = when (update.updateType) {
                        RuleUpdateType.ADD -> {
                            if (content.isBlank()) {
                                "# Agent Rules\n\n## ${update.ruleId}\n${update.proposedRule}\n"
                            } else {
                                "$content\n\n## ${update.ruleId}\n${update.proposedRule}\n"
                            }
                        }

                        RuleUpdateType.MODIFY -> {
                            if (content.contains("## ${update.ruleId}")) {
                                val sectionRegex = Regex(
                                    "## ${Regex.escape(update.ruleId)}\\s*\\n.*?(?=\\n## |\\z)",
                                    RegexOption.DOT_MATCHES_ALL,
                                )
                                sectionRegex.replace(content) {
                                    "## ${update.ruleId}\n${update.proposedRule}"
                                }
                            } else {
                                content
                            }
                        }

                        RuleUpdateType.DELETE -> {
                            val sectionRegex = Regex(
                                "## ${Regex.escape(update.ruleId)}\\s*\\n.*?(?=\\n## |\\z)",
                                RegexOption.DOT_MATCHES_ALL,
                            )
                            sectionRegex.replace(content, "").trimEnd('\n') + "\n"
                        }

                        else -> content // REORDER, MERGE not implemented in MVP
                    }
                }

                fileManager.writeAgentRules(content)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun rollbackToVersion(version: String): Result<Unit> = try {
        fileManager.restoreVersion(version)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private inline fun <reified T> parseResponse(response: String): T {
        val cleaned = response.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        return json.decodeFromString<T>(cleaned)
    }

    private inline fun <reified T> parseListResponse(response: String): List<T> {
        val cleaned = response.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        return json.decodeFromString<List<T>>(cleaned)
    }
}
