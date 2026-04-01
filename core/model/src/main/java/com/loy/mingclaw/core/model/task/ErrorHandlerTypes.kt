package com.loy.mingclaw.core.model.task

import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

sealed interface ErrorHandlingStrategy {
    data class Retry(val maxRetries: Int, val delayMs: Long) : ErrorHandlingStrategy
    data class Fallback(val fallbackType: String) : ErrorHandlingStrategy
    object Ignore : ErrorHandlingStrategy
    object Fail : ErrorHandlingStrategy
}

sealed interface ErrorHandlingResult {
    object Ignored : ErrorHandlingResult
    data class Recovered(val data: Map<String, String> = emptyMap()) : ErrorHandlingResult
    data class Failed(val error: String) : ErrorHandlingResult
}

data class ErrorRecord(
    val id: String,
    val taskId: String,
    val error: String,
    val timestamp: Instant = Clock.System.now(),
    val metadata: Map<String, String> = emptyMap(),
)
