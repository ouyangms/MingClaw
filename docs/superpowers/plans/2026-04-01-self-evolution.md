# Self-Evolution Layer (core:evolution) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the Self-Evolution layer providing behavior optimization, knowledge accumulation, and capability gap detection for MingClaw.

**Architecture:** Single module `core:evolution` with 6 public interfaces (FeedbackCollector, KnowledgeEvolver, BehaviorEvolver, CapabilityEvolver, EvolutionTriggerManager, EvolutionEngine), internal implementations using `@CloudLlm LlmProvider` for LLM-powered analysis, file system for Markdown/JSON persistence, and core:data + core:memory for structured data access. A state machine orchestrates the evolution lifecycle.

**Tech Stack:** Kotlin, Hilt DI, kotlinx.coroutines (Flow), kotlinx.serialization, kotlinx-datetime, MockK + Turbine for tests

**Design Spec:** `docs/superpowers/specs/2026-04-01-self-evolution-design.md`

---

## File Structure

### New module: `core/evolution/`

```
core/evolution/
├── build.gradle.kts
└── src/
    ├── main/java/com/loy/mingclaw/core/evolution/
    │   ├── model/
    │   │   ├── EvolutionState.kt
    │   │   ├── EvolutionTypes.kt
    │   │   ├── FeedbackTypes.kt
    │   │   ├── KnowledgeTypes.kt
    │   │   ├── BehaviorTypes.kt
    │   │   └── CapabilityTypes.kt
    │   ├── FeedbackCollector.kt
    │   ├── KnowledgeEvolver.kt
    │   ├── BehaviorEvolver.kt
    │   ├── CapabilityEvolver.kt
    │   ├── EvolutionTriggerManager.kt
    │   ├── EvolutionEngine.kt
    │   ├── internal/
    │   │   ├── FeedbackCollectorImpl.kt
    │   │   ├── KnowledgeEvolverImpl.kt
    │   │   ├── BehaviorEvolverImpl.kt
    │   │   ├── CapabilityEvolverImpl.kt
    │   │   ├── EvolutionTriggerManagerImpl.kt
    │   │   ├── EvolutionEngineImpl.kt
    │   │   ├── EvolutionStateMachine.kt
    │   │   ├── EvolutionFileManager.kt
    │   │   └── prompts/
    │   │       ├── BehaviorAnalysisPrompt.kt
    │   │       ├── KnowledgeExtractionPrompt.kt
    │   │       └── CapabilityGapPrompt.kt
    │   └── di/
    │       └── EvolutionModule.kt
    └── test/java/com/loy/mingclaw/core/evolution/
        ├── model/
        │   ├── EvolutionTypesTest.kt
        │   ├── FeedbackTypesTest.kt
        │   └── BehaviorTypesTest.kt
        ├── internal/
        │   ├── EvolutionStateMachineTest.kt
        │   ├── EvolutionFileManagerTest.kt
        │   ├── FeedbackCollectorImplTest.kt
        │   ├── KnowledgeEvolverImplTest.kt
        │   ├── BehaviorEvolverImplTest.kt
        │   ├── CapabilityEvolverImplTest.kt
        │   ├── EvolutionTriggerManagerImplTest.kt
        │   └── EvolutionEngineImplTest.kt
```

### Modified files in existing modules:

```
core/model/src/main/java/com/loy/mingclaw/core/model/Event.kt    # Add evolution event subtypes
settings.gradle.kts                                                # Add :core:evolution
```

---

## Phase E1: Module Infrastructure + Domain Types + FeedbackCollector

### Task 1: Register module and create build.gradle.kts

**Files:**
- Create: `core/evolution/build.gradle.kts`
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Add `include(":core:evolution")` to settings.gradle.kts**

In `settings.gradle.kts`, add after the `include(":core:data")` line:

```kotlin
include(":core:evolution")
```

- [ ] **Step 2: Create `core/evolution/build.gradle.kts`**

```kotlin
plugins {
    id("mingclaw.android.library")
    id("mingclaw.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.loy.mingclaw.core.evolution"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:kernel"))
    implementation(project(":core:workspace"))
    implementation(project(":core:data"))
    implementation(project(":core:memory"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
```

- [ ] **Step 3: Create directory structure**

```bash
mkdir -p core/evolution/src/main/java/com/loy/mingclaw/core/evolution/model
mkdir -p core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/prompts
mkdir -p core/evolution/src/main/java/com/loy/mingclaw/core/evolution/di
mkdir -p core/evolution/src/test/java/com/loy/mingclaw/core/evolution/model
mkdir -p core/evolution/src/test/java/com/loy/mingclaw/core/evolution/internal
```

- [ ] **Step 4: Verify build resolves**

Run: `./gradlew :core:evolution:assembleDebug`
Expected: BUILD SUCCESSFUL (no source files yet, but module resolves)

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts core/evolution/
git commit -m "chore: scaffold core:evolution module"
```

---

### Task 2: Domain types — EvolutionState + EvolutionTypes

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/model/EvolutionState.kt`
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/model/EvolutionTypes.kt`
- Test: `core/evolution/src/test/java/com/loy/mingclaw/core/evolution/model/EvolutionTypesTest.kt`

- [ ] **Step 1: Write EvolutionState.kt**

```kotlin
package com.loy.mingclaw.core.evolution.model

sealed class EvolutionState {
    data object Idle : EvolutionState()
    data class Analyzing(val trigger: EvolutionTrigger) : EvolutionState()
    data class AwaitingApproval(val proposals: List<EvolutionProposal>) : EvolutionState()
    data class Applying(val proposals: List<EvolutionProposal>) : EvolutionState()
    data class Completed(val results: List<EvolutionResult>) : EvolutionState()
    data class Failed(val error: String) : EvolutionState()
}
```

- [ ] **Step 2: Write EvolutionTypes.kt**

```kotlin
package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class EvolutionType { BEHAVIOR, KNOWLEDGE, CAPABILITY }

@Serializable
enum class EvolutionTrigger {
    USER_FEEDBACK, TASK_FAILURE, PERFORMANCE_DEGRADATION,
    SCHEDULED, MANUAL, KNOWLEDGE_THRESHOLD, CAPABILITY_GAP
}

