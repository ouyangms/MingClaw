package com.loy.mingclaw.core.evolution.internal

import com.loy.mingclaw.core.data.repository.MemoryRepository
import com.loy.mingclaw.core.data.repository.SessionRepository
import com.loy.mingclaw.core.evolution.model.KnowledgeCategory
import com.loy.mingclaw.core.evolution.model.KnowledgePoint
import com.loy.mingclaw.core.evolution.model.KnowledgeType
import com.loy.mingclaw.core.memory.EmbeddingService
import com.loy.mingclaw.core.model.context.Message
import com.loy.mingclaw.core.model.context.MessageRole
import com.loy.mingclaw.core.model.llm.ChatResponse
import com.loy.mingclaw.core.model.llm.LlmProvider
import com.loy.mingclaw.core.model.memory.Memory
import com.loy.mingclaw.core.model.memory.MemoryType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KnowledgeEvolverImplTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var embeddingService: EmbeddingService
    private lateinit var llmProvider: LlmProvider
    private lateinit var fileManager: EvolutionFileManager
    private lateinit var evolver: KnowledgeEvolverImpl

    @Before
    fun setup() {
        sessionRepository = mockk()
        memoryRepository = mockk()
        embeddingService = mockk()
        llmProvider = mockk()
        fileManager = mockk(relaxed = true)
        evolver = KnowledgeEvolverImpl(
            sessionRepository = sessionRepository,
            memoryRepository = memoryRepository,
            embeddingService = embeddingService,
            llmProvider = llmProvider,
            fileManager = fileManager,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun extractKnowledge_extractsFromConversation() = runTest {
        val messages = listOf(
            Message(
                id = "m1",
                sessionId = "s1",
                role = MessageRole.User,
                content = "What is Kotlin?",
            ),
            Message(
                id = "m2",
                sessionId = "s1",
                role = MessageRole.Assistant,
                content = "Kotlin is a modern programming language.",
            ),
        )
        coEvery { sessionRepository.getMessages("s1") } returns messages
        coEvery { llmProvider.chat(any(), any(), any(), any()) } returns Result.success(
            ChatResponse(
                content = """[{"type":"FACT","content":"Kotlin is a modern programming language","confidence":0.9,"importance":0.8,"categories":["DOMAIN_KNOWLEDGE"],"tags":["kotlin","programming"]}]""",
            ),
        )

        val result = evolver.extractKnowledge("s1")
        assertTrue(result.isSuccess)
        val points = result.getOrNull()!!
        assertEquals(1, points.size)
        assertEquals(KnowledgeType.FACT, points[0].type)
        assertEquals("Kotlin is a modern programming language", points[0].content)
        assertEquals(0.9f, points[0].confidence, 0.01f)
        assertEquals(setOf(KnowledgeCategory.DOMAIN_KNOWLEDGE), points[0].categories)
    }

    @Test
    fun extractKnowledge_handlesEmptyConversation() = runTest {
        coEvery { sessionRepository.getMessages("s1") } returns emptyList()

        val result = evolver.extractKnowledge("s1")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.size)
    }

    @Test
    fun extractKnowledge_handlesLlmFailure() = runTest {
        coEvery { sessionRepository.getMessages("s1") } returns listOf(
            Message(
                id = "m1",
                sessionId = "s1",
                role = MessageRole.User,
                content = "test",
            ),
        )
        coEvery { llmProvider.chat(any(), any(), any(), any()) } returns Result.failure(
            RuntimeException("LLM error"),
        )

        val result = evolver.extractKnowledge("s1")
        assertTrue(result.isFailure)
    }

    @Test
    fun extractKnowledge_handlesMalformedJsonResponse() = runTest {
        coEvery { sessionRepository.getMessages("s1") } returns listOf(
            Message(
                id = "m1",
                sessionId = "s1",
                role = MessageRole.User,
                content = "test",
            ),
        )
        coEvery { llmProvider.chat(any(), any(), any(), any()) } returns Result.success(
            ChatResponse(content = "This is not valid JSON"),
        )

        val result = evolver.extractKnowledge("s1")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.size)
    }

    @Test
    fun extractKnowledge_handlesMarkdownWrappedJson() = runTest {
        coEvery { sessionRepository.getMessages("s1") } returns listOf(
            Message(
                id = "m1",
                sessionId = "s1",
                role = MessageRole.User,
                content = "test",
            ),
        )
        coEvery { llmProvider.chat(any(), any(), any(), any()) } returns Result.success(
            ChatResponse(
                content = """```json
[{"type":"FACT","content":"Test fact","confidence":0.8,"importance":0.7,"categories":[],"tags":[]}]
```""",
            ),
        )

        val result = evolver.extractKnowledge("s1")
        assertTrue(result.isSuccess)
        val points = result.getOrNull()!!
        assertEquals(1, points.size)
        assertEquals("Test fact", points[0].content)
    }

    @Test
    fun extractKnowledge_defaultsUnknownTypeToFact() = runTest {
        coEvery { sessionRepository.getMessages("s1") } returns listOf(
            Message(
                id = "m1",
                sessionId = "s1",
                role = MessageRole.User,
                content = "test",
            ),
        )
        coEvery { llmProvider.chat(any(), any(), any(), any()) } returns Result.success(
            ChatResponse(
                content = """[{"type":"UNKNOWN_TYPE","content":"test","confidence":0.5,"importance":0.5,"categories":["INVALID_CATEGORY"],"tags":[]}]""",
            ),
        )

        val result = evolver.extractKnowledge("s1")
        assertTrue(result.isSuccess)
        val points = result.getOrNull()!!
        assertEquals(1, points.size)
        assertEquals(KnowledgeType.FACT, points[0].type)
        assertEquals(emptySet<KnowledgeCategory>(), points[0].categories)
    }

    @Test
    fun consolidateToMemory_savesNewKnowledge() = runTest {
        val kp = KnowledgePoint(
            id = "kp1",
            type = KnowledgeType.FACT,
            content = "Test knowledge",
            confidence = 0.9f,
            importance = 0.8f,
            categories = emptySet(),
            tags = emptySet(),
            extractedAt = Clock.System.now(),
        )
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.success(List(768) { 0.1f })
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { memoryRepository.save(any()) } returns Result.success(mockk())

        val result = evolver.consolidateToMemory(listOf(kp))
        assertTrue(result.isSuccess)
        val consolidation = result.getOrNull()!!
        assertEquals(1, consolidation.added)
        assertEquals(0, consolidation.skipped)
        coVerify { memoryRepository.save(any()) }
    }

    @Test
    fun consolidateToMemory_skipsDuplicates() = runTest {
        val kp = KnowledgePoint(
            id = "kp1",
            type = KnowledgeType.FACT,
            content = "Duplicate knowledge",
            confidence = 0.9f,
            importance = 0.8f,
            categories = emptySet(),
            tags = emptySet(),
            extractedAt = Clock.System.now(),
        )
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.success(List(768) { 0.1f })
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.success(
            listOf(mockk()),
        )

        val result = evolver.consolidateToMemory(listOf(kp))
        assertTrue(result.isSuccess)
        val consolidation = result.getOrNull()!!
        assertEquals(0, consolidation.added)
        assertEquals(1, consolidation.skipped)
    }

    @Test
    fun consolidateToMemory_handlesEmbeddingFailure() = runTest {
        val kp = KnowledgePoint(
            id = "kp1",
            type = KnowledgeType.FACT,
            content = "Test",
            confidence = 0.9f,
            importance = 0.8f,
            categories = emptySet(),
            tags = emptySet(),
            extractedAt = Clock.System.now(),
        )
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.failure(
            RuntimeException("Embedding failed"),
        )

        val result = evolver.consolidateToMemory(listOf(kp))
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()!!.skipped)
    }

    @Test
    fun consolidateToMemory_handlesSaveFailure() = runTest {
        val kp = KnowledgePoint(
            id = "kp1",
            type = KnowledgeType.FACT,
            content = "Test",
            confidence = 0.9f,
            importance = 0.8f,
            categories = emptySet(),
            tags = emptySet(),
            extractedAt = Clock.System.now(),
        )
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.success(List(768) { 0.1f })
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { memoryRepository.save(any()) } returns Result.failure(RuntimeException("Save failed"))

        val result = evolver.consolidateToMemory(listOf(kp))
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.added)
        assertEquals(1, result.getOrNull()!!.skipped)
    }

    @Test
    fun consolidateToMemory_handlesVectorSearchFailure() = runTest {
        val kp = KnowledgePoint(
            id = "kp1",
            type = KnowledgeType.FACT,
            content = "Test",
            confidence = 0.9f,
            importance = 0.8f,
            categories = emptySet(),
            tags = emptySet(),
            extractedAt = Clock.System.now(),
        )
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.success(List(768) { 0.1f })
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.failure(
            RuntimeException("Search failed"),
        )

        val result = evolver.consolidateToMemory(listOf(kp))
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.added)
        assertEquals(1, result.getOrNull()!!.skipped)
    }

    @Test
    fun consolidateToMemory_processesMultipleKnowledgePoints() = runTest {
        val kp1 = KnowledgePoint(
            id = "kp1", type = KnowledgeType.FACT, content = "First",
            confidence = 0.9f, importance = 0.8f, categories = emptySet(),
            tags = emptySet(), extractedAt = Clock.System.now(),
        )
        val kp2 = KnowledgePoint(
            id = "kp2", type = KnowledgeType.CONCEPT, content = "Second",
            confidence = 0.7f, importance = 0.6f, categories = emptySet(),
            tags = emptySet(), extractedAt = Clock.System.now(),
        )
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.success(List(768) { 0.1f })
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { memoryRepository.save(any()) } returns Result.success(mockk())

        val result = evolver.consolidateToMemory(listOf(kp1, kp2))
        assertTrue(result.isSuccess)
        val consolidation = result.getOrNull()!!
        assertEquals(2, consolidation.added)
        assertEquals(0, consolidation.skipped)
    }

    @Test
    fun consolidateToMemory_emptyListReturnsZeroCounts() = runTest {
        val result = evolver.consolidateToMemory(emptyList())
        assertTrue(result.isSuccess)
        val consolidation = result.getOrNull()!!
        assertEquals(0, consolidation.added)
        assertEquals(0, consolidation.skipped)
    }

    @Test
    fun searchMemory_delegatesToRepository() = runTest {
        coEvery { embeddingService.generateEmbedding("kotlin") } returns Result.success(List(768) { 0.1f })
        val memory = Memory(
            id = "m1",
            content = "Kotlin info",
            type = MemoryType.Semantic,
            importance = 0.8f,
            metadata = mapOf("knowledgeType" to "FACT", "confidence" to "0.9"),
            embedding = emptyList(),
            createdAt = Clock.System.now(),
            accessedAt = Clock.System.now(),
        )
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.success(listOf(memory))

        val result = evolver.searchMemory("kotlin")
        assertTrue(result.isSuccess)
        val points = result.getOrNull()!!
        assertEquals(1, points.size)
        assertEquals("Kotlin info", points[0].content)
        assertEquals(KnowledgeType.FACT, points[0].type)
    }

    @Test
    fun searchMemory_handlesEmbeddingFailure() = runTest {
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.failure(
            RuntimeException("Embedding failed"),
        )

        val result = evolver.searchMemory("test")
        assertTrue(result.isFailure)
    }

    @Test
    fun searchMemory_handlesVectorSearchFailure() = runTest {
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.success(List(768) { 0.1f })
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.failure(
            RuntimeException("Search failed"),
        )

        val result = evolver.searchMemory("test")
        assertTrue(result.isFailure)
    }

    @Test
    fun searchMemory_returnsEmptyListWhenNoMatches() = runTest {
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.success(List(768) { 0.1f })
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.success(emptyList())

        val result = evolver.searchMemory("nonexistent")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()!!.size)
    }

    @Test
    fun searchMemory_handlesUnknownKnowledgeTypeInMetadata() = runTest {
        coEvery { embeddingService.generateEmbedding(any()) } returns Result.success(List(768) { 0.1f })
        val memory = Memory(
            id = "m1",
            content = "Some info",
            type = MemoryType.Semantic,
            importance = 0.5f,
            metadata = mapOf("knowledgeType" to "INVALID_TYPE"),
            embedding = emptyList(),
            createdAt = Clock.System.now(),
            accessedAt = Clock.System.now(),
        )
        coEvery { memoryRepository.vectorSearch(any(), any(), any()) } returns Result.success(listOf(memory))

        val result = evolver.searchMemory("test")
        assertTrue(result.isSuccess)
        val points = result.getOrNull()!!
        assertEquals(KnowledgeType.FACT, points[0].type)
    }
}
