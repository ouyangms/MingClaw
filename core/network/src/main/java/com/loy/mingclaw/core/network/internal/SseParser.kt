package com.loy.mingclaw.core.network.internal

import com.loy.mingclaw.core.network.dto.ChatCompletionChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.Response

internal class SseParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseStream(response: Response): Flow<ChatCompletionChunk> = flow {
        // Simplified SSE parsing - reads line by line
        val source = response.body?.source() ?: return@flow
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    val chunk = json.decodeFromString<ChatCompletionChunk>(data)
                    emit(chunk)
                }
            }
        } finally {
            response.close()
        }
    }
}
