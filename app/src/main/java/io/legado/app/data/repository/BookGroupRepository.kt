package io.legado.app.data.repository

import io.legado.app.domain.model.BookTags
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.entities.BookGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

class BookGroupRepository(
    private val bookGroupDao: BookGroupDao,
    private val bookRepository: BookRepository,
) {

    fun flowAll(): Flow<List<BookGroup>> {
        return combine(
            bookGroupDao.flowAll(),
            bookRepository.flowTagNames(),
            bookRepository.flowDirectoryTagNames()
        ) { groups, tags, directoryTags ->
            val liveTagIds = tags.mapTo(HashSet()) { BookTags.groupId(it) }
            // 标签分组默认按书籍标签实时生成; 用户调整过显示/排序后会落库, 此时以落库状态为准.
            val persisted = groups.filter { group ->
                group.isLocalDirectory || group.groupId == BookGroup.IdAll ||
                    (group.isTag && group.groupId in liveTagIds)
            }.sortedBy { it.order }
            val persistedIds = persisted.mapTo(HashSet()) { it.groupId }
            val generated = BookTags.builtInGroupTags.map { tag ->
                val groupId = BookTags.groupId(tag)
                if (groupId in persistedIds) null else BookGroup(
                    groupId = groupId,
                    groupName = tag,
                    show = false
                )
            }
            persisted + generated.filterNotNull()
        }
    }

    fun flowSelect(): Flow<List<BookGroup>> {
        return bookGroupDao.flowSelect()
    }

    fun flowShow(): Flow<List<BookGroup>> {
        return flowAll().map { groups -> groups.filter { it.show } }
    }

    suspend fun upsert(vararg bookGroup: BookGroup) {
        bookGroupDao.upsert(*bookGroup)
    }

    suspend fun insert(vararg bookGroup: BookGroup) {
        bookGroupDao.insert(*bookGroup)
    }

    suspend fun delete(vararg bookGroup: BookGroup) {
        bookGroupDao.delete(*bookGroup)
    }

    suspend fun getUnusedId(): Long {
        return bookGroupDao.getUnusedId()
    }

    fun getMaxOrder(): Int {
        return bookGroupDao.maxOrder
    }

    suspend fun getByID(id: Long): BookGroup? {
        return if (id < -100 && id != Long.MIN_VALUE) {
            flowAll().first().firstOrNull { it.groupId == id }
        } else bookGroupDao.getByID(id)
    }

    suspend fun getIdsSum(): Long {
        return bookGroupDao.idsSum
    }

    suspend fun getGroupNames(id: Long): List<String> {
        return bookGroupDao.getGroupNames(id).distinct()
    }

    suspend fun clearCover(groupId: Long) {
        bookGroupDao.clearCover(groupId)
    }
}
