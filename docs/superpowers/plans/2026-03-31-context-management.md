# MingClaw 上下文管理模块实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 MingClaw 上下文管理模块——会话管理 (SessionContextManager)、Token 估算器 (TokenEstimator)、上下文窗口管理器 (ContextWindowManager)，为 LLM 交互提供会话历史管理和 Token 预算分配能力。

**Architecture:** 在 core:kernel 和 core:model 之上新增 core:context 模块。MVP 阶段聚焦不依赖 LLM 服务的组件：会话 CRUD、消息管理、Token 估算、上下文窗口预算分配。记忆检索 (MemoryContextManager) 和上下文压缩 (ContextCompressionManager) 依赖 LLM/向量存储，后续实现。

**Tech Stack:** Kotlin 2.0.21, Hilt 2.51, Coroutines 1.7.3, kotlinx.serialization 1.6.2, kotlinx-datetime 0.5.0, JUnit 4, MockK 1.13.9

---

## 文件结构总览

```
core/model/
  src/main/java/com/loy/mingclaw/core/model/context/
    Session.kt                  # Session, SessionStatus, SessionContext
    Message.kt                  # Message, MessageRole, ToolCall
    ContextTypes.kt             # TokenStats, SessionEvent, ContextComponent, WindowStatistics
  src/test/java/com/loy/mingclaw/core/model/context/
    SessionTest.kt              # 序列化测试
    MessageTest.kt              # 消息序列化测试

core/context/
  build.gradle.kts
  src/main/java/com/loy/mingclaw/core/context/
    SessionContextManager.kt    # 会话管理接口
    ContextWindowManager.kt     # 上下文窗口管理接口
    TokenEstimator.kt           # Token 估算接口
    internal/
      SessionContextManagerImpl.kt
      ContextWindowManagerImpl.kt
      TokenEstimatorImpl.kt
    di/
      ContextModule.kt          # Hilt DI 模块
  src/test/java/com/loy/mingclaw/core/context/
    TokenEstimatorImplTest.kt
    SessionContextManagerImplTest.kt
    ContextWindowManagerImplTest.kt
```

---

### Task 1: 添加上下文领域类型到 core:model

**Files:**
- Create: `core/model/src/main/java/com/loy/mingclaw/core/model/context/Session.kt`
- Create: `core/model/src/main/java/com/loy/mingclaw/core/model/context/Message.kt`
- Create: `core/model/src/main/java/com/loy/mingclaw/core/model/context/ContextTypes.kt`
- Test: `core/model/src/test/java/com/loy/mingclaw/core/model/context/SessionTest.kt`
- Test: `core/model/src/test/java/com/loy/mingclaw/core/model/context/MessageTest.kt`

- [ ] **Step 1: 写 Session 序列化测试**

`core/model/src/test/java/com/loy/mingclaw/core/model/context/SessionTest.kt`:

```kotlin
package com.loy.mingclaw.core.model.context

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Session serializes and deserializes`() {
        val now = Clock.System.now()
        val original = Session(
            id = "session-1",
            title = "Test Session",
            createdAt = now,
            updatedAt = now,
            metadata = mapOf("key" to "value"),
            status = SessionStatus.Active
        )
        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<Session>(jsonString)
        assertEquals(original, restored)
    }

    @Test
    fun `SessionStatus has expected values`() {
        val statuses = SessionStatus.values()
        assertEquals(3, statuses.size)
        assertEquals(SessionStatus.Active, statuses[0])
        assertEquals(SessionStatus.Archived, statuses[1])
        assertEquals(SessionStatus.Deleted, statuses[2])
    }

    @Test
    fun `SessionContext computes messageCount`() {
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "hi"),
            Message(id = "2", sessionId = "s1", role = MessageRole.Assistant, content = "hello")
        )
        val context = SessionContext(
            sessionId = "s1",
            title = "Test",
            messages = messages,
            metadata = emptyMap(),
            status = SessionStatus.Active
        )
        assertEquals(2, context.messageCount)
    }
}
```

- [ ] **Step 2: 写 Message 序列化测试**

`core/model/src/test/java/com/loy/mingclaw/core/model/context/MessageTest.kt`:

```kotlin
package com.loy.mingclaw.core.model.context

