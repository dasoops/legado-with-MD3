package io.legado.app.data.repository

import androidx.room.withTransaction
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.gateway.BookGroupMutationGateway
import io.legado.app.domain.model.BookTags
import io.legado.app.domain.model.BookGroupUpdate
import io.legado.app.domain.model.NewBookGroup

class BookGroupMutationRepository(
    private val database: AppDatabase,
) : BookGroupMutationGateway {

    override suspend fun addGroup(group: NewBookGroup) {
        database.withTransaction {
            val groupDao = database.bookGroupDao
            val groupId = if (group.isTag) BookTags.groupId(group.groupName) else groupDao.getUnusedId()
            val bookGroup = BookGroup(
                groupId = groupId,
                groupName = group.groupName,
                cover = group.cover,
                bookSort = group.bookSort,
                enableRefresh = group.enableRefresh,
                isPrivate = group.isPrivate,
                order = groupDao.maxOrder.plus(1),
                localDirectoryUri = group.localDirectoryUri,
            )

            if (!group.isTag && groupDao.getByID(groupId) == null) {
                database.bookDao.removeGroup(groupId)
            }
            groupDao.insert(bookGroup)
        }
    }

    override suspend fun saveGroup(bookGroup: BookGroupUpdate) {
        database.withTransaction {
            database.bookGroupDao.update(bookGroup.toEntity())
        }
    }

    override suspend fun deleteGroup(groupId: Long) {
        database.withTransaction {
            val group = database.bookGroupDao.getByID(groupId) ?: return@withTransaction
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
        bookSort = bookSort,
        isPrivate = isPrivate,
        localDirectoryUri = localDirectoryUri,
    )
}
