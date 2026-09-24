package io.legado.app.domain.model

data class NewBookGroup(
    val groupName: String,
    val bookSort: Int,
    val enableRefresh: Boolean,
    val isPrivate: Boolean,
    val cover: String?,
    val localDirectoryUri: String? = null,
    val isTag: Boolean = false,
)

data class BookGroupUpdate(
    val groupId: Long,
    val groupName: String,
    val cover: String?,
    val order: Int,
    val enableRefresh: Boolean,
    val show: Boolean,
    val bookSort: Int,
    val isPrivate: Boolean,
    val localDirectoryUri: String? = null,
)
