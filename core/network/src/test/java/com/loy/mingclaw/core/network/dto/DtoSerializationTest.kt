package com.loy.mingclaw.core.network.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DtoSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun chatCompletionRequest_serialization() {
        val request = ChatCompletionRequest(
            model = "gpt-4",
            messages = listOf(
                ChatMessageDto(role = "user", content = "Hello"),
            ),
            temperature = 0.7,
            max_tokens = 100,
        )
        val encoded = json.encodeToString(ChatCompletionRequest.serializer(), request)
        val decoded = json.decodeFromString(ChatCompletionRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun chatCompletionResponse_deserialization() {
        val jsonString = """
        {
            "id": "chatcmpl-123",
            "choices": [
                {
                    "index": 0,
                    "message": {
                        "role": "assistant",
                        "content": "Hello! How can I help you?"
                    },
                    "finish_reason": "stop"
                }
            ],
            "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 8,
                "total_tokens": 18
            }
        }
        """.trimIndent()
        val response = json.decodeFromString<ChatCompletionResponse>(jsonString)
        assertEquals("chatcmpl-123", response.id)
        assertEquals(1, response.choices.size)
        assertEquals("assistant", response.choices[0].message?.role)
        assertEquals(18, response.usage?.total_tokens)
    }

    @Test
    fun chatCompletionChunk_deserialization() {
        val jsonString = """
        {
            "id": "chatcmpl-123",
            "choices": [
                {
                    "index": 0,
                    "delta": {
                        "content": "Hello"
                    },
                    "finish_reason": null
                }
            ]
        }
        """.trimIndent()
        val chunk = json.decodeFromString<ChatCompletionChunk>(jsonString)
        assertEquals("chatcmpl-123", chunk.id)
        assertEquals(1, chunk.choices.size)
        assertEquals("Hello", chunk.choices[0].delta?.content)
    }

    @Test
    fun chatMessageDto_roles() {
        val messages = listOf(
            ChatMessageDto(role = "system", content = "You are helpful"),
            ChatMessageDto(role = "user", content = "Hi"),
            ChatMessageDto(role = "assistant", content = "Hello"),
        )
        assertEquals(3, messages.size)
        assertEquals("system", messages[0].role)
    }
}