@Serializable
enum class EvolutionPriority { LOW, MEDIUM, HIGH, IMMEDIATE }

data class EvolutionContext(
    val sessionId: String,
    val feedbackScore: Float,
    val taskSuccessRate: Float,
    val memoryCount: Int,
    val lastEvolution: Instant?,
)

@Serializable
data class EvolutionProposal(
    val id: String,
    val type: EvolutionType,
    val description: String,
    val reason: String,
    val expectedImpact: String,
    val priority: EvolutionPriority,
    val confidence: Float,
)

@Serializable
data class EvolutionResult(
    val proposalId: String,
    val success: Boolean,
    val changes: List<String>,
    val error: String? = null,
)
```

- [ ] **Step 3: Write EvolutionTypesTest.kt**

```kotlin
package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class EvolutionTypesTest {

    @Test
    fun evolutionType_hasAllThreePaths() {
        assertEquals(3, EvolutionType.entries.size)
        assertEquals(EvolutionType.BEHAVIOR, EvolutionType.valueOf("BEHAVIOR"))
        assertEquals(EvolutionType.KNOWLEDGE, EvolutionType.valueOf("KNOWLEDGE"))
        assertEquals(EvolutionType.CAPABILITY, EvolutionType.valueOf("CAPABILITY"))
    }

    @Test
    fun evolutionTrigger_hasAllTriggers() {
        assertEquals(7, EvolutionTrigger.entries.size)
    }

    @Test
    fun evolutionProposal_isSerializable() {
        val proposal = EvolutionProposal(
            id = "p1",
            type = EvolutionType.BEHAVIOR,
            description = "Test proposal",
            reason = "Testing",
            expectedImpact = "None",
            priority = EvolutionPriority.LOW,
            confidence = 0.8f,
        )
        assertEquals("p1", proposal.id)
        assertEquals(EvolutionType.BEHAVIOR, proposal.type)
        assertEquals(0.8f, proposal.confidence, 0.01f)
    }

    @Test
    fun evolutionResult_tracksSuccess() {
        val result = EvolutionResult(
            proposalId = "p1",
            success = true,
            changes = listOf("rule updated"),
        )
        assertEquals(true, result.success)
        assertEquals(1, result.changes.size)
        assertEquals(null, result.error)
    }

    @Test
    fun evolutionState_idleIsSingleton() {
        val a = EvolutionState.Idle
        val b = EvolutionState.Idle
        assertEquals(a, b)
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:evolution:test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add core/evolution/src/main/ core/evolution/src/test/
git commit -m "feat: add evolution domain types (EvolutionState, EvolutionTypes)"
```

---

### Task 3: Domain types — FeedbackTypes + KnowledgeTypes + BehaviorTypes + CapabilityTypes

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/model/FeedbackTypes.kt`
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/model/KnowledgeTypes.kt`
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/model/BehaviorTypes.kt`
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/model/CapabilityTypes.kt`
- Test: `core/evolution/src/test/java/com/loy/mingclaw/core/evolution/model/FeedbackTypesTest.kt`
- Test: `core/evolution/src/test/java/com/loy/mingclaw/core/evolution/model/BehaviorTypesTest.kt`

- [ ] **Step 1: Write FeedbackTypes.kt**

```kotlin
package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class ExplicitFeedbackType { THUMBS_UP, THUMBS_DOWN, RATING, CORRECTION, SUGGESTION }

@Serializable
enum class FeedbackAspect { ACCURACY, RELEVANCE, COMPLETENESS, CLARITY, TIMELINESS, TONE, OVERALL }

@Serializable
enum class ImplicitAction { REGENERATED, EDITED, COPIED, IGNORED, FOLLOWED_UP, ABANDONED }

@Serializable
enum class Trend { IMPROVING, STABLE, DECLINING }

sealed class UserFeedback {
    abstract val feedbackId: String
    abstract val timestamp: Instant
    abstract val sessionId: String

    @Serializable
    data class Explicit(
        override val feedbackId: String,
        override val timestamp: Instant,
        override val sessionId: String,
        val type: ExplicitFeedbackType,
        val rating: Int,
        val comment: String,
        val aspect: FeedbackAspect,
    ) : UserFeedback()

    @Serializable
    data class Implicit(
        override val feedbackId: String,
        override val timestamp: Instant,
        override val sessionId: String,
        val action: ImplicitAction,
        val confidence: Float,
    ) : UserFeedback()
}

data class FeedbackSummary(
    val periodStart: Instant,
    val periodEnd: Instant,
    val totalFeedbacks: Int,
    val explicitCount: Int,
    val implicitCount: Int,
    val averageRating: Float,
    val ratingDistribution: Map<Int, Int>,
)
```

- [ ] **Step 2: Write KnowledgeTypes.kt**

```kotlin
package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class KnowledgeType { FACT, CONCEPT, PROCEDURE, PRINCIPLE, PREFERENCE, PATTERN, EXPERIENCE }

@Serializable
enum class KnowledgeCategory { USER_PROFILE, TASK_PATTERN, DOMAIN_KNOWLEDGE, COMMON_SENSE, PREFERENCE, CONTEXT }

@Serializable
enum class Importance { CRITICAL, HIGH, MEDIUM, LOW, TRIVIAL }

@Serializable
data class KnowledgePoint(
    val id: String,
    val type: KnowledgeType,
    val content: String,
    val confidence: Float,
    val importance: Float,
    val categories: Set<KnowledgeCategory>,
    val tags: Set<String>,
    val extractedAt: Instant,
)

@Serializable
data class ConsolidationResult(
    val added: Int,
    val updated: Int,
    val merged: Int,
    val skipped: Int,
)
```

- [ ] **Step 3: Write BehaviorTypes.kt**

```kotlin
package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class RuleUpdateType { ADD, MODIFY, DELETE, REORDER, MERGE }

@Serializable
enum class DecisionOutcome { SUCCESS, FAILURE, PARTIAL }

@Serializable
data class AgentDecision(
    val decisionId: String,
    val timestamp: Instant,
    val ruleApplied: String,
    val reasoning: String,
    val outcome: DecisionOutcome,
)

@Serializable
data class RuleUpdate(
    val id: String,
    val ruleId: String,
    val updateType: RuleUpdateType,
    val currentRule: String,
    val proposedRule: String,
    val reason: String,
    val confidence: Float,
)

@Serializable
data class BehaviorAnalysis(
    val decisionCount: Int,
    val successRate: Float,
    val improvementAreas: List<String>,
    val suggestedRules: List<String>,
)
```

- [ ] **Step 4: Write CapabilityTypes.kt**

```kotlin
package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class SkillLevel { NONE, BASIC, INTERMEDIATE, ADVANCED, EXPERT }

@Serializable
enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }

@Serializable
data class CapabilityGap(
    val id: String,
    val capability: String,
    val currentLevel: SkillLevel,
    val desiredLevel: SkillLevel,
    val priority: Priority,
    val detectedAt: Instant,
)
```

- [ ] **Step 5: Write FeedbackTypesTest.kt**

```kotlin
package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedbackTypesTest {

    private val testInstant = Instant.fromEpochMilliseconds(1700000000000)

    @Test
    fun explicitFeedback_holdsAllFields() {
        val feedback = UserFeedback.Explicit(
            feedbackId = "fb1",
            timestamp = testInstant,
            sessionId = "s1",
            type = ExplicitFeedbackType.THUMBS_UP,
            rating = 5,
            comment = "Great",
            aspect = FeedbackAspect.ACCURACY,
        )
        assertEquals("fb1", feedback.feedbackId)
        assertEquals(ExplicitFeedbackType.THUMBS_UP, feedback.type)
        assertEquals(5, feedback.rating)
    }

    @Test
    fun implicitFeedback_holdsAllFields() {
        val feedback = UserFeedback.Implicit(
            feedbackId = "fb2",
            timestamp = testInstant,
            sessionId = "s1",
            action = ImplicitAction.REGENERATED,
            confidence = 0.9f,
        )
        assertEquals(ImplicitAction.REGENERATED, feedback.action)
        assertEquals(0.9f, feedback.confidence, 0.01f)
    }

    @Test
    fun allFeedbackEnumsHaveExpectedCount() {
        assertEquals(5, ExplicitFeedbackType.entries.size)
        assertEquals(7, FeedbackAspect.entries.size)
        assertEquals(6, ImplicitAction.entries.size)
    }
}
```

- [ ] **Step 6: Write BehaviorTypesTest.kt**

```kotlin
package com.loy.mingclaw.core.evolution.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class BehaviorTypesTest {

    private val testInstant = Instant.fromEpochMilliseconds(1700000000000)

    @Test
    fun agentDecision_roundTripViaCopy() {
        val decision = AgentDecision(
            decisionId = "d1",
            timestamp = testInstant,
            ruleApplied = "rule-1",
            reasoning = "test",
            outcome = DecisionOutcome.SUCCESS,
        )
        val copy = decision.copy(outcome = DecisionOutcome.FAILURE)
        assertEquals(DecisionOutcome.FAILURE, copy.outcome)
        assertEquals("d1", copy.decisionId)
    }

    @Test
    fun ruleUpdate_hasAllUpdateTypes() {
        assertEquals(5, RuleUpdateType.entries.size)
    }

    @Test
    fun behaviorAnalysis_summarizesCorrectly() {
        val analysis = BehaviorAnalysis(
            decisionCount = 10,
            successRate = 0.8f,
            improvementAreas = listOf("response length"),
            suggestedRules = listOf("be concise"),
        )
        assertEquals(10, analysis.decisionCount)
        assertEquals(0.8f, analysis.successRate, 0.01f)
    }
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew :core:evolution:test`
Expected: All tests pass

- [ ] **Step 8: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add feedback, knowledge, behavior, capability domain types"
```

---

### Task 4: Add Evolution events to core:model

**Files:**
- Modify: `core/model/src/main/java/com/loy/mingclaw/core/model/Event.kt`

- [ ] **Step 1: Add evolution event subtypes to Event.kt**

Append three new event types after the existing `ConfigUpdated` class inside the `Event` sealed interface:

```kotlin
    @Serializable
    data class EvolutionTriggered(
        val evolutionType: String,
        val reason: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class EvolutionCompleted(
        val evolutionId: String,
        val changes: List<String>,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event

    @Serializable
    data class EvolutionFailed(
        val evolutionId: String,
        val error: String,
        override val timestamp: Instant = Clock.System.now(),
    ) : Event
```

- [ ] **Step 2: Verify core:model builds**

Run: `./gradlew :core:model:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add core/model/src/main/java/com/loy/mingclaw/core/model/Event.kt
git commit -m "feat: add evolution event types to core:model Event sealed interface"
```

---

### Task 5: EvolutionFileManager

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/EvolutionFileManager.kt`
- Test: `core/evolution/src/test/java/com/loy/mingclaw/core/evolution/internal/EvolutionFileManagerTest.kt`

- [ ] **Step 1: Write EvolutionFileManager.kt**

The file manager uses `WorkspaceManager.getCurrentWorkspace().path` as the root for all evolution files. All file I/O runs on `@IODispatcher`. Uses `kotlinx.serialization` for JSON encoding/decoding.

Key methods:
- `ensureDirectories()` — creates `EXPERIENCE/decisions/`, `EXPERIENCE/feedbacks/`, `.evolution/rollback/`
- `readAgentRules()` / `writeAgentRules(content)` — reads/writes `AGENTS.md` at workspace root
- `readKnowledgeMemory()` / `writeKnowledgeMemory(content)` — reads/writes `MEMORY.md` at workspace root
- `writeDecision(decision)` — serializes `AgentDecision` to `EXPERIENCE/decisions/{decisionId}.json`
- `writeFeedback(feedback)` — serializes `UserFeedback` to `EXPERIENCE/feedbacks/{feedbackId}.json`
- `readDecisions(since)` — scans `EXPERIENCE/decisions/`, filters by `timestamp >= since`, deserializes
- `readFeedbacks(since)` — scans `EXPERIENCE/feedbacks/`, filters by `timestamp >= since`, deserializes
- `backupCurrent(version)` — copies current `AGENTS.md` and `MEMORY.md` to `.evolution/rollback/v{version}/`
- `restoreVersion(version)` — copies from `.evolution/rollback/v{version}/` back to workspace root

Constructor injects `WorkspaceManager` and `@IODispatcher`. All methods are `suspend` and use `withContext(ioDispatcher)`.

Serialization uses a private `Json { ignoreUnknownKeys = true }` instance and the `@Serializable` annotations on the domain types.

- [ ] **Step 2: Write EvolutionFileManagerTest.kt**

Tests use `@TempDir` (JUnit 5) or a manual temp dir approach with MockK:
- Mock `WorkspaceManager.getCurrentWorkspace()` to return a workspace pointing at a temp directory
- Test `writeDecision` + `readDecisions` round-trip: write an `AgentDecision`, read it back, verify fields match
- Test `writeFeedback` + `readFeedbacks` round-trip with both `UserFeedback.Explicit` and `UserFeedback.Implicit`
- Test `readDecisions(since)` filters by timestamp correctly (write 3 decisions with different timestamps, read with a middle timestamp, verify only newer ones returned)
- Test `writeAgentRules` + `readAgentRules` round-trip: write markdown string, read back identical
- Test `writeKnowledgeMemory` + `readKnowledgeMemory` round-trip
- Test `backupCurrent` + `restoreVersion`: write agent rules, backup, modify rules, restore, verify original content
- Test `readAgentRules` returns empty string when file does not exist
- Use `Dispatchers.Unconfined` for the ioDispatcher parameter

- [ ] **Step 3: Run tests**

Run: `./gradlew :core:evolution:test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add EvolutionFileManager for AGENTS.md/MEMORY.md/EXPERIENCE persistence"
```

---

### Task 6: FeedbackCollector interface + implementation

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/FeedbackCollector.kt`
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/FeedbackCollectorImpl.kt`
- Test: `core/evolution/src/test/java/com/loy/mingclaw/core/evolution/internal/FeedbackCollectorImplTest.kt`

- [ ] **Step 1: Write FeedbackCollector.kt interface**

```kotlin
package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.FeedbackSummary
import com.loy.mingclaw.core.evolution.model.ImplicitAction
import com.loy.mingclaw.core.evolution.model.UserFeedback
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface FeedbackCollector {
    suspend fun collectExplicitFeedback(feedback: UserFeedback.Explicit): Result<Unit>
    suspend fun collectImplicitFeedback(action: ImplicitAction, sessionId: String): Result<Unit>
    suspend fun getFeedbackSummary(since: Instant): FeedbackSummary
    fun observeFeedback(): Flow<UserFeedback>
}
```

- [ ] **Step 2: Write FeedbackCollectorImpl.kt**

Constructor injects `EvolutionFileManager` and `@IODispatcher`.

`collectExplicitFeedback`: validates rating is in 1..5, generates a UUID feedbackId if blank, calls `fileManager.writeFeedback(feedback)`, emits to internal `MutableSharedFlow`.

`collectImplicitFeedback`: maps action to confidence using a map:
- REGENERATED → 0.9f, EDITED → 0.8f, FOLLOWED_UP → 0.6f, COPIED → 0.3f, IGNORED → 0.2f, ABANDONED → 0.7f
Generates UUID feedbackId, creates `UserFeedback.Implicit`, calls `fileManager.writeFeedback()`, emits to shared flow.

`getFeedbackSummary`: calls `fileManager.readFeedbacks(since)`, separates explicit vs implicit, computes average rating and distribution.

`observeFeedback`: returns the shared flow.

- [ ] **Step 3: Write FeedbackCollectorImplTest.kt**

Mock `EvolutionFileManager` (mockk). Use `Dispatchers.Unconfined`.

Tests:
- `collectExplicitFeedback_delegatesToFileManager`: verify `fileManager.writeFeedback()` called with correct data
- `collectExplicitFeedback_validatesRating`: rating 0 or 6 should return failure
- `collectImplicitFeedback_mapsConfidence`: verify REGENERATED maps to 0.9f confidence
- `collectImplicitFeedback_delegatesToFileManager`: verify writeFeedback called
- `getFeedbackSummary_aggregatesCorrectly`: mock `readFeedbacks` to return a mix of explicit and implicit feedback, verify summary counts and average
- `getFeedbackSummary_emptyPeriod`: no feedbacks → zero counts, 0f average
- `observeFeedback_emitsCollectedFeedback`: collect feedback then observe flow receives it

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:evolution:test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add FeedbackCollector interface and implementation"
```

---

## Phase E2: KnowledgeEvolver

### Task 7: KnowledgeEvolver interface + KnowledgeExtractionPrompt

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/KnowledgeEvolver.kt`
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/prompts/KnowledgeExtractionPrompt.kt`

- [ ] **Step 1: Write KnowledgeEvolver.kt interface**

```kotlin
package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.ConsolidationResult
import com.loy.mingclaw.core.evolution.model.KnowledgePoint
import kotlinx.datetime.Instant

interface KnowledgeEvolver {
    suspend fun extractKnowledge(sessionId: String): Result<List<KnowledgePoint>>
    suspend fun consolidateToMemory(knowledge: List<KnowledgePoint>): Result<ConsolidationResult>
    suspend fun searchMemory(query: String): Result<List<KnowledgePoint>>
}
```

- [ ] **Step 2: Write KnowledgeExtractionPrompt.kt**

```kotlin
package com.loy.mingclaw.core.evolution.internal.prompts

import com.loy.mingclaw.core.model.llm.ChatMessage

internal object KnowledgeExtractionPrompt {

    fun build(conversationContent: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """你是一个知识提取专家。从对话内容中提取有价值的知识点。
每个知识点包含以下字段，以 JSON 数组格式返回：
- type: FACT/CONCEPT/PROCEDURE/PRINCIPLE/PREFERENCE/PATTERN/EXPERIENCE
- content: 知识内容（简洁的一句话）
- confidence: 置信度 (0.0-1.0)
- importance: 重要性 (0.0-1.0)
- categories: 分类数组，可选值: USER_PROFILE/TASK_PATTERN/DOMAIN_KNOWLEDGE/COMMON_SENSE/PREFERENCE/CONTEXT
- tags: 标签数组（字符串）

只返回 JSON 数组，不要其他文字。如果没有有价值的知识点，返回空数组 []。""",
        ),
        ChatMessage(
            role = "user",
            content = "请从以下对话中提取知识点：\n\n$conversationContent",
        ),
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add KnowledgeEvolver interface and extraction prompt"
```

---

### Task 8: KnowledgeEvolverImpl

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/KnowledgeEvolverImpl.kt`
- Test: `core/evolution/src/test/java/com/loy/mingclaw/core/evolution/internal/KnowledgeEvolverImplTest.kt`

- [ ] **Step 1: Write KnowledgeEvolverImpl.kt**

Constructor injects: `SessionRepository`, `MemoryRepository`, `EmbeddingService`, `@CloudLlm LlmProvider`, `EvolutionFileManager`, `@IODispatcher`.

`extractKnowledge(sessionId)`:
1. Call `sessionRepository.getMessages(sessionId)` to get conversation
2. Join messages into a single string: `"[{role}] {content}"` per line
3. Call `KnowledgeExtractionPrompt.build(conversationContent)` to get prompt messages
4. Call `llmProvider.chat(model = "qwen-plus", messages = promptMessages)` — use the model name from the provider's response or a configurable default
5. Parse the LLM response JSON array into `List<KnowledgePoint>` using `kotlinx.serialization`
6. Assign UUID ids and `Clock.System.now()` as `extractedAt`
7. Return `Result.success(knowledgePoints)`

`consolidateToMemory(knowledge)`:
1. For each knowledge point, generate embedding via `embeddingService.generateEmbedding(kp.content)`
2. Use `memoryRepository.vectorSearch(embedding, limit = 1, threshold = 0.85f)` to check for duplicates
3. If similar memory exists → skip (increment skipped count)
4. If no similar → call `memoryRepository.save()` to persist as a `Memory` (map KnowledgePoint to Memory domain type), also append to MEMORY.md via `fileManager.writeKnowledgeMemory()`
5. Return `ConsolidationResult` with counts

`searchMemory(query)`:
1. Generate query embedding via `embeddingService.generateEmbedding(query)`
2. Call `memoryRepository.vectorSearch(queryEmbedding, limit = 10, threshold = 0.5f)`
3. Map `Memory` results back to `KnowledgePoint` (extract type from metadata, or default to FACT)
4. Return `Result.success(knowledgePoints)`

- [ ] **Step 2: Write KnowledgeEvolverImplTest.kt**

Mock all dependencies. Use `Dispatchers.Unconfined`.

Tests:
- `extractKnowledge_extractsFromConversation`: mock SessionRepository to return messages, mock LLM to return JSON array of knowledge points, verify parsed correctly
- `extractKnowledge_handlesEmptyConversation`: empty messages → empty result
- `extractKnowledge_handlesLlmFailure`: LLM returns failure → Result.failure
- `consolidateToMemory_savesNewKnowledge`: mock embedding to return a vector, mock vectorSearch to return empty (no duplicates), verify save() called
- `consolidateToMemory_skipsDuplicates`: mock vectorSearch to return existing memory (similarity > 0.85), verify save() NOT called, skipped count = 1
- `consolidateToMemory_handlesEmbeddingFailure`: embedding fails → skipped
- `searchMemory_delegatesToRepository`: mock embedding + vectorSearch, verify mapped correctly

- [ ] **Step 3: Run tests**

Run: `./gradlew :core:evolution:test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add KnowledgeEvolver implementation with LLM extraction and dedup"
```

---

## Phase E3: BehaviorEvolver

### Task 9: BehaviorEvolver interface + BehaviorAnalysisPrompt

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/BehaviorEvolver.kt`
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/prompts/BehaviorAnalysisPrompt.kt`

- [ ] **Step 1: Write BehaviorEvolver.kt interface**

```kotlin
package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.AgentDecision
import com.loy.mingclaw.core.evolution.model.BehaviorAnalysis
import com.loy.mingclaw.core.evolution.model.RuleUpdate

interface BehaviorEvolver {
    suspend fun recordDecision(decision: AgentDecision): Result<Unit>
    suspend fun analyzePatterns(): Result<BehaviorAnalysis>
    suspend fun suggestRuleUpdates(): Result<List<RuleUpdate>>
    suspend fun applyRuleUpdates(updates: List<RuleUpdate>): Result<Unit>
    suspend fun rollbackToVersion(version: String): Result<Unit>
}
```

- [ ] **Step 2: Write BehaviorAnalysisPrompt.kt**

```kotlin
package com.loy.mingclaw.core.evolution.internal.prompts

import com.loy.mingclaw.core.model.llm.ChatMessage

internal object BehaviorAnalysisPrompt {

    fun analyzePatterns(decisionHistory: String, currentRules: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """你是一个 AI 行为分析专家。分析 Agent 的决策历史，识别成功和失败的模式。
返回 JSON 对象：
{
  "decisionCount": 数字,
  "successRate": 0.0-1.0,
  "improvementAreas": ["改进领域1", "改进领域2"],
  "suggestedRules": ["建议规则1", "建议规则2"]
}""",
        ),
        ChatMessage(
            role = "user",
            content = "决策历史：\n$decisionHistory\n\n当前行为规则（AGENTS.md）：\n$currentRules",
        ),
    )

    fun suggestRules(analysis: String, currentRules: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """你是一个 AI 规则优化专家。基于行为分析结果，建议具体的行为规则修改。
返回 JSON 数组，每个元素包含：
- ruleId: 规则标识
- updateType: ADD/MODIFY/DELETE
- currentRule: 当前规则内容（如果是 MODIFY 或 DELETE）
- proposedRule: 建议的新规则内容
- reason: 修改原因
- confidence: 置信度 (0.0-1.0)""",
        ),
        ChatMessage(
            role = "user",
            content = "分析结果：\n$analysis\n\n当前规则：\n$currentRules",
        ),
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add BehaviorEvolver interface and analysis prompts"
```

---

### Task 10: BehaviorEvolverImpl

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/BehaviorEvolverImpl.kt`
- Test: `core/evolution/src/test/java/com/loy/mingclaw/core/evolution/internal/BehaviorEvolverImplTest.kt`

- [ ] **Step 1: Write BehaviorEvolverImpl.kt**

Constructor injects: `@CloudLlm LlmProvider`, `EvolutionFileManager`, `@IODispatcher`.

`recordDecision(decision)`: delegates to `fileManager.writeDecision(decision)`.

`analyzePatterns()`:
1. Call `fileManager.readDecisions(since = Instant.DISTANT_PAST)` — for MVP read all
2. Format decisions as text: `"[{outcome}] {ruleApplied}: {reasoning}"`
3. Call `fileManager.readAgentRules()` to get current rules
4. Call `BehaviorAnalysisPrompt.analyzePatterns(history, rules)` then `llmProvider.chat()`
5. Parse JSON response into `BehaviorAnalysis`
6. Return `Result.success(analysis)`

`suggestRuleUpdates()`:
1. Call `analyzePatterns()` to get current analysis
2. Format analysis as JSON string
3. Call `fileManager.readAgentRules()` for current rules
4. Call `BehaviorAnalysisPrompt.suggestRules(analysisJson, rules)` then `llmProvider.chat()`
5. Parse JSON array response into `List<RuleUpdate>`, assign UUID ids
6. Return `Result.success(updates)`

`applyRuleUpdates(updates)`:
1. Call `fileManager.backupCurrent(version = "v${Clock.System.now().toEpochMilliseconds()}")`
2. Call `fileManager.readAgentRules()` to get current content
3. For each update: apply to the markdown content string (ADD = append section, MODIFY = replace section, DELETE = remove section — match by `ruleId` as a markdown heading or comment marker)
4. Call `fileManager.writeAgentRules(modifiedContent)`
5. Return `Result.success(Unit)`

`rollbackToVersion(version)`:
1. Call `fileManager.restoreVersion(version)`
2. Return `Result.success(Unit)`

- [ ] **Step 2: Write BehaviorEvolverImplTest.kt**

Mock `LlmProvider` and `EvolutionFileManager`.

Tests:
- `recordDecision_delegatesToFileManager`: verify writeDecision called
- `analyzePatterns_callsLlmAndParsesResponse`: mock readDecisions returning list, mock readAgentRules, mock LLM returning JSON analysis, verify parsed BehaviorAnalysis
- `analyzePatterns_handlesLlmFailure`: LLM returns failure → Result.failure
- `suggestRuleUpdates_callsLlmAndParsesResponse`: mock analyzePatterns + LLM returning JSON array, verify RuleUpdate list
- `applyRuleUpdates_backsUpAndModifiesRules`: verify backupCurrent called, verify writeAgentRules called with modified content
- `rollbackToVersion_restoresFromFileManager`: verify restoreVersion called

- [ ] **Step 3: Run tests**

Run: `./gradlew :core:evolution:test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add BehaviorEvolver implementation with LLM analysis and rule rollback"
```

---

## Phase E4: EvolutionTriggerManager + StateMachine + EvolutionEngine

### Task 11: EvolutionStateMachine

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/EvolutionStateMachine.kt`
- Test: `core/evolution/src/test/java/com/loy/mingclaw/core/evolution/internal/EvolutionStateMachineTest.kt`

- [ ] **Step 1: Write EvolutionStateMachine.kt**

```kotlin
package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.evolution.model.EvolutionState
import com.loy.mingclaw.core.evolution.model.EvolutionTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EvolutionStateMachine @Inject constructor() {

    private val state = MutableStateFlow<EvolutionState>(EvolutionState.Idle)

    fun currentState(): EvolutionState = state.value
    fun observeState(): Flow<EvolutionState> = state

    fun transitionTo(newState: EvolutionState): Result<EvolutionState> {
        val current = state.value
        val allowed = when (current) {
            is EvolutionState.Idle -> newState is EvolutionState.Analyzing
            is EvolutionState.Analyzing -> newState is EvolutionState.AwaitingApproval || newState is EvolutionState.Failed
            is EvolutionState.AwaitingApproval -> newState is EvolutionState.Applying || newState is EvolutionState.Idle
            is EvolutionState.Applying -> newState is EvolutionState.Completed || newState is EvolutionState.Failed
            is EvolutionState.Completed -> newState is EvolutionState.Idle || newState is EvolutionState.Analyzing
            is EvolutionState.Failed -> newState is EvolutionState.Idle || newState is EvolutionState.Analyzing
        }
        return if (allowed) {
            state.value = newState
            Result.success(newState)
        } else {
            Result.failure(IllegalStateException("Invalid transition: $current -> $newState"))
        }
    }
}
```

- [ ] **Step 2: Write EvolutionStateMachineTest.kt**

Tests:
- `idle_toAnalyzing_succeeds`
- `idle_toCompleted_fails`
- `analyzing_toAwaitingApproval_succeeds`
- `analyzing_toFailed_succeeds`
- `analyzing_toIdle_fails`
- `awaitingApproval_toApplying_succeeds`
- `awaitingApproval_toIdle_succeeds` (rejection)
- `awaitingApproval_toAnalyzing_fails`
- `applying_toCompleted_succeeds`
- `applying_toFailed_succeeds`
- `completed_toIdle_succeeds`
- `completed_toAnalyzing_succeeds` (re-trigger)
- `failed_toIdle_succeeds`
- `observeState_emitsTransitions`: collect flow, transition Idle→Analyzing→AwaitingApproval, verify emissions

- [ ] **Step 3: Run tests**

Run: `./gradlew :core:evolution:test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add EvolutionStateMachine with state transition validation"
```

---

### Task 12: EvolutionTriggerManager interface + implementation

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/EvolutionTriggerManager.kt`
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/EvolutionTriggerManagerImpl.kt`
- Test: `core/evolution/src/test/java/com/loy/mingclaw/core/evolution/internal/EvolutionTriggerManagerImplTest.kt`

- [ ] **Step 1: Write EvolutionTriggerManager.kt interface**

```kotlin
package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.EvolutionContext
import com.loy.mingclaw.core.evolution.model.EvolutionProposal
import com.loy.mingclaw.core.evolution.model.EvolutionTrigger

interface EvolutionTriggerManager {
    suspend fun shouldTrigger(trigger: EvolutionTrigger, context: EvolutionContext): Boolean
    suspend fun performAnalysis(): Result<List<EvolutionProposal>>
}
```

- [ ] **Step 2: Write EvolutionTriggerManagerImpl.kt**

Constructor injects: `FeedbackCollector`, `BehaviorEvolver`, `KnowledgeEvolver`, `CapabilityEvolver`, `@IODispatcher`.

`shouldTrigger(trigger, context)`:
- `USER_FEEDBACK` → true if `context.feedbackScore < 0.3f`
- `TASK_FAILURE` → true if `context.taskSuccessRate < 0.5f`
- `KNOWLEDGE_THRESHOLD` → true if `context.memoryCount > 50` and `context.lastEvolution` was more than 1 hour ago
- `CAPABILITY_GAP` → always false in MVP (no skill marketplace)
- `SCHEDULED` → true (delegate to caller's scheduling logic)
- `MANUAL` → always true
- `PERFORMANCE_DEGRADATION` → true if `context.feedbackScore < 0.4f`

`performAnalysis()`:
1. Call `behaviorEvolver.analyzePatterns()` — if success and has improvement areas, create BEHAVIOR proposals
2. Call `feedbackCollector.getFeedbackSummary(since = recent)` — if negative trends, add more BEHAVIOR proposals
3. Return consolidated list of `EvolutionProposal`

- [ ] **Step 3: Write EvolutionTriggerManagerImplTest.kt**

Mock all evolvers and FeedbackCollector.

Tests:
- `shouldTrigger_userFeedback_lowScore_returnsTrue`
- `shouldTrigger_userFeedback_highScore_returnsFalse`
- `shouldTrigger_manual_alwaysTrue`
- `shouldTrigger_capabilityGap_alwaysFalse` (MVP)
- `shouldTrigger_knowledgeThreshold_aboveThreshold_returnsTrue`
- `shouldTrigger_knowledgeThreshold_belowThreshold_returnsFalse`
- `performAnalysis_returnsProposalsFromAllEvolver`
- `performAnalysis_handlesEvolverFailure`: one evolver fails → proposals from others still returned

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:evolution:test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add EvolutionTriggerManager with rule-based trigger logic"
```

---

### Task 13: EvolutionEngine interface + implementation

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/EvolutionEngine.kt`
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/EvolutionEngineImpl.kt`
- Test: `core/evolution/src/test/java/com/loy/mingclaw/core/evolution/internal/EvolutionEngineImplTest.kt`

- [ ] **Step 1: Write EvolutionEngine.kt interface**

```kotlin
package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.EvolutionContext
import com.loy.mingclaw.core.evolution.model.EvolutionProposal
import com.loy.mingclaw.core.evolution.model.EvolutionResult
import com.loy.mingclaw.core.evolution.model.EvolutionState
import com.loy.mingclaw.core.evolution.model.EvolutionTrigger
import kotlinx.coroutines.flow.Flow

interface EvolutionEngine {
    fun observeState(): Flow<EvolutionState>
    suspend fun triggerEvolution(trigger: EvolutionTrigger, context: EvolutionContext): Result<List<EvolutionResult>>
    suspend fun approveAndApply(proposals: List<EvolutionProposal>): Result<List<EvolutionResult>>
    suspend fun rejectProposals()
}
```

- [ ] **Step 2: Write EvolutionEngineImpl.kt**

Constructor injects: `EvolutionStateMachine`, `EvolutionTriggerManager`, `BehaviorEvolver`, `KnowledgeEvolver`, `CapabilityEvolver`, `EventBus` (from core:kernel), `@IODispatcher`.

`observeState()`: delegates to `stateMachine.observeState()`.

`triggerEvolution(trigger, context)`:
1. `stateMachine.transitionTo(EvolutionState.Analyzing(trigger))` — if fails, return failure
2. `triggerManager.shouldTrigger(trigger, context)` — if false, `stateMachine.transitionTo(Idle)`, return empty list
3. `triggerManager.performAnalysis()` — get proposals
4. `stateMachine.transitionTo(AwaitingApproval(proposals))`
5. For each proposal by type:
   - BEHAVIOR: call `behaviorEvolver.suggestRuleUpdates()` then `behaviorEvolver.applyRuleUpdates(updates)`
   - KNOWLEDGE: call `knowledgeEvolver.extractKnowledge(context.sessionId)` then `knowledgeEvolver.consolidateToMemory(knowledge)`
   - CAPABILITY: call `capabilityEvolver.identifyCapabilityGaps()`
6. Build `List<EvolutionResult>` from outcomes
7. `stateMachine.transitionTo(Completed(results))`
8. `eventBus.publishAsync(Event.EvolutionCompleted(...))`
9. Return `Result.success(results)`

If any step fails: `stateMachine.transitionTo(Failed(error))`, `eventBus.publishAsync(Event.EvolutionFailed(...))`.

`approveAndApply(proposals)`: same steps 5-9 above (called when proposals are pre-generated).

`rejectProposals()`: `stateMachine.transitionTo(Idle)`.

- [ ] **Step 3: Write EvolutionEngineImplTest.kt**

Mock StateMachine, TriggerManager, all three Evolvers, EventBus.

Tests:
- `triggerEvolution_fullFlow_success`: mock shouldTrigger=true, mock performAnalysis returning proposals, mock evolver methods succeeding, verify state transitions: Idle→Analyzing→AwaitingApproval→Completed
- `triggerEvolution_triggerNotActivated`: mock shouldTrigger=false, verify state returns to Idle, empty results
- `triggerEvolution_handlesAnalyzerFailure`: performAnalysis fails → verify state goes to Failed
- `approveAndApply_behaviorProposal_success`: verify behaviorEvolver.suggestRuleUpdates + applyRuleUpdates called
- `approveAndApply_knowledgeProposal_success`: verify knowledgeEvolver.extractKnowledge + consolidateToMemory called
- `rejectProposals_transitionsToIdle`: verify stateMachine.transitionTo(Idle) called
- `observeState_delegatesToStateMachine`: verify returns stateMachine.observeState()

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:evolution:test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add EvolutionEngine with full orchestration flow"
```

---

## Phase E5: CapabilityEvolver + DI

### Task 14: CapabilityEvolver interface + MVP stub + CapabilityGapPrompt

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/CapabilityEvolver.kt`
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/CapabilityEvolverImpl.kt`
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/internal/prompts/CapabilityGapPrompt.kt`
- Test: `core/evolution/src/test/java/com/loy/mingclaw/core/evolution/internal/CapabilityEvolverImplTest.kt`

- [ ] **Step 1: Write CapabilityEvolver.kt interface**

```kotlin
package com.loy.mingclaw.core.evolution

import com.loy.mingclaw.core.evolution.model.CapabilityGap

interface CapabilityEvolver {
    suspend fun identifyCapabilityGaps(): Result<List<CapabilityGap>>
}
```

- [ ] **Step 2: Write CapabilityGapPrompt.kt**

```kotlin
package com.loy.mingclaw.core.evolution.internal.prompts

import com.loy.mingclaw.core.model.llm.ChatMessage

internal object CapabilityGapPrompt {

    fun build(failedTaskDescriptions: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = """你是一个能力分析专家。分析失败的任务，识别 Agent 缺失的能力。
返回 JSON 数组，每个元素包含：
- capability: 缺失的能力名称
- currentLevel: NONE/BASIC/INTERMEDIATE/ADVANCED/EXPERT
- desiredLevel: NONE/BASIC/INTERMEDIATE/ADVANCED/EXPERT
- priority: LOW/MEDIUM/HIGH/CRITICAL
如果没有能力缺口，返回空数组 []。""",
        ),
        ChatMessage(
            role = "user",
            content = "以下是最近失败的任务：\n$failedTaskDescriptions",
        ),
    )
}
```

- [ ] **Step 3: Write CapabilityEvolverImpl.kt**

Constructor injects: `@CloudLlm LlmProvider`, `@IODispatcher`.

`identifyCapabilityGaps()`:
1. For MVP, return an empty list with `Result.success(emptyList())` — real implementation requires task history integration
2. The infrastructure (prompt + LLM call) is in place for future enhancement

- [ ] **Step 4: Write CapabilityEvolverImplTest.kt**

Test:
- `identifyCapabilityGaps_returnsEmptyList`: MVP stub returns empty list

- [ ] **Step 5: Run tests**

Run: `./gradlew :core:evolution:test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add CapabilityEvolver MVP stub with gap detection prompt"
```

---

### Task 15: Hilt DI Module

**Files:**
- Create: `core/evolution/src/main/java/com/loy/mingclaw/core/evolution/di/EvolutionModule.kt`

- [ ] **Step 1: Write EvolutionModule.kt**

```kotlin
package com.loy.mingclaw.core.evolution.di

import com.loy.mingclaw.core.evolution.BehaviorEvolver
import com.loy.mingclaw.core.evolution.CapabilityEvolver
import com.loy.mingclaw.core.evolution.EvolutionEngine
import com.loy.mingclaw.core.evolution.EvolutionTriggerManager
import com.loy.mingclaw.core.evolution.FeedbackCollector
import com.loy.mingclaw.core.evolution.KnowledgeEvolver
import com.loy.mingclaw.core.evolution.internal.BehaviorEvolverImpl
import com.loy.mingclaw.core.evolution.internal.CapabilityEvolverImpl
import com.loy.mingclaw.core.evolution.internal.EvolutionEngineImpl
import com.loy.mingclaw.core.evolution.internal.EvolutionTriggerManagerImpl
import com.loy.mingclaw.core.evolution.internal.FeedbackCollectorImpl
import com.loy.mingclaw.core.evolution.internal.KnowledgeEvolverImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class EvolutionModule {

    @Binds
    @Singleton
    abstract fun bindFeedbackCollector(impl: FeedbackCollectorImpl): FeedbackCollector

    @Binds
    @Singleton
    abstract fun bindKnowledgeEvolver(impl: KnowledgeEvolverImpl): KnowledgeEvolver

    @Binds
    @Singleton
    abstract fun bindBehaviorEvolver(impl: BehaviorEvolverImpl): BehaviorEvolver

    @Binds
    @Singleton
    abstract fun bindCapabilityEvolver(impl: CapabilityEvolverImpl): CapabilityEvolver

    @Binds
    @Singleton
    abstract fun bindEvolutionTriggerManager(impl: EvolutionTriggerManagerImpl): EvolutionTriggerManager

    @Binds
    @Singleton
    abstract fun bindEvolutionEngine(impl: EvolutionEngineImpl): EvolutionEngine
}
```

- [ ] **Step 2: Commit**

```bash
git add core/evolution/src/
git commit -m "feat: add EvolutionModule Hilt DI bindings"
```

---

### Task 16: Full build verification

- [ ] **Step 1: Run full assemble**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 3: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: resolve build issues from evolution module integration"
```

---

## Self-Review

**Spec coverage check:**
- EvolutionState + EvolutionTypes → Task 2 ✓
- FeedbackTypes → Task 3 ✓
- KnowledgeTypes → Task 3 ✓
- BehaviorTypes → Task 3 ✓
- CapabilityTypes → Task 3 ✓
- Event evolution subtypes → Task 4 ✓
- EvolutionFileManager → Task 5 ✓
- FeedbackCollector → Task 6 ✓
- KnowledgeEvolver → Tasks 7-8 ✓
- BehaviorEvolver → Tasks 9-10 ✓
- EvolutionStateMachine → Task 11 ✓
- EvolutionTriggerManager → Task 12 ✓
- EvolutionEngine → Task 13 ✓
- CapabilityEvolver → Task 14 ✓
- EvolutionModule DI → Task 15 ✓
- Full build verification → Task 16 ✓

**Placeholder scan:** No TBDs, TODOs, or vague steps found. All tasks have complete code or detailed implementation descriptions.

**Type consistency:** All type names, method signatures, and property names match across tasks. `EvolutionTrigger` enum values match between `EvolutionTypes.kt` (Task 2) and `EvolutionStateMachine.kt` (Task 11). `UserFeedback.Explicit`/`Implicit` referenced consistently across Tasks 3, 5, 6, 8.
