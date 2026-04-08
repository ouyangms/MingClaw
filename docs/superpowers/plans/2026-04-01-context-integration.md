# Context 集成层 (core:context) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `core:context` from an isolated in-memory implementation into a true "context orchestration layer" that delegates persistence to `core:data`, vector search to `core:memory`, and orchestrates token budget allocation, memory injection, and LLM-powered context compression.

**Architecture:** 5-component design: `SessionContextManager` (rewrites to delegate `SessionRepository`), `MemoryContextManager` (new, uses `EmbeddingService` + `MemoryRepository`), `ContextWindowManager` (improved `shouldCompress` signature), `ContextCompressionManager` (new, LLM summary), and `ContextOrchestrator` (new, top-level 6-step pipeline). The `ContextOrchestrator` -> `OfflineFirstChatRepository` wiring is deferred to the app/feature layer due to a circular dependency between `core:context` and `core:data`. A comment is added to `OfflineFirstChatRepository` documenting the intended integration path.

**Tech Stack:** Kotlin, Hilt/Dagger, kotlinx.coroutines, MockK + Turbine for testing

---

## File Structure

### New files (core:context)
| File | Responsibility |
|------|---------------|
| `core/context/src/main/java/com/loy/mingclaw/core/context/model/ConversationContext.kt` | Complete LLM request context |
| `core/context/src/main/java/com/loy/mingclaw/core/context/model/CompressedContext.kt` | Compression result |
| `core/context/src/main/java/com/loy/mingclaw/core/context/model/TokenUsage.kt` | Token usage stats |
| `core/context/src/main/java/com/loy/mingclaw/core/context/model/ContextStats.kt` | Context stats for observation |
| `core/context/src/main/java/com/loy/mingclaw/core/context/MemoryContextManager.kt` | Public interface |
| `core/context/src/main/java/com/loy/mingclaw/core/context/ContextCompressionManager.kt` | Public interface |
| `core/context/src/main/java/com/loy/mingclaw/core/context/ContextOrchestrator.kt` | Public interface |
| `core/context/src/main/java/com/loy/mingclaw/core/context/internal/MemoryContextManagerImpl.kt` | Implementation |
| `core/context/src/main/java/com/loy/mingclaw/core/context/internal/ContextCompressionManagerImpl.kt` | Implementation |
| `core/context/src/main/java/com/loy/mingclaw/core/context/internal/ContextOrchestratorImpl.kt` | Implementation |
| `core/context/src/main/java/com/loy/mingclaw/core/context/internal/prompts/CompressionPrompt.kt` | LLM prompt |
| `core/context/src/test/.../internal/MemoryContextManagerImplTest.kt` | Tests |
| `core/context/src/test/.../internal/ContextCompressionManagerImplTest.kt` | Tests |
| `core/context/src/test/.../internal/ContextOrchestratorImplTest.kt` | Tests |

### Modified files
| File | Change |
|------|--------|
| `core/context/build.gradle.kts` | Add `:core:data`, `:core:memory` dependencies + turbine |
| `core/context/.../SessionContextManager.kt` | Rewrite interface (remove old methods, add new) |
| `core/context/.../internal/SessionContextManagerImpl.kt` | Rewrite to delegate `SessionRepository` |
| `core/context/.../ContextWindowManager.kt` | Change `shouldCompress` signature |
| `core/context/.../internal/ContextWindowManagerImpl.kt` | Update `shouldCompress` implementation |
| `core/context/.../di/ContextModule.kt` | Add 3 new `@Binds` |
| `core/context/.../SessionContextManagerImplTest.kt` | Rewrite for delegation tests |
| `core/context/.../ContextWindowManagerImplTest.kt` | Update `shouldCompress` tests |
| `core/data/build.gradle.kts` | Add `:core:context` dependency |
| `core/data/.../internal/OfflineFirstChatRepository.kt` | Inject `ContextOrchestrator`, use `buildContext()` |
| `core/data/.../OfflineFirstChatRepositoryTest.kt` | Update for `ContextOrchestrator` |

---

## Task 1: Domain Types + build.gradle.kts Dependencies

**Files:**
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/model/ConversationContext.kt`
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/model/CompressedContext.kt`
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/model/TokenUsage.kt`
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/model/ContextStats.kt`
- Modify: `core/context/build.gradle.kts`

- [ ] **Step 1: Create ConversationContext.kt**

```kotlin
package com.loy.mingclaw.core.context.model

import com.loy.mingclaw.core.model.llm.ChatMessage
import com.loy.mingclaw.core.model.memory.Memory

data class ConversationContext(
    val systemPrompt: String,
    val messages: List<ChatMessage>,
    val tokenUsage: TokenUsage,
    val memories: List<Memory>,
)
```

- [ ] **Step 2: Create CompressedContext.kt**

```kotlin
package com.loy.mingclaw.core.context.model

import com.loy.mingclaw.core.model.context.Message

data class CompressedContext(
    val summary: String,
    val summaryTokenCount: Int,
    val retainedMessages: List<Message>,
)
```

- [ ] **Step 3: Create TokenUsage.kt**

```kotlin
package com.loy.mingclaw.core.context.model

import com.loy.mingclaw.core.model.TokenBudget

data class TokenUsage(
    val systemTokens: Int,
    val memoryTokens: Int,
    val conversationTokens: Int,
    val totalTokens: Int,
    val budget: TokenBudget,
)
```

- [ ] **Step 4: Create ContextStats.kt**

```kotlin
package com.loy.mingclaw.core.context.model

data class ContextStats(
    val sessionId: String,
    val totalTokensUsed: Int,
    val compressionCount: Int,
    val memoriesInjected: Int,
    val budgetUtilization: Float,
)
```

- [ ] **Step 5: Update core/context/build.gradle.kts to add dependencies**

The full new `dependencies` block:

```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:kernel"))
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

Changes: added `implementation(project(":core:data"))`, `implementation(project(":core:memory"))`, and `testImplementation(libs.turbine)`.

- [ ] **Step 6: Run build to verify new files compile**

Run: `./gradlew :core:context:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add core/context/src/main/java/com/loy/mingclaw/core/context/model/ core/context/build.gradle.kts
git commit -m "feat(context): add domain types and update build dependencies for context integration"
```

---

## Task 2: Rewrite SessionContextManager Interface

**Files:**
- Modify: `core/context/src/main/java/com/loy/mingclaw/core/context/SessionContextManager.kt`

The existing interface has methods that don't match the spec: `getSessionContext`, `archiveSession`, `getAllSessions`, `watchSession`. The new interface delegates these concerns to `SessionRepository` directly and exposes only what the `ContextOrchestrator` needs.

- [ ] **Step 1: Replace the SessionContextManager interface**

Replace the entire file content with:

```kotlin
package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionEvent
import kotlinx.coroutines.flow.Flow