import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `Message serializes and deserializes`() {
        val now = Clock.System.now()
        val original = Message(
            id = "msg-1",
            sessionId = "session-1",
            role = MessageRole.User,
            content = "Hello, how are you?",
            timestamp = now
        )
        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<Message>(jsonString)
        assertEquals(original, restored)
    }

    @Test
    fun `MessageRole has expected values`() {
        val roles = MessageRole.values()
        assertEquals(4, roles.size)
        assertEquals(MessageRole.User, roles[0])
        assertEquals(MessageRole.Assistant, roles[1])
        assertEquals(MessageRole.System, roles[2])
        assertEquals(MessageRole.Tool, roles[3])
    }

    @Test
    fun `ToolCall serializes and deserializes`() {
        val original = ToolCall(
            id = "call-1",
            name = "search",
            arguments = """{"query": "test"}"""
        )
        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<ToolCall>(jsonString)
        assertEquals(original, restored)
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `./gradlew :core:model:test`
Expected: FAIL — 源码不存在

- [ ] **Step 4: 创建 Session.kt**

`core/model/src/main/java/com/loy/mingclaw/core/model/context/Session.kt`:

```kotlin
package com.loy.mingclaw.core.model.context

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val metadata: Map<String, String> = emptyMap(),
    val status: SessionStatus = SessionStatus.Active,
)

enum class SessionStatus {
    Active, Archived, Deleted
}

data class SessionContext(
    val sessionId: String,
    val title: String,
    val messages: List<Message>,
    val metadata: Map<String, String> = emptyMap(),
    val status: SessionStatus = SessionStatus.Active,
) {
    val messageCount: Int get() = messages.size
    val totalTokens: Int get() = 0 // Computed by SessionContextManager
}
```

- [ ] **Step 5: 创建 Message.kt**

`core/model/src/main/java/com/loy/mingclaw/core/model/context/Message.kt`:

```kotlin
package com.loy.mingclaw.core.model.context

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Instant? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val editedAt: Instant? = null,
)

enum class MessageRole {
    User, Assistant, System, Tool
}

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)
```

- [ ] **Step 6: 创建 ContextTypes.kt**

`core/model/src/main/java/com/loy/mingclaw/core/model/context/ContextTypes.kt`:

```kotlin
package com.loy.mingclaw.core.model.context

data class TokenStats(
    val totalTokens: Int,
    val userTokens: Int,
    val assistantTokens: Int,
)

sealed interface SessionEvent {
    data class Created(val session: Session) : SessionEvent
    data class MessageAdded(val message: Message) : SessionEvent
    data class MessageUpdated(val message: Message) : SessionEvent
    data class MessageDeleted(val messageId: String) : SessionEvent
    data class StatusChanged(val status: SessionStatus) : SessionEvent
    data class Deleted(val sessionId: String) : SessionEvent
}

data class ContextComponent(
    val id: String,
    val name: String,
    val requestedTokens: Int,
    val maxTokens: Int = Int.MAX_VALUE,
    val priority: Int = 0,
)

data class WindowStatistics(
    val averageTokenUsage: Double = 0.0,
    val peakTokenUsage: Int = 0,
    val compressionCount: Int = 0,
)
```

- [ ] **Step 7: 运行测试确认通过**

Run: `./gradlew :core:model:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add core/model/src/main/java/com/loy/mingclaw/core/model/context/ core/model/src/test/java/com/loy/mingclaw/core/model/context/
git commit -m "feat: add context domain types (Session, Message, TokenStats, SessionEvent)"
```

---

### Task 2: 创建 core:context 模块基础设施

**Files:**
- Create: `core/context/build.gradle.kts`
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/SessionContextManager.kt`
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/ContextWindowManager.kt`
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/TokenEstimator.kt`
- Modify: `settings.gradle.kts` (添加 `:core:context`)

- [ ] **Step 1: 创建 core/context/build.gradle.kts**

```kotlin
plugins {
    id("mingclaw.android.library")
    id("mingclaw.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.loy.mingclaw.core.context"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:kernel"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
```

- [ ] **Step 2: 创建 SessionContextManager.kt 接口**

```kotlin
package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionContext
import com.loy.mingclaw.core.model.context.SessionEvent
import com.loy.mingclaw.core.model.context.SessionStatus
import kotlinx.coroutines.flow.Flow

interface SessionContextManager {
    suspend fun createSession(
        title: String? = null,
        metadata: Map<String, String> = emptyMap(),
    ): Result<Session>

    suspend fun getSession(sessionId: String): Result<Session>
    suspend fun getSessionContext(sessionId: String): Result<SessionContext>
    suspend fun addMessage(sessionId: String, message: Message): Result<Message>
    suspend fun getConversationHistory(
        sessionId: String,
        limit: Int? = null,
    ): Result<List<Message>>

    suspend fun deleteSession(sessionId: String): Result<Unit>
    suspend fun archiveSession(sessionId: String): Result<Unit>
    suspend fun getAllSessions(includeArchived: Boolean = false): Result<List<Session>>
    fun watchSession(sessionId: String): Flow<SessionEvent>
}
```

- [ ] **Step 3: 创建 ContextWindowManager.kt 接口**

```kotlin
package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.TokenBudget
import com.loy.mingclaw.core.model.context.ContextComponent
import com.loy.mingclaw.core.model.context.SessionContext
import com.loy.mingclaw.core.model.context.WindowStatistics

interface ContextWindowManager {
    fun calculateTokenBudget(): TokenBudget
    fun allocateTokenBudget(
        budget: TokenBudget,
        components: List<ContextComponent>,
    ): Map<String, Int>

    fun estimateTokens(content: String): Int
    fun shouldCompress(context: SessionContext): Boolean
    fun getWindowStatistics(): WindowStatistics
}
```

- [ ] **Step 4: 创建 TokenEstimator.kt 接口**

```kotlin
package com.loy.mingclaw.core.context

interface TokenEstimator {
    fun estimate(text: String): Int
    fun estimateMessages(messages: List<com.loy.mingclaw.core.model.context.Message>): Int
}
```

- [ ] **Step 5: 添加 `:core:context` 到 settings.gradle.kts**

在 `settings.gradle.kts` 的 `include` 块中添加 `include(":core:context")`。

- [ ] **Step 6: Commit**

```bash
git add core/context/ settings.gradle.kts
git commit -m "feat: add core:context module interfaces (SessionContextManager, ContextWindowManager, TokenEstimator)"
```

---

### Task 3: 实现 TokenEstimator

**Files:**
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/internal/TokenEstimatorImpl.kt`
- Test: `core/context/src/test/java/com/loy/mingclaw/core/context/TokenEstimatorImplTest.kt`

- [ ] **Step 1: 写 TokenEstimator 测试**

`core/context/src/test/java/com/loy/mingclaw/core/context/TokenEstimatorImplTest.kt`:

```kotlin
package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.context.internal.TokenEstimatorImpl
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TokenEstimatorImplTest {

    private lateinit var estimator: TokenEstimatorImpl

    @Before
    fun setup() {
        estimator = TokenEstimatorImpl()
    }

    @Test
    fun `estimate returns positive for non-empty text`() {
        val tokens = estimator.estimate("Hello, how are you?")
        assertTrue(tokens > 0)
    }

    @Test
    fun `estimate returns zero for empty text`() {
        assertEquals(0, estimator.estimate(""))
    }

    @Test
    fun `estimate scales with text length`() {
        val short = estimator.estimate("Hi")
        val long = estimator.estimate("This is a much longer sentence with many more words in it.")
        assertTrue(long > short)
    }

    @Test
    fun `estimate uses chars-per-token heuristic`() {
        // Default: ~4 chars per token
        val tokens = estimator.estimate("abcd") // 4 chars
        assertEquals(1, tokens)
    }

    @Test
    fun `estimateMessages sums message tokens`() {
        val messages = listOf(
            Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "Hello there"),
            Message(id = "2", sessionId = "s1", role = MessageRole.Assistant, content = "Hi! How can I help?"),
        )
        val total = estimator.estimateMessages(messages)
        val expected = estimator.estimate("Hello there") + estimator.estimate("Hi! How can I help?")
        assertEquals(expected, total)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:context:test --tests TokenEstimatorImplTest`
Expected: FAIL

- [ ] **Step 3: 创建 TokenEstimatorImpl**

`core/context/src/main/java/com/loy/mingclaw/core/context/internal/TokenEstimatorImpl.kt`:

```kotlin
package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.model.context.Message
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class TokenEstimatorImpl @Inject constructor() : TokenEstimator {

    private val charsPerToken = 4

    override fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length + charsPerToken - 1) / charsPerToken
    }

    override fun estimateMessages(messages: List<Message>): Int {
        return messages.sumOf { estimate(it.content) }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :core:context:test --tests TokenEstimatorImplTest`
Expected: BUILD SUCCESSFUL, 5 tests pass

- [ ] **Step 5: Commit**

```bash
git add core/context/src/main/java/com/loy/mingclaw/core/context/internal/TokenEstimatorImpl.kt core/context/src/test/java/com/loy/mingclaw/core/context/TokenEstimatorImplTest.kt
git commit -m "feat: implement TokenEstimator with chars-per-token heuristic"
```

---

### Task 4: 实现 SessionContextManager

**Files:**
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/internal/SessionContextManagerImpl.kt`
- Test: `core/context/src/test/java/com/loy/mingclaw/core/context/SessionContextManagerImplTest.kt`

- [ ] **Step 1: 写 SessionContextManager 测试**

`core/context/src/test/java/com/loy/mingclaw/core/context/SessionContextManagerImplTest.kt`:

```kotlin
package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.context.SessionStatus
import com.loy.mingclaw.core.context.internal.SessionContextManagerImpl
import com.loy.mingclaw.core.context.internal.TokenEstimatorImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionContextManagerImplTest {

    private lateinit var manager: SessionContextManagerImpl
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        manager = SessionContextManagerImpl(
            tokenEstimator = TokenEstimatorImpl(),
            dispatcher = testDispatcher,
        )
    }

    @Test
    fun `createSession creates session with default title`() = runTest(testDispatcher) {
        val result = manager.createSession()
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertTrue(session.title.isNotEmpty())
        assertEquals(SessionStatus.Active, session.status)
    }

    @Test
    fun `createSession creates session with custom title`() = runTest(testDispatcher) {
        val result = manager.createSession(title = "My Chat")
        assertTrue(result.isSuccess)
        assertEquals("My Chat", result.getOrThrow().title)
    }

    @Test
    fun `getSession returns created session`() = runTest(testDispatcher) {
        val created = manager.createSession(title = "Test").getOrThrow()
        val result = manager.getSession(created.id)
        assertTrue(result.isSuccess)
        assertEquals("Test", result.getOrThrow().title)
    }

    @Test
    fun `getSession returns failure for unknown id`() = runTest(testDispatcher) {
        val result = manager.getSession("nonexistent")
        assertTrue(result.isFailure)
    }

    @Test
    fun `addMessage stores message in session`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "Chat").getOrThrow()
        val message = Message(
            id = "msg-1",
            sessionId = session.id,
            role = MessageRole.User,
            content = "Hello!",
        )
        val result = manager.addMessage(session.id, message)
        assertTrue(result.isSuccess)
        assertEquals("Hello!", result.getOrThrow().content)
    }

    @Test
    fun `getConversationHistory returns messages`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "Chat").getOrThrow()
        manager.addMessage(session.id, Message(
            id = "1", sessionId = session.id, role = MessageRole.User, content = "Hi"
        ))
        manager.addMessage(session.id, Message(
            id = "2", sessionId = session.id, role = MessageRole.Assistant, content = "Hello"
        ))
        val history = manager.getConversationHistory(session.id)
        assertTrue(history.isSuccess)
        assertEquals(2, history.getOrThrow().size)
    }

    @Test
    fun `getConversationHistory with limit`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "Chat").getOrThrow()
        repeat(5) { i ->
            manager.addMessage(session.id, Message(
                id = "msg-$i", sessionId = session.id, role = MessageRole.User, content = "Msg $i"
            ))
        }
        val history = manager.getConversationHistory(session.id, limit = 3)
        assertTrue(history.isSuccess)
        assertEquals(3, history.getOrThrow().size)
    }

    @Test
    fun `deleteSession removes session`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "To Delete").getOrThrow()
        val deleteResult = manager.deleteSession(session.id)
        assertTrue(deleteResult.isSuccess)
        assertTrue(manager.getSession(session.id).isFailure)
    }

    @Test
    fun `archiveSession changes status`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "Archive Me").getOrThrow()
        manager.archiveSession(session.id)
        val archived = manager.getSession(session.id).getOrThrow()
        assertEquals(SessionStatus.Archived, archived.status)
    }

    @Test
    fun `getAllSessions returns active sessions only`() = runTest(testDispatcher) {
        manager.createSession(title = "Active")
        val s2 = manager.createSession(title = "Archive").getOrThrow()
        manager.archiveSession(s2.id)
        val sessions = manager.getAllSessions(includeArchived = false)
        assertTrue(sessions.isSuccess)
        assertEquals(1, sessions.getOrThrow().size)
    }

    @Test
    fun `getAllSessions includes archived when requested`() = runTest(testDispatcher) {
        manager.createSession(title = "Active")
        val s2 = manager.createSession(title = "Archive").getOrThrow()
        manager.archiveSession(s2.id)
        val sessions = manager.getAllSessions(includeArchived = true)
        assertTrue(sessions.isSuccess)
        assertEquals(2, sessions.getOrThrow().size)
    }

    @Test
    fun `getSessionContext returns context with messages`() = runTest(testDispatcher) {
        val session = manager.createSession(title = "Context").getOrThrow()
        manager.addMessage(session.id, Message(
            id = "1", sessionId = session.id, role = MessageRole.User, content = "Hello"
        ))
        val context = manager.getSessionContext(session.id)
        assertTrue(context.isSuccess)
        assertEquals(1, context.getOrThrow().messageCount)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:context:test --tests SessionContextManagerImplTest`
Expected: FAIL

- [ ] **Step 3: 创建 SessionContextManagerImpl**

`core/context/src/main/java/com/loy/mingclaw/core/context/internal/SessionContextManagerImpl.kt`:

```kotlin
package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.common.dispatchers.IODispatcher
import com.loy.mingclaw.core.context.SessionContextManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.Session
import com.loy.mingclaw.core.model.context.SessionContext
import com.loy.mingclaw.core.model.context.SessionEvent
import com.loy.mingclaw.core.model.context.SessionStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SessionContextManagerImpl @Inject constructor(
    private val tokenEstimator: TokenEstimator,
    @IODispatcher private val dispatcher: CoroutineDispatcher,
) : SessionContextManager {

    private val sessions = ConcurrentHashMap<String, Session>()
    private val messages = ConcurrentHashMap<String, MutableList<Message>>()
    private val sessionEvents = ConcurrentHashMap<String, MutableSharedFlow<SessionEvent>>()

    override suspend fun createSession(
        title: String?,
        metadata: Map<String, String>,
    ): Result<Session> = withContext(dispatcher) {
        val sessionId = UUID.randomUUID().toString()
        val now = kotlinx.datetime.Clock.System.now()
        val session = Session(
            id = sessionId,
            title = title ?: "Session ${now.toEpochMilliseconds()}",
            createdAt = now,
            updatedAt = now,
            metadata = metadata,
            status = SessionStatus.Active,
        )
        sessions[sessionId] = session
        messages[sessionId] = mutableListOf()
        sessionEvents[sessionId] = MutableSharedFlow(replay = 10)
        emitEvent(sessionId, SessionEvent.Created(session))
        Result.success(session)
    }

    override suspend fun getSession(sessionId: String): Result<Session> {
        val session = sessions[sessionId]
            ?: return Result.failure(IllegalArgumentException("Session not found: $sessionId"))
        return Result.success(session)
    }

    override suspend fun getSessionContext(sessionId: String): Result<SessionContext> {
        val session = sessions[sessionId]
            ?: return Result.failure(IllegalArgumentException("Session not found: $sessionId"))
        val sessionMessages = messages[sessionId] ?: emptyList()
        return Result.success(
            SessionContext(
                sessionId = session.id,
                title = session.title,
                messages = sessionMessages.toList(),
                metadata = session.metadata,
                status = session.status,
            )
        )
    }

    override suspend fun addMessage(sessionId: String, message: Message): Result<Message> {
        val session = sessions[sessionId]
            ?: return Result.failure(IllegalArgumentException("Session not found: $sessionId"))
        val now = kotlinx.datetime.Clock.System.now()
        val savedMessage = message.copy(
            sessionId = sessionId,
            timestamp = message.timestamp ?: now,
        )
        messages.getOrPut(sessionId) { mutableListOf() }.add(savedMessage)
        sessions[sessionId] = session.copy(updatedAt = now)
        emitEvent(sessionId, SessionEvent.MessageAdded(savedMessage))
        return Result.success(savedMessage)
    }

    override suspend fun getConversationHistory(
        sessionId: String,
        limit: Int?,
    ): Result<List<Message>> {
        val sessionMessages = messages[sessionId]
            ?: return Result.failure(IllegalArgumentException("Session not found: $sessionId"))
        val result = if (limit != null) {
            sessionMessages.takeLast(limit)
        } else {
            sessionMessages.toList()
        }
        return Result.success(result)
    }

    override suspend fun deleteSession(sessionId: String): Result<Unit> {
        sessions.remove(sessionId)
        messages.remove(sessionId)
        sessionEvents.remove(sessionId)
        return Result.success(Unit)
    }

    override suspend fun archiveSession(sessionId: String): Result<Unit> {
        val session = sessions[sessionId]
            ?: return Result.failure(IllegalArgumentException("Session not found: $sessionId"))
        val now = kotlinx.datetime.Clock.System.now()
        sessions[sessionId] = session.copy(status = SessionStatus.Archived, updatedAt = now)
        emitEvent(sessionId, SessionEvent.StatusChanged(SessionStatus.Archived))
        return Result.success(Unit)
    }

    override suspend fun getAllSessions(includeArchived: Boolean): Result<List<Session>> {
        val all = sessions.values.toList()
        val filtered = if (includeArchived) {
            all
        } else {
            all.filter { it.status == SessionStatus.Active }
        }
        return Result.success(filtered)
    }

    override fun watchSession(sessionId: String): Flow<SessionEvent> {
        return sessionEvents.getOrPut(sessionId) {
            MutableSharedFlow(replay = 10)
        }.asSharedFlow()
    }

    private suspend fun emitEvent(sessionId: String, event: SessionEvent) {
        sessionEvents[sessionId]?.emit(event)
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :core:context:test --tests SessionContextManagerImplTest`
Expected: BUILD SUCCESSFUL, 12 tests pass

- [ ] **Step 5: Commit**

```bash
git add core/context/src/main/java/com/loy/mingclaw/core/context/internal/SessionContextManagerImpl.kt core/context/src/test/java/com/loy/mingclaw/core/context/SessionContextManagerImplTest.kt
git commit -m "feat: implement SessionContextManager with in-memory session and message storage"
```

---

### Task 5: 实现 ContextWindowManager 与 DI 模块

**Files:**
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/internal/ContextWindowManagerImpl.kt`
- Create: `core/context/src/main/java/com/loy/mingclaw/core/context/di/ContextModule.kt`
- Test: `core/context/src/test/java/com/loy/mingclaw/core/context/ContextWindowManagerImplTest.kt`

- [ ] **Step 1: 写 ContextWindowManager 测试**

`core/context/src/test/java/com/loy/mingclaw/core/context/ContextWindowManagerImplTest.kt`:

```kotlin
package com.loy.mingclaw.core.context

import com.loy.mingclaw.core.context.internal.ContextWindowManagerImpl
import com.loy.mingclaw.core.context.internal.TokenEstimatorImpl
import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.model.KernelConfig
import com.loy.mingclaw.core.model.context.ContextComponent
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.context.SessionContext
import com.loy.mingclaw.core.model.context.SessionStatus
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
            override fun watchConfigChanges() = kotlinx.coroutines.flow.flowOf(KernelConfig())
        }
        windowManager = ContextWindowManagerImpl(
            tokenEstimator = TokenEstimatorImpl(),
            configManager = configManager,
        )
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
        val budget = windowManager.calculateTokenBudget()
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
        val tokens = windowManager.estimateTokens("Hello, world!")
        assertTrue(tokens > 0)
    }

    @Test
    fun `shouldCompress returns false for small context`() {
        val context = SessionContext(
            sessionId = "s1",
            title = "Test",
            messages = listOf(
                Message(id = "1", sessionId = "s1", role = MessageRole.User, content = "Hi")
            ),
            status = SessionStatus.Active,
        )
        assertFalse(windowManager.shouldCompress(context))
    }

    @Test
    fun `shouldCompress returns true for large context`() {
        // Create a context that exceeds 80% of maxTokens (8192 * 0.8 = 6553 tokens)
        val largeMessages = (1..500).map { i ->
            Message(
                id = "$i",
                sessionId = "s1",
                role = MessageRole.User,
                content = "This is message number $i with some extra text to make it longer.",
            )
        }
        val context = SessionContext(
            sessionId = "s1",
            title = "Large",
            messages = largeMessages,
            status = SessionStatus.Active,
        )
        assertTrue(windowManager.shouldCompress(context))
    }

    @Test
    fun `getWindowStatistics returns default stats`() {
        val stats = windowManager.getWindowStatistics()
        assertEquals(0.0, stats.averageTokenUsage, 0.01)
        assertEquals(0, stats.peakTokenUsage)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :core:context:test --tests ContextWindowManagerImplTest`
Expected: FAIL

- [ ] **Step 3: 创建 ContextWindowManagerImpl**

`core/context/src/main/java/com/loy/mingclaw/core/context/internal/ContextWindowManagerImpl.kt`:

```kotlin
package com.loy.mingclaw.core.context.internal

import com.loy.mingclaw.core.context.ContextWindowManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.kernel.ConfigManager
import com.loy.mingclaw.core.model.TokenBudget
import com.loy.mingclaw.core.model.context.ContextComponent
import com.loy.mingclaw.core.model.context.SessionContext
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

    override fun allocateTokenBudget(
        budget: TokenBudget,
        components: List<ContextComponent>,
    ): Map<String, Int> {
        val allocation = mutableMapOf<String, Int>()
        var remaining = budget.totalTokens - budget.systemTokens

        val sorted = components.sortedByDescending { it.priority }
        for (component in sorted) {
            val allocated = minOf(
                component.requestedTokens,
                remaining,
                component.maxTokens,
            )
            allocation[component.id] = allocated
            remaining -= allocated
            if (remaining <= 0) break
        }

        return allocation
    }

    override fun estimateTokens(content: String): Int {
        return tokenEstimator.estimate(content)
    }

    override fun shouldCompress(context: SessionContext): Boolean {
        val config = configManager.getConfig()
        val threshold = (config.maxTokens * 0.8).toInt()
        val contextTokens = tokenEstimator.estimateMessages(context.messages)
        return contextTokens > threshold
    }

    override fun getWindowStatistics(): WindowStatistics {
        return WindowStatistics()
    }
}
```

- [ ] **Step 4: 创建 Hilt DI 模块**

`core/context/src/main/java/com/loy/mingclaw/core/context/di/ContextModule.kt`:

```kotlin
package com.loy.mingclaw.core.context.di

import com.loy.mingclaw.core.context.ContextWindowManager
import com.loy.mingclaw.core.context.SessionContextManager
import com.loy.mingclaw.core.context.TokenEstimator
import com.loy.mingclaw.core.context.internal.ContextWindowManagerImpl
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

    @Binds
    @Singleton
    abstract fun bindTokenEstimator(impl: TokenEstimatorImpl): TokenEstimator

    @Binds
    @Singleton
    abstract fun bindSessionContextManager(impl: SessionContextManagerImpl): SessionContextManager

    @Binds
    @Singleton
    abstract fun bindContextWindowManager(impl: ContextWindowManagerImpl): ContextWindowManager
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `./gradlew :core:context:test`
Expected: BUILD SUCCESSFUL, all tests pass (5 TokenEstimator + 12 SessionContext + 6 ContextWindow = 23)

- [ ] **Step 6: Commit**

```bash
git add core/context/src/main/java/com/loy/mingclaw/core/context/internal/ContextWindowManagerImpl.kt core/context/src/main/java/com/loy/mingclaw/core/context/di/ContextModule.kt core/context/src/test/java/com/loy/mingclaw/core/context/ContextWindowManagerImplTest.kt
git commit -m "feat: implement ContextWindowManager, DI module, and wire context components"
```

---

### Task 6: 全量验证

**Files:** 全项目

- [ ] **Step 1: 运行全部测试**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 构建 Debug APK**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 验证模块结构**

Run: `./gradlew projects`
Expected: 包含 `:core:context`

- [ ] **Step 4: Commit 如有更改**

```bash
git add -A
git commit -m "chore: verify context management build and tests"
```
