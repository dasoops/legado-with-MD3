package io.legado.app.domain.model

data class LocalDirectoryEntry(
    val uri: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val lastModified: Long,
)

data class LocalBookProgress(
    val bookUrl: String,
    val durChapterIndex: Int,
    val totalChapterNum: Int,
    val durChapterTitle: String?,
)