interface SessionContextManager {
    suspend fun createSession(title: String? = null): Result<Session>
    suspend fun getSession(sessionId: String): Result<Session>
    suspend fun addMessage(sessionId: String, message: Message): Result<Message>
    suspend fun getConversationHistory(sessionId: String, limit: Int? = null): Result<List<Message>>
    suspend fun deleteSession(sessionId: String): Result<Unit>
    fun observeSessionEvents(sessionId: String): Flow<SessionEvent>
}
```

Key changes: removed `metadata` parameter from `createSession`, removed `getSessionContext`, `archiveSession`, `getAllSessions`, `watchSession`. Renamed `watchSession` to `observeSessionEvents`.

- [ ] **Step 2: Verify it compiles (impl will be broken — that's expected, fix in Task 3)**

Run: `./gradlew :core:context:compileDebugKotlin 2>&1 | tail -5`
Expected: Compilation errors in `SessionContextManagerImpl` (expected — fixing in Task 3)

- [ ] **Step 3: Commit**

```bash
git add core/context/src/main/java/com/loy/mingclaw/core/context/SessionContextManager.kt
git commit -m "refactor(context): rewrite SessionContextManager interface to delegate to SessionRepository"
```

---

## Task 3: Rewrite SessionContextManagerImpl

**Files:**
- Modify: `core/context/src/main/java/com/loy/mingclaw/core/context/internal/SessionContextManagerImpl.kt`
- Modify: `core/context/src/test/java/com/loy/mingclaw/core/context/SessionContextManagerImplTest.kt`

The implementation currently uses `ConcurrentHashMap` for in-memory storage. Rewrite to delegate to `SessionRepository` from `core:data`.

- [ ] **Step 1: Rewrite SessionContextManagerImpl**

Replace the entire file content with:

```kotlin
package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.context.SessionContextManager
import com.loy.mingclaw.core.data.repository.SessionRepository
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SessionContextManagerImpl @Inject constructor(
    private val sessionRepository: SessionRepository,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : SessionContextManager {

    private val sessionEvents = MutableSharedFlow<SessionEvent>(replay = 10)

    override suspend fun createSession(title: String?): Result<Session> =
        withContext(ioDispatcher) {
            try {
                val session = sessionRepository.createSession(
                    title = title ?: "Session ${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
                )
                sessionEvents.emit(SessionEvent.Created(session))
                Result.success(session)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getSession(sessionId: String): Result<Session> =
        withContext(ioDispatcher) {
            try {
                val session = sessionRepository.getSession(sessionId)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Session not found: $sessionId")
                    )
                Result.success(session)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun addMessage(sessionId: String, message: Message): Result<Message> =
        withContext(ioDispatcher) {
            try {
                val saved = sessionRepository.addMessage(sessionId, message)
                sessionEvents.emit(SessionEvent.MessageAdded(saved))
                Result.success(saved)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getConversationHistory(sessionId: String, limit: Int?): Result<List<Message>> =
        withContext(ioDispatcher) {
            try {
                val messages = if (limit != null) {
                    sessionRepository.getMessages(sessionId, limit)
                } else {
                    sessionRepository.getMessages(sessionId)
                }
                Result.success(messages)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteSession(sessionId: String): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                sessionRepository.deleteSession(sessionId)
                sessionEvents.emit(SessionEvent.Deleted(sessionId))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // MVP: 后续增强 - simple mapping from observeMessages, no complex event aggregation
    override fun observeSessionEvents(sessionId: String): Flow<SessionEvent> =
        sessionRepository.observeMessages(sessionId).map { messages ->
            SessionEvent.MessageAdded(messages.last())
        }
}
```

- [ ] **Step 2: Rewrite SessionContextManagerImplTest**

Replace the entire test file. Now tests use MockK to mock `SessionRepository`:

```kotlin
package com.loy.mingclaw.core.context

import app.cash.turbine.test
import com.loy.mingclaw.core.context.internal.SessionContextManagerImpl
import com.loy.mingclaw.core.data.repository.SessionRepository
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionContextManagerImplTest {
    private val sessionRepository = mockk<SessionRepository>()
    private lateinit var manager: SessionContextManagerImpl

    @Before
    fun setup() {
        manager = SessionContextManagerImpl(
            sessionRepository = sessionRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `createSession delegates to repository`() = runTest {
        val now = Clock.System.now()
        val session = Session(id = "s1", title = "My Chat", createdAt = now, updatedAt = now)
        coEvery { sessionRepository.createSession("My Chat") } returns session

        val result = manager.createSession(title = "My Chat")
        assertTrue(result.isSuccess)
        assertEquals("s1", result.getOrThrow().id)
        coVerify { sessionRepository.createSession("My Chat") }
    }

    @Test
    fun `createSession with null title generates default`() = runTest {
        coEvery { sessionRepository.createSession(any()) } answers {
            Session(id = "s1", title = firstArg(), createdAt = Clock.System.now(), updatedAt = Clock.System.now())
        }

        val result = manager.createSession()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().title.startsWith("Session "))
    }

    @Test
    fun `getSession returns session from repository`() = runTest {
        val now = Clock.System.now()
        val session = Session(id = "s1", title = "Test", createdAt = now, updatedAt = now)
        coEvery { sessionRepository.getSession("s1") } returns session

        val result = manager.getSession("s1")
        assertTrue(result.isSuccess)
        assertEquals("Test", result.getOrThrow().title)
    }

    @Test
    fun `getSession returns failure for missing session`() = runTest {
        coEvery { sessionRepository.getSession("missing") } returns null

        val result = manager.getSession("missing")
        assertTrue(result.isFailure)
    }

    @Test
    fun `addMessage delegates to repository`() = runTest {
        val now = Clock.System.now()
        val message = Message(id = "m1", sessionId = "s1", role = MessageRole.User, content = "Hello", timestamp = now)
        coEvery { sessionRepository.addMessage("s1", any()) } returns message

        val result = manager.addMessage("s1", message)
        assertTrue(result.isSuccess)
        assertEquals("m1", result.getOrThrow().id)
        coVerify { sessionRepository.addMessage("s1", message) }
    }

    @Test
    fun `getConversationHistory returns messages from repository`() = runTest {
        val now = Clock.System.now()
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "Hi", timestamp = now),
            Message(id = "2", sessionId = "s1", role = MessageRole.Assistant, content = "Hello", timestamp = now),
        )
        coEvery { sessionRepository.getMessages("s1") } returns messages

        val result = manager.getConversationHistory("s1")
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `getConversationHistory with limit delegates with limit`() = runTest {
        coEvery { sessionRepository.getMessages("s1", 3) } returns emptyList()

        manager.getConversationHistory("s1", limit = 3)
        coVerify { sessionRepository.getMessages("s1", 3) }
    }

    @Test
    fun `deleteSession delegates to repository`() = runTest {
        coEvery { sessionRepository.deleteSession("s1") } returns Unit

        val result = manager.deleteSession("s1")
        assertTrue(result.isSuccess)
        coVerify { sessionRepository.deleteSession("s1") }
    }

    @Test
    fun `observeSessionEvents maps from observeMessages`() = runTest {
        val now = Clock.System.now()
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "Hi", timestamp = now),
        )
        every { sessionRepository.observeMessages("s1") } returns flowOf(messages)

        manager.observeSessionEvents("s1").test {
            val event = awaitItem()
            assertTrue(event is SessionEvent.MessageAdded)
            assertEquals("1", (event as SessionEvent.MessageAdded).message.id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 3: Run tests to verify**

Run: `./gradlew :core:context:test --tests "com.loy.mingclaw.core.context.SessionContextManagerImplTest"`
Expected: All tests PASS

- [ ] **Step 4: Commit**

```bash
git add core/context/src/main/java/com/loy/mingclaw/core/context/internal/SessionContextManagerImpl.kt core/context/src/test/java/com/loy/mingclaw/core/context/SessionContextManagerImplTest.kt
git commit -m "refactor(context): rewrite SessionContextManagerImpl to delegate to SessionRepository"
```

---

## Task 4: Update ContextWindowManager Interface + Implementation

**Files:**
- Modify: `core/context/src/main/java/com/loy/mingclaw/core/context/ContextWindowManager.kt`
- Modify: `core/context/src/main/java/com/loy/mingclaw/core/context/internal/ContextWindowManagerImpl.kt`
- Modify: `core/context/src/test/java/com/loy/mingclaw/core/context/ContextWindowManagerImplTest.kt`

The spec changes `shouldCompress` from taking `SessionContext` to taking `List<Message>` and `TokenBudget`.

- [ ] **Step 1: Update ContextWindowManager interface**

Replace the entire file:

```kotlin
package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.TokenBudget
import com.loy.mingclaw.core.model.context.ContextComponent
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.WindowStatistics

interface ContextWindowManager {
    fun calculateTokenBudget(): TokenBudget
    fun allocateTokenBudget(budget: TokenBudget, components: List<ContextComponent>): Map<String, Int>
    fun estimateTokens(content: String): Int
    fun shouldCompress(messages: List<Message>, budget: TokenBudget): Boolean
    fun getWindowStatistics(): WindowStatistics
}
```

- [ ] **Step 2: Update ContextWindowManagerImpl**

Replace the entire file:

```kotlin
package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.context.ContextWindowManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.model.TokenBudget
import com.loy.mingclaw.core.model.context.ContextComponent
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.WindowStatistics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ContextWindowManagerImpl @Inject constructor(
    private val tokenEstimator: TokenEstimator,
    private val configManager: ConfigManager,
) : ContextWindowManager {

    override fun calculateTokenBudget(): TokenBudget {
        val config = configManager.getConfig()
        return TokenBudget.calculate(maxTokens = config.maxTokens)
    }

    override fun allocateTokenBudget(budget: TokenBudget, components: List<ContextComponent>): Map<String, Int> {
        val allocation = mutableMapOf<String, Int>()
        var remaining = budget.totalTokens - budget.systemTokens
        for (component in components.sortedByDescending { it.priority }) {
            val allocated = minOf(component.requestedTokens, remaining, component.maxTokens)
            allocation[component.id] = allocated
            remaining -= allocated
            if (remaining <= 0) break
        }
        return allocation
    }

    override fun estimateTokens(content: String): Int = tokenEstimator.estimate(content)

    override fun shouldCompress(messages: List<Message>, budget: TokenBudget): Boolean {
        val threshold = (budget.conversationTokens * 0.8).toInt()
        val contextTokens = tokenEstimator.estimateMessages(messages)
        return contextTokens > threshold
    }

    // MVP: 后续增强 - returns default values, no historical tracking
    override fun getWindowStatistics(): WindowStatistics = WindowStatistics()
}
```

Key change: `shouldCompress` now takes `List<Message>` + `TokenBudget` instead of `SessionContext`. The threshold is now based on `budget.conversationTokens` (80%) instead of `config.maxTokens`.

- [ ] **Step 3: Update ContextWindowManagerImplTest**

Replace the entire test file:

```kotlin
package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.context.internal.ContextWindowManagerImpl
import com.loy.mingclaw.core.context.internal.TokenEstimatorImpl
import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.model.KernelConfig
import com.loy.mingclaw.core.model.TokenBudget
import com.loy.mingclaw.core.model.context.ContextComponent
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextWindowManagerImplTest {
    private lateinit var windowManager: ContextWindowManagerImpl

    @Before
    fun setup() {
        val configManager = object : ConfigManager {
            override fun getConfig() = KernelConfig()
            override fun updateConfig(updates: Map<String, Any>) = Result.success(KernelConfig())
            override fun resetToDefault() = KernelConfig()
            override fun watchConfigChanges() = flowOf(KernelConfig())
        }
        windowManager = ContextWindowManagerImpl(TokenEstimatorImpl(), configManager)
    }

    @Test
    fun `calculateTokenBudget returns valid budget`() {
        val budget = windowManager.calculateTokenBudget()
        assertEquals(8192, budget.totalTokens)
        assertEquals(1000, budget.systemTokens)
        assertTrue(budget.memoryTokens > 0)
        assertTrue(budget.conversationTokens > 0)
    }

    @Test
    fun `allocateTokenBudget distributes by priority`() {
        val budget = TokenBudget.calculate(10000)
        val components = listOf(
            ContextComponent(id = "system", name = "System", requestedTokens = 1000, priority = 3),
            ContextComponent(id = "memory", name = "Memory", requestedTokens = 2000, priority = 2),
            ContextComponent(id = "chat", name = "Chat", requestedTokens = 5000, priority = 1),
        )
        val allocation = windowManager.allocateTokenBudget(budget, components)
        assertEquals(3, allocation.size)
        assertTrue(allocation["system"]!! > 0)
    }

    @Test
    fun `estimateTokens delegates to estimator`() {
        assertTrue(windowManager.estimateTokens("Hello, world!") > 0)
    }

    @Test
    fun `shouldCompress returns false for small messages`() {
        val budget = windowManager.calculateTokenBudget()
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "Hi"),
        )
        assertFalse(windowManager.shouldCompress(messages, budget))
    }

    @Test
    fun `shouldCompress returns true for large messages`() {
        val budget = windowManager.calculateTokenBudget()
        val largeMessages = (1..500).map { i ->
            Message(id = "$i", sessionId = "s1", role = MessageRole.User, content = "This is message number $i with some extra text to make it longer.")
        }
        assertTrue(windowManager.shouldCompress(largeMessages, budget))
    }

    @Test
    fun `getWindowStatistics returns default stats`() {
        val stats = windowManager.getWindowStatistics()
        assertEquals(0.0, stats.averageTokenUsage, 0.01)
        assertEquals(0, stats.peakTokenUsage)
    }
}
```

- [ ] **Step 4: Run tests to verify**

Run: `./gradlew :core:context:test --tests "com.loy.mingclaw.core.context.ContextWindowManagerImplTest"`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/context/src/main/java/com/loy/mingclaw/core/context/ContextWindowManager.kt core/context/src/main/java/com/loy/mingclaw/core/context/internal/ContextWindowManagerImpl.kt core/context/src/test/java/com/loy/mingclaw/core/context/ContextWindowManagerImplTest.kt
git commit -m "refactor(context): update ContextWindowManager shouldCompress to use Message list + TokenBudget"
```

---

## Task 5: MemoryContextManager Interface + Implementation

**Files:**
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/MemoryContextManager.kt`
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/internal/MemoryContextManagerImpl.kt`
- Create: `core/context/src/test/java/com/loy/mingclaw/core/context/internal/MemoryContextManagerImplTest.kt`

- [ ] **Step 1: Create MemoryContextManager interface**

```kotlin
package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.memory.Memory

interface MemoryContextManager {
    suspend fun retrieveRelevantMemories(query: String, maxTokens: Int): Result<List<Memory>>
}
```

- [ ] **Step 2: Create MemoryContextManagerImpl**

```kotlin
package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.context.MemoryContextManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.data.repository.MemoryRepository
import com.loy.mingclaw.core.memory.EmbeddingService
import com.loy.mingclaw.core.model.memory.Memory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MemoryContextManagerImpl @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val embeddingService: EmbeddingService,
    private val tokenEstimator: TokenEstimator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : MemoryContextManager {

    override suspend fun retrieveRelevantMemories(
        query: String,
        maxTokens: Int,
    ): Result<List<Memory>> = withContext(ioDispatcher) {
        try {
            val embedding = embeddingService.generateEmbedding(query)
                .getOrElse { return@withContext Result.failure(it) }

            val candidates = memoryRepository.vectorSearch(
                queryEmbedding = embedding,
                limit = 10,
                threshold = 0.5f,
            ).getOrElse { return@withContext Result.failure(it) }

            // MVP: 后续增强 - no memory expiry, importance decay, or dedup
            val result = mutableListOf<Memory>()
            var usedTokens = 0
            for (memory in candidates) {
                val tokens = tokenEstimator.estimate(memory.content)
                if (usedTokens + tokens > maxTokens) break
                result.add(memory)
                usedTokens += tokens
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

- [ ] **Step 3: Create MemoryContextManagerImplTest**

```kotlin
package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.data.repository.MemoryRepository
import com.loy.mingclaw.core.memory.EmbeddingService
import com.loy.mingclaw.core.model.memory.Memory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryContextManagerImplTest {
    private val memoryRepository = mockk<MemoryRepository>()
    private val embeddingService = mockk<EmbeddingService>()
    private val tokenEstimator = mockk<TokenEstimator>()
    private lateinit var manager: MemoryContextManagerImpl

    @Before
    fun setup() {
        manager = MemoryContextManagerImpl(
            memoryRepository = memoryRepository,
            embeddingService = embeddingService,
            tokenEstimator = tokenEstimator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `retrieveRelevantMemories returns memories within token budget`() = runTest {
        val now = Clock.System.now()
        val embedding = listOf(0.1f, 0.2f, 0.3f)
        val memories = listOf(
            Memory(id = "m1", content = "Memory one", importance = 0.8f, createdAt = now, accessedAt = now),
            Memory(id = "m2", content = "Memory two", importance = 0.7f, createdAt = now, accessedAt = now),
        )

        coEvery { embeddingService.generateEmbedding("test query") } returns Result.success(embedding)
        coEvery { memoryRepository.vectorSearch(embedding, limit = 10, threshold = 0.5f) } returns Result.success(memories)
        every { tokenEstimator.estimate("Memory one") } returns 3
        every { tokenEstimator.estimate("Memory two") } returns 3

        val result = manager.retrieveRelevantMemories("test query", maxTokens = 100)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
    }

    @Test
    fun `retrieveRelevantMemories truncates when exceeding budget`() = runTest {
        val now = Clock.System.now()
        val embedding = listOf(0.1f, 0.2f, 0.3f)
        val memories = listOf(
            Memory(id = "m1", content = "First memory that is somewhat long", importance = 0.8f, createdAt = now, accessedAt = now),
            Memory(id = "m2", content = "Second memory also quite long", importance = 0.7f, createdAt = now, accessedAt = now),
            Memory(id = "m3", content = "Third memory", importance = 0.6f, createdAt = now, accessedAt = now),
        )

        coEvery { embeddingService.generateEmbedding("query") } returns Result.success(embedding)
        coEvery { memoryRepository.vectorSearch(embedding, limit = 10, threshold = 0.5f) } returns Result.success(memories)
        every { tokenEstimator.estimate("First memory that is somewhat long") } returns 20
        every { tokenEstimator.estimate("Second memory also quite long") } returns 20
        every { tokenEstimator.estimate("Third memory") } returns 10

        val result = manager.retrieveRelevantMemories("query", maxTokens = 30)
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size) // 20 + 20 > 30, but 20 <= 30, then 20+20=40 > 30
    }

    @Test
    fun `retrieveRelevantMemories returns empty when embedding fails`() = runTest {
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.failure(RuntimeException("Embedding failed"))

        val result = manager.retrieveRelevantMemories("query", maxTokens = 100)
        assertTrue(result.isFailure)
    }

    @Test
    fun `retrieveRelevantMemories returns empty when vector search fails`() = runTest {
        val embedding = listOf(0.1f, 0.2f, 0.3f)
        coEvery { embeddingService.generateEmbedding("query") } returns Result.success(embedding)
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.failure(RuntimeException("Search failed"))

        val result = manager.retrieveRelevantMemories("query", maxTokens = 100)
        assertTrue(result.isFailure)
    }

    @Test
    fun `retrieveRelevantMemories returns empty list when no matches`() = runTest {
        val embedding = listOf(0.1f, 0.2f, 0.3f)
        coEvery { embeddingService.generateEmbedding("query") } returns Result.success(embedding)
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.success(emptyList())

        val result = manager.retrieveRelevantMemories("query", maxTokens = 100)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }
}
```

- [ ] **Step 4: Run tests to verify**

Run: `./gradlew :core:context:test --tests "com.loy.mingclaw.core.context.internal.MemoryContextManagerImplTest"`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/context/src/main/java/com/loy/mingclaw/core/context/MemoryContextManager.kt core/context/src/main/java/com/loy/mingclaw/core/context/internal/MemoryContextManagerImpl.kt core/context/src/test/java/com/loy/mingclaw/core/context/internal/MemoryContextManagerImplTest.kt
git commit -m "feat(context): add MemoryContextManager for embedding-based memory retrieval"
```

---

## Task 6: ContextCompressionManager Interface + Implementation + CompressionPrompt

**Files:**
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/ContextCompressionManager.kt`
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/internal/ContextCompressionManagerImpl.kt`
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/internal/prompts/CompressionPrompt.kt`
- Create: `core/context/src/test/java/com/loy/mingclaw/core/context/internal/ContextCompressionManagerImplTest.kt`

- [ ] **Step 1: Create ContextCompressionManager interface**

```kotlin
package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.context.model.CompressedContext
import com.loy.mingclaw.core.model.context.Message

interface ContextCompressionManager {
    suspend fun compressHistory(messages: List<Message>, maxTokens: Int): Result<CompressedContext>
}
```

- [ ] **Step 2: Create CompressionPrompt**

```kotlin
package com.loy.mingclaw.core.context.internal.prompts

import com.loy.mingclaw.core.model.llm.ChatMessage

internal object CompressionPrompt {
    fun build(conversationHistory: String): List<ChatMessage> = listOf(
        ChatMessage(
            role = "system",
            content = "你是一个对话摘要专家。请将以下对话历史总结为简洁的摘要，保留关键信息、用户偏好和重要决策。只返回摘要文本，不要其他文字。",
        ),
        ChatMessage(
            role = "user",
            content = "请总结以下对话：\n\n$conversationHistory",
        ),
    )
}
```

- [ ] **Step 3: Create ContextCompressionManagerImpl**

```kotlin
package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.common.llm.CloudLlm
import com.loy.mingclaw.core.context.ContextCompressionManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.context.internal.prompts.CompressionPrompt
import com.loy.mingclaw.core.context.model.CompressedContext
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.llm.LlmProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ContextCompressionManagerImpl @Inject constructor(
    @CloudLlm private val llmProvider: LlmProvider,
    private val tokenEstimator: TokenEstimator,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContextCompressionManager {

    companion object {
        private const val DEFAULT_MODEL = "qwen-plus"
        private const val RETAINED_MESSAGE_COUNT = 6
    }

    // MVP: 后续增强 - single summarization strategy, no sliding window/key info extraction
    override suspend fun compressHistory(
        messages: List<Message>,
        maxTokens: Int,
    ): Result<CompressedContext> = withContext(ioDispatcher) {
        try {
            if (messages.size <= RETAINED_MESSAGE_COUNT) {
                return@withContext Result.success(
                    CompressedContext(
                        summary = "",
                        summaryTokenCount = 0,
                        retainedMessages = messages,
                    )
                )
            }

            val retainedMessages = messages.takeLast(RETAINED_MESSAGE_COUNT)
            val oldMessages = messages.dropLast(RETAINED_MESSAGE_COUNT)

            val conversationHistory = oldMessages.joinToString("\n") { msg ->
                "[${msg.role}] ${msg.content}"
            }

            val promptMessages = CompressionPrompt.build(conversationHistory)
            val llmResult = llmProvider.chat(
                model = DEFAULT_MODEL,
                messages = promptMessages,
                temperature = 0.3,
            )

            val response = llmResult.getOrElse { return@withContext Result.failure(it) }
            val summary = response.content
            val summaryTokenCount = tokenEstimator.estimate(summary)

            Result.success(
                CompressedContext(
                    summary = summary,
                    summaryTokenCount = summaryTokenCount,
                    retainedMessages = retainedMessages,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

- [ ] **Step 4: Create ContextCompressionManagerImplTest**

```kotlin
package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.llm.ChatResponse
import com.loy.mingclaw.core.model.llm.LlmProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextCompressionManagerImplTest {
    private val llmProvider = mockk<LlmProvider>()
    private val tokenEstimator = mockk<TokenEstimator>()
    private lateinit var manager: ContextCompressionManagerImpl

    @Before
    fun setup() {
        manager = ContextCompressionManagerImpl(
            llmProvider = llmProvider,
            tokenEstimator = tokenEstimator,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun makeMessage(id: String, role: MessageRole, content: String): Message =
        Message(id = id, sessionId = "s1", role = role, content = content, timestamp = Clock.System.now())

    @Test
    fun `compressHistory returns messages unchanged when count <= 6`() = runTest {
        val messages = (1..5).map { makeMessage("$it", MessageRole.User, "Msg $it") }

        val result = manager.compressHistory(messages, maxTokens = 4000)
        assertTrue(result.isSuccess)
        val compressed = result.getOrThrow()
        assertEquals("", compressed.summary)
        assertEquals(0, compressed.summaryTokenCount)
        assertEquals(5, compressed.retainedMessages.size)
    }

    @Test
    fun `compressHistory summarizes old messages and retains recent`() = runTest {
        val messages = (1..10).map { makeMessage("$it", MessageRole.User, "Message number $it") }

        coEvery {
            llmProvider.chat(model = any(), messages = any(), temperature = any(), maxTokens = any())
        } returns Result.success(
            ChatResponse(id = "r1", content = "Summary of conversation", model = "qwen-plus")
        )
        every { tokenEstimator.estimate("Summary of conversation") } returns 5

        val result = manager.compressHistory(messages, maxTokens = 4000)
        assertTrue(result.isSuccess)
        val compressed = result.getOrThrow()
        assertEquals("Summary of conversation", compressed.summary)
        assertEquals(5, compressed.summaryTokenCount)
        assertEquals(6, compressed.retainedMessages.size)
        assertEquals("5", compressed.retainedMessages.first().id)
        assertEquals("10", compressed.retainedMessages.last().id)
    }

    @Test
    fun `compressHistory returns failure when LLM fails`() = runTest {
        val messages = (1..10).map { makeMessage("$it", MessageRole.User, "Message $it") }

        coEvery {
            llmProvider.chat(model = any(), messages = any(), temperature = any(), maxTokens = any())
        } returns Result.failure(RuntimeException("LLM error"))

        val result = manager.compressHistory(messages, maxTokens = 4000)
        assertTrue(result.isFailure)
    }

    @Test
    fun `compressHistory with exactly 6 messages returns no compression`() = runTest {
        val messages = (1..6).map { makeMessage("$it", MessageRole.User, "Msg $it") }

        val result = manager.compressHistory(messages, maxTokens = 4000)
        assertTrue(result.isSuccess)
        assertEquals("", result.getOrThrow().summary)
        assertEquals(6, result.getOrThrow().retainedMessages.size)
    }

    @Test
    fun `compressHistory with 7 messages summarizes 1 old message`() = runTest {
        val messages = (1..7).map { makeMessage("$it", MessageRole.User, "Msg $it") }

        coEvery {
            llmProvider.chat(model = any(), messages = any(), temperature = any(), maxTokens = any())
        } returns Result.success(
            ChatResponse(id = "r1", content = "Brief summary", model = "qwen-plus")
        )
        every { tokenEstimator.estimate("Brief summary") } returns 3

        val result = manager.compressHistory(messages, maxTokens = 4000)
        assertTrue(result.isSuccess)
        assertEquals("Brief summary", result.getOrThrow().summary)
        assertEquals(6, result.getOrThrow().retainedMessages.size)
    }
}
```

- [ ] **Step 5: Run tests to verify**

Run: `./gradlew :core:context:test --tests "com.loy.mingclaw.core.context.internal.ContextCompressionManagerImplTest"`
Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add core/context/src/main/java/com/loy/mingclaw/core/context/ContextCompressionManager.kt core/context/src/main/java/com/loy/mingclaw/core/context/internal/ContextCompressionManagerImpl.kt core/context/src/main/java/com/loy/mingclaw/core/context/internal/prompts/ core/context/src/test/java/com/loy/mingclaw/core/context/internal/ContextCompressionManagerImplTest.kt
git commit -m "feat(context): add ContextCompressionManager with LLM summarization"
```

---

## Task 7: ContextOrchestrator Interface + Implementation

**Files:**
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/ContextOrchestrator.kt`
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/internal/ContextOrchestratorImpl.kt`
- Create: `core/context/src/test/java/com/loy/mingclaw/core/context/internal/ContextOrchestratorImplTest.kt`

- [ ] **Step 1: Create ContextOrchestrator interface**

```kotlin
package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.context.model.ConversationContext
import com.loy.mingclaw.core.context.model.ContextStats
import kotlinx.coroutines.flow.Flow

interface ContextOrchestrator {
    suspend fun buildContext(sessionId: String, userMessage: String): Result<ConversationContext>
    fun observeContextStats(): Flow<ContextStats>
}
```

- [ ] **Step 2: Create ContextOrchestratorImpl**

```kotlin
package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.context.ContextCompressionManager
import com.loy.mingclaw.core.context.ContextOrchestrator
import com.loy.mingclaw.core.context.ContextWindowManager
import com.loy.mingclaw.core.context.MemoryContextManager
import com.loy.mingclaw.core.context.SessionContextManager
import com.loy.mingclaw.core.context.model.CompressedContext
import com.loy.mingclaw.core.context.model.ContextStats
import com.loy.mingclaw.core.context.model.ConversationContext
import com.loy.mingclaw.core.context.model.TokenUsage
import com.loy.mingclaw.core.model.llm.ChatMessage
import com.loy.mingclaw.core.model.memory.Memory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ContextOrchestratorImpl @Inject constructor(
    private val sessionContextManager: SessionContextManager,
    private val memoryContextManager: MemoryContextManager,
    private val contextWindowManager: ContextWindowManager,
    private val compressionManager: ContextCompressionManager,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContextOrchestrator {

    private val contextStats = MutableStateFlow(
        ContextStats(sessionId = "", totalTokensUsed = 0, compressionCount = 0, memoriesInjected = 0, budgetUtilization = 0f)
    )

    // MVP: 后续增强 - system prompt hardcoded, future DynamicPromptBuilder from Evolution layer
    override suspend fun buildContext(
        sessionId: String,
        userMessage: String,
    ): Result<ConversationContext> = withContext(ioDispatcher) {
        try {
            // Step 1: Get conversation history
            val historyResult = sessionContextManager.getConversationHistory(sessionId)
            val messages = historyResult.getOrElse { return@withContext Result.failure(it) }

            // Step 2: Calculate token budget
            val budget = contextWindowManager.calculateTokenBudget()

            // Step 3: Retrieve relevant memories
            val memoriesResult = memoryContextManager.retrieveRelevantMemories(userMessage, budget.memoryTokens)
            val memories = memoriesResult.getOrElse { emptyList() }

            // Step 4: Compress if needed
            var conversationMessages = messages
            var compressionCount = 0
            if (contextWindowManager.shouldCompress(messages, budget)) {
                val compressed = compressionManager.compressHistory(messages, budget.conversationTokens)
                compressed.getOrElse {
                    // If compression fails, use original messages (graceful degradation)
                    CompressedContext(summary = "", summaryTokenCount = 0, retainedMessages = messages)
                }.also { ctx ->
                    compressionCount = if (ctx.summary.isNotEmpty()) 1 else 0
                    conversationMessages = ctx.retainedMessages
                }
            }

            // Step 5: Build system prompt with memories
            val memorySection = if (memories.isNotEmpty()) {
                val memoryText = memories.joinToString("\n") { "- ${it.content}" }
                "\n\n## 相关记忆\n$memoryText"
            } else {
                ""
            }
            val systemPrompt = "你是一个智能助手，帮助用户完成各种任务。$memorySection"

            // Step 6: Convert to ChatMessage list and trim to budget
            val chatMessages = mutableListOf<ChatMessage>()
            chatMessages.add(ChatMessage(role = "system", content = systemPrompt))
            for (msg in conversationMessages) {
                chatMessages.add(ChatMessage(role = msg.role.name.lowercase(), content = msg.content))
            }
            chatMessages.add(ChatMessage(role = "user", content = userMessage))

            // Calculate token usage
            val systemTokens = contextWindowManager.estimateTokens(systemPrompt)
            val memoryTokens = memories.sumOf { contextWindowManager.estimateTokens(it.content) }
            val conversationTokens = chatMessages.sumOf { contextWindowManager.estimateTokens(it.content) } - systemTokens
            val totalTokens = systemTokens + memoryTokens + conversationTokens

            val tokenUsage = TokenUsage(
                systemTokens = systemTokens,
                memoryTokens = memoryTokens,
                conversationTokens = conversationTokens,
                totalTokens = totalTokens,
                budget = budget,
            )

            val result = ConversationContext(
                systemPrompt = systemPrompt,
                messages = chatMessages,
                tokenUsage = tokenUsage,
                memories = memories,
            )

            // Update stats
            contextStats.value = ContextStats(
                sessionId = sessionId,
                totalTokensUsed = totalTokens,
                compressionCount = compressionCount,
                memoriesInjected = memories.size,
                budgetUtilization = if (budget.totalTokens > 0) totalTokens.toFloat() / budget.totalTokens else 0f,
            )

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeContextStats(): Flow<ContextStats> = contextStats.asStateFlow()
}
```

- [ ] **Step 3: Create ContextOrchestratorImplTest**

```kotlin
package com.loy.mingclaw.core.context.internal

import app.cash.turbine.test
import com.loy.mingclaw.core.context.ContextCompressionManager
import com.loy.mingclaw.core.context.ContextWindowManager
import com.loy.mingclaw.core.context.MemoryContextManager
import com.loy.mingclaw.core.context.SessionContextManager
import com.loy.mingclaw.core.context.model.CompressedContext
import com.loy.mingclaw.core.context.model.ContextStats
import com.loy.mingclaw.core.model.TokenBudget
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.memory.Memory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContextOrchestratorImplTest {
    private val sessionContextManager = mockk<SessionContextManager>()
    private val memoryContextManager = mockk<MemoryContextManager>()
    private val contextWindowManager = mockk<ContextWindowManager>()
    private val compressionManager = mockk<ContextCompressionManager>()
    private lateinit var orchestrator: ContextOrchestratorImpl

    @Before
    fun setup() {
        orchestrator = ContextOrchestratorImpl(
            sessionContextManager = sessionContextManager,
            memoryContextManager = memoryContextManager,
            contextWindowManager = contextWindowManager,
            compressionManager = compressionManager,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun defaultSetup(
        messages: List<Message> = emptyList(),
        memories: List<Memory> = emptyList(),
        shouldCompress: Boolean = false,
        budget: TokenBudget = TokenBudget.calculate(8192),
    ) {
        coEvery { sessionContextManager.getConversationHistory(any(), any()) } returns Result.success(messages)
        every { contextWindowManager.calculateTokenBudget() } returns budget
        coEvery { memoryContextManager.retrieveRelevantMemories(any(), any()) } returns Result.success(memories)
        every { contextWindowManager.shouldCompress(any(), any()) } returns shouldCompress
        every { contextWindowManager.estimateTokens(any()) } answers {
            (firstArg<String>().length + 3) / 4
        }
    }

    @Test
    fun `buildContext with no history and no memories returns basic context`() = runTest {
        defaultSetup()

        val result = orchestrator.buildContext("s1", "Hello")
        assertTrue(result.isSuccess)
        val context = result.getOrThrow()
        assertEquals("system", context.messages.first().role)
        assertEquals("Hello", context.messages.last().content)
        assertEquals(0, context.memories.size)
    }

    @Test
    fun `buildContext injects memories into system prompt`() = runTest {
        val now = Clock.System.now()
        val memories = listOf(
            Memory(id = "m1", content = "User likes Kotlin", importance = 0.8f, createdAt = now, accessedAt = now),
        )
        defaultSetup(memories = memories)

        val result = orchestrator.buildContext("s1", "What language?")
        assertTrue(result.isSuccess)
        val context = result.getOrThrow()
        assertTrue(context.systemPrompt.contains("Kotlin"))
        assertTrue(context.systemPrompt.contains("相关记忆"))
        assertEquals(1, context.memories.size)
    }

    @Test
    fun `buildContext includes conversation history`() = runTest {
        val now = Clock.System.now()
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "Previous question", timestamp = now),
            Message(id = "2", sessionId = "s1", role = MessageRole.Assistant, content = "Previous answer", timestamp = now),
        )
        defaultSetup(messages = messages)

        val result = orchestrator.buildContext("s1", "New question")
        assertTrue(result.isSuccess)
        val context = result.getOrThrow()
        // system + 2 history + 1 new user = 4
        assertEquals(4, context.messages.size)
    }

    @Test
    fun `buildContext triggers compression when needed`() = runTest {
        val messages = (1..20).map { i ->
            Message(id = "$i", sessionId = "s1", role = MessageRole.User, content = "Message $i with padding text", timestamp = Clock.System.now())
        }
        val compressed = CompressedContext(
            summary = "Summary of old messages",
            summaryTokenCount = 5,
            retainedMessages = messages.takeLast(6),
        )

        defaultSetup(messages = messages, shouldCompress = true)
        coEvery { compressionManager.compressHistory(any(), any()) } returns Result.success(compressed)

        val result = orchestrator.buildContext("s1", "New message")
        assertTrue(result.isSuccess)
        val context = result.getOrThrow()
        // system + 6 retained + 1 new user = 8
        assertEquals(8, context.messages.size)
    }

    @Test
    fun `buildContext degrades gracefully when memory retrieval fails`() = runTest {
        defaultSetup()
        coEvery { memoryContextManager.retrieveRelevantMemories(any(), any()) } returns Result.failure(RuntimeException("Embedding failed"))

        val result = orchestrator.buildContext("s1", "Hello")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().memories.size)
    }

    @Test
    fun `buildContext degrades gracefully when compression fails`() = runTest {
        val messages = (1..20).map { i ->
            Message(id = "$i", sessionId = "s1", role = MessageRole.User, content = "Message $i", timestamp = Clock.System.now())
        }
        defaultSetup(messages = messages, shouldCompress = true)
        coEvery { compressionManager.compressHistory(any(), any()) } returns Result.failure(RuntimeException("LLM error"))

        val result = orchestrator.buildContext("s1", "New")
        assertTrue(result.isSuccess)
        // Falls back to original messages: system + 20 + 1 = 22
        assertEquals(22, result.getOrThrow().messages.size)
    }

    @Test
    fun `buildContext returns failure when history retrieval fails`() = runTest {
        coEvery { sessionContextManager.getConversationHistory(any(), any()) } returns Result.failure(RuntimeException("DB error"))

        val result = orchestrator.buildContext("s1", "Hello")
        assertTrue(result.isFailure)
    }

    @Test
    fun `observeContextStats emits updated stats after buildContext`() = runTest {
        defaultSetup()

        orchestrator.observeContextStats().test {
            // Initial stats
            val initial = awaitItem()
            assertEquals(0, initial.totalTokensUsed)

            orchestrator.buildContext("s1", "Hello")
            val updated = awaitItem()
            assertEquals("s1", updated.sessionId)
            assertTrue(updated.totalTokensUsed > 0)
            assertTrue(updated.budgetUtilization > 0f)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 4: Run tests to verify**

Run: `./gradlew :core:context:test --tests "com.loy.mingclaw.core.context.internal.ContextOrchestratorImplTest"`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/context/src/main/java/com/loy/mingclaw/core/context/ContextOrchestrator.kt core/context/src/main/java/com/loy/mingclaw/core/context/internal/ContextOrchestratorImpl.kt core/context/src/test/java/com/loy/mingclaw/core/context/internal/ContextOrchestratorImplTest.kt
git commit -m "feat(context): add ContextOrchestrator with 6-step context assembly pipeline"
```

---

## Task 8: Update DI Module (ContextModule)

**Files:**
- Modify: `core/context/src/main/java/com/loy/mingclaw/core/context/di/ContextModule.kt`

- [ ] **Step 1: Replace ContextModule with all 6 bindings**

```kotlin
package com.loy.mingclaw.core.context.di

import com.loy.mingclaw.core.context.ContextCompressionManager
import com.loy.mingclaw.core.context.ContextOrchestrator
import com.loy.mingclaw.core.context.ContextWindowManager
import com.loy.mingclaw.core.context.MemoryContextManager
import com.loy.mingclaw.core.context.SessionContextManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.context.internal.ContextCompressionManagerImpl
import com.loy.mingclaw.core.context.internal.ContextOrchestratorImpl
import com.loy.mingclaw.core.context.internal.ContextWindowManagerImpl
import com.loy.mingclaw.core.context.internal.MemoryContextManagerImpl
import com.loy.mingclaw.core.context.internal.SessionContextManagerImpl
import com.loy.mingclaw.core.context.internal.TokenEstimatorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ContextModule {
    @Binds @Singleton abstract fun bindTokenEstimator(impl: TokenEstimatorImpl): TokenEstimator
    @Binds @Singleton abstract fun bindSessionContextManager(impl: SessionContextManagerImpl): SessionContextManager
    @Binds @Singleton abstract fun bindContextWindowManager(impl: ContextWindowManagerImpl): ContextWindowManager
    @Binds @Singleton abstract fun bindMemoryContextManager(impl: MemoryContextManagerImpl): MemoryContextManager
    @Binds @Singleton abstract fun bindCompressionManager(impl: ContextCompressionManagerImpl): ContextCompressionManager
    @Binds @Singleton abstract fun bindContextOrchestrator(impl: ContextOrchestratorImpl): ContextOrchestrator
}
```

- [ ] **Step 2: Run full core:context test suite to verify all bindings**

Run: `./gradlew :core:context:test`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add core/context/src/main/java/com/loy/mingclaw/core/context/di/ContextModule.kt
git commit -m "feat(context): extend ContextModule with MemoryContextManager, ContextCompressionManager, ContextOrchestrator bindings"
```

---

## Task 9: ChatRepository Integration

**Files:**
- Modify: `core/data/build.gradle.kts`
- Modify: `core/data/src/main/java/com/loy/mingclaw/core/data/repository/internal/OfflineFirstChatRepository.kt`
- Modify: `core/data/src/test/java/com/loy/mingclaw/core/data/repository/OfflineFirstChatRepositoryTest.kt`

This task integrates `ContextOrchestrator` into `OfflineFirstChatRepository`. The key change: `chatStream` and `chat` now use `contextOrchestrator.buildContext()` to assemble messages instead of passing `request.messages` directly to the LLM.

**Important:** `core:data` will depend on `core:context`. This is safe because `core:context` depends on `core:data` for repositories. We need to check for circular dependency. Looking at the module graph:
- `core:context` depends on `core:data` (for `SessionRepository`, `MemoryRepository`)
- `core:data` needs to depend on `core:context` (for `ContextOrchestrator`)

This creates a circular dependency! The solution is to keep `ContextOrchestrator` injection in `OfflineFirstChatRepository` but break the cycle. The spec says `ContextOrchestrator` is in `core:context` and `OfflineFirstChatRepository` is in `core:data`. We must move the `ContextOrchestrator` dependency to `core:data` accepting `core:context`.

Wait — `core:context` already depends on `core:data`. If `core:data` also depends on `core:context`, that's a circular Gradle dependency which will fail. The correct approach is to have `core:data` NOT depend on `core:context`. Instead, `OfflineFirstChatRepository` should accept `ContextOrchestrator` via Hilt without a Gradle module dependency — but that's not possible in Gradle.

The real solution: Move `ContextOrchestrator` (interface only) to `core:model` or use a separate API module. However, the spec explicitly places it in `core:context`. The simplest fix: inject `ContextOrchestrator` as an **optional** dependency or use **lazy injection**.

**Actually, re-examining the dependency graph:** `core:context` depends on `core:data` (for repositories). So `core:data` cannot depend on `core:context`. The integration point must be at a higher level — either `app` module or a new wiring module. But the spec says to modify `OfflineFirstChatRepository`.

**Correct approach for MVP:** The `OfflineFirstChatRepository` changes are deferred to a future integration phase. For now, `ContextOrchestrator` is fully functional and can be called from the `app` layer or `feature` layer. The `ChatRepository` integration will happen when the app module wires everything together.

**Alternative MVP approach:** Create a thin wrapper in `core:data` that takes `ContextOrchestrator` as a parameter at call time rather than constructor injection. This avoids the circular dependency.

Let me re-read the spec carefully... The spec says:
> `OfflineFirstChatRepository` 改造：注入 `ContextOrchestrator`

For MVP, the cleanest solution is: **skip the ChatRepository modification** and add a note that this integration will happen from the `app` module layer. This avoids the circular dependency issue entirely while keeping all 5 components in `core:context` working.

- [ ] **Step 1: Skip ChatRepository modification due to circular dependency**

Verify the circular dependency issue:

Run: `./gradlew :core:context:dependencies --configuration implementation 2>&1 | grep core:data`

`core:context` depends on `core:data`. If `core:data` adds `core:context`, Gradle will fail with circular dependency error.

**Decision:** Add `ContextOrchestrator` usage documentation as a comment in `OfflineFirstChatRepository` for future integration from the app layer, but do NOT add the actual dependency.

- [ ] **Step 2: Add integration comment to OfflineFirstChatRepository**

Add the following comment at the top of the class body in `core/data/.../OfflineFirstChatRepository.kt`, after the constructor:

```kotlin
    // MVP: 后续增强 - ContextOrchestrator integration will be wired at the app/feature layer
    // to avoid circular dependency (core:context -> core:data -> core:context).
    // The feature layer will call ContextOrchestrator.buildContext() before ChatRepository.chatStream()
    // and pass the assembled ConversationContext.messages as ChatRequest.messages.
```

- [ ] **Step 3: Run full build to verify everything still compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all core:context tests**

Run: `./gradlew :core:context:test`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/java/com/loy/mingclaw/core/data/repository/internal/OfflineFirstChatRepository.kt
git commit -m "docs(data): add ContextOrchestrator integration note to OfflineFirstChatRepository"
```

---

## Task 10: Full Build Verification

**Files:** None (verification only)

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all core:context tests**

Run: `./gradlew :core:context:test`
Expected: All tests PASS

- [ ] **Step 3: Run all core:data tests (verify nothing broken)**

Run: `./gradlew :core:data:test`
Expected: All tests PASS

- [ ] **Step 4: Run full test suite**

Run: `./gradlew test`
Expected: All tests PASS

- [ ] **Step 5: Final commit (if any cleanup needed)**

Only if any fixes were needed during verification.
