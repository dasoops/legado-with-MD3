package io.legado.app.data.repository

import androidx.room.withTransaction
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.gateway.BookGroupMutationGateway
import io.legado.app.domain.model.BookGroupUpdate
import io.legado.app.domain.model.BookTags
import io.legado.app.domain.model.NewBookGroup

class BookGroupMutationRepository(
    private val database: AppDatabase,
) : BookGroupMutationGateway {
    override suspend fun addGroup(group: NewBookGroup) {
        group.requireSupportedType()
        // 非法正则在写入前拦截, 避免落库后动态分组静默失效.
        group.pattern?.takeIf(String::isNotBlank)?.let(::Regex)
        database.withTransaction {
            val groupDao = database.bookGroupDao
            val groupId = if (group.isTag) BookTags.groupId(group.groupName) else groupDao.getUnusedId()
            val bookGroup =
                BookGroup(
                    groupId = groupId,
                    groupName = group.groupName,
                    cover = group.cover,
                    enableRefresh = group.enableRefresh,
                    isPrivate = group.isPrivate,
                    order = groupDao.maxOrder.plus(1),
                    localDirectoryUri = group.localDirectoryUri,
                    pattern = group.pattern,
                )

            if (!group.isTag && groupDao.getByID(groupId) == null) {
                database.bookDao.removeGroup(groupId)
            }
            groupDao.insert(bookGroup)
        }
    }

    override suspend fun saveGroup(bookGroup: BookGroupUpdate) {
        bookGroup.requireSupportedType()
        // 非法正则在写入前拦截, 避免落库后动态分组静默失效.
        bookGroup.pattern?.takeIf(String::isNotBlank)?.let(::Regex)
        database.withTransaction {
            database.bookGroupDao.update(bookGroup.toEntity())
        }
    }

    override suspend fun deleteGroup(groupId: Long) {
        database.withTransaction {
            val group = database.bookGroupDao.getByID(groupId) ?: return@withTransaction
            if (group.isLocalDirectory) {
                val remainingLocalDirectoryMask =
                    database.bookGroupDao.all
                        .asSequence()
                        .filter { it.isLocalDirectory && it.groupId != groupId }
                        .fold(0L) { mask, remainingGroup -> mask or remainingGroup.groupId }
                val booksToDelete =
                    database.bookDao.getLocalBooksOnlyInGroup(
                        groupId = groupId,
                        remainingLocalDirectoryMask = remainingLocalDirectoryMask,
                    )
                booksToDelete.forEach { book ->
                    database.bookChapterDao.delByBook(book.bookUrl)
                }
                if (booksToDelete.isNotEmpty()) {
                    database.bookDao.delete(*booksToDelete.toTypedArray())
                }
            }
            database.bookDao.removeGroup(groupId)
            database.bookGroupDao.delete(group)
        }
    }

    private fun BookGroupUpdate.toEntity() = BookGroup(
        groupId = groupId,
        groupName = groupName,
        cover = cover,
        order = order,
        enableRefresh = enableRefresh,
        show = show,
        isPrivate = isPrivate,
        localDirectoryUri = localDirectoryUri,
        pattern = pattern,
    )

    private fun NewBookGroup.requireSupportedType() {
        val isLocalDirectory = !localDirectoryUri.isNullOrBlank()
        val isAdvanced = !pattern.isNullOrBlank()
        require(
            if (isTag) {
                !isLocalDirectory && !isAdvanced
            } else {
                isLocalDirectory.xor(isAdvanced)
            },
        ) {
            "分组必须是标签、本地目录或高级分组"
        }
    }

    private fun BookGroupUpdate.requireSupportedType() {
        val isLocalDirectory = !localDirectoryUri.isNullOrBlank()
        val isAdvanced = !pattern.isNullOrBlank()
        require(
            groupId != 0L &&
                if (groupId < 0) {
                    !isLocalDirectory && !isAdvanced
                } else {
                    isLocalDirectory.xor(isAdvanced)
                },
        ) {
            "分组必须是标签、本地目录或高级分组"
        }
    }
}
