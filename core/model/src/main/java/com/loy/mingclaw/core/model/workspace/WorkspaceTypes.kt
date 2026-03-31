package com.loy.mingclaw.core.model.workspace

import kotlinx.serialization.Serializable

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
)

data class ScanOptions(
    val recursive: Boolean = true,
    val includeHidden: Boolean = false,
    val maxDepth: Int = Int.MAX_VALUE,
) {
    companion object {
        val Default = ScanOptions()
    }
}

@Serializable
data class DirectoryInfo(
    val path: String,
    val name: String,
    val fileCount: Int,
    val directoryCount: Int,
    val totalSize: Long,
    val lastModified: Long,
    val isReadable: Boolean,
    val isWritable: Boolean,
)

@Serializable
sealed class FileTreeNode {
    abstract val path: String
    abstract val name: String

    @Serializable
    data class FileNode(
        override val path: String,
        override val name: String,
        val size: Long,
        val lastModified: Long,
    ) : FileTreeNode()

    @Serializable
    data class DirectoryNode(
        override val path: String,
        override val name: String,
        val children: List<FileTreeNode> = emptyList(),
    ) : FileTreeNode()
}

sealed class FileChangeEvent {
    data class Created(val path: String) : FileChangeEvent()
    data class Modified(val path: String) : FileChangeEvent()
    data class Deleted(val path: String) : FileChangeEvent()
    data class Moved(val from: String, val to: String) : FileChangeEvent()
}
