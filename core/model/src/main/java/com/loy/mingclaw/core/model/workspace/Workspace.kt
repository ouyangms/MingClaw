package com.loy.mingclaw.core.model.workspace

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val path: String,
    val createdAt: Instant,
    val modifiedAt: Instant,
    val isActive: Boolean = false,
    val metadata: WorkspaceMetadata = WorkspaceMetadata(),
)

@Serializable
data class WorkspaceMetadata(
    val description: String = "",
    val tags: List<String> = emptyList(),
    val version: String = "1.0",
    val templateId: String? = null,
)
