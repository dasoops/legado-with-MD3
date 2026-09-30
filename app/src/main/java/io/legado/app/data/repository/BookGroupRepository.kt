package io.legado.app.data.repository

import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.entities.BookGroup
import kotlinx.coroutines.flow.Flow

class BookGroupRepository(
    private val bookGroupDao: BookGroupDao,
) {
    fun flowAll(): Flow<List<BookGroup>> = bookGroupDao.flowAll()

    fun flowSelect(): Flow<List<BookGroup>> = bookGroupDao.flowSelect()

    fun flowShow(): Flow<List<BookGroup>> = bookGroupDao.flowShow()

    suspend fun upsert(vararg bookGroup: BookGroup) {
        bookGroupDao.upsert(*bookGroup)
    }

    suspend fun insert(vararg bookGroup: BookGroup) {
        bookGroupDao.insert(*bookGroup)
    }

    suspend fun delete(vararg bookGroup: BookGroup) {
        bookGroupDao.delete(*bookGroup)
    }

    suspend fun getUnusedId(): Long = bookGroupDao.getUnusedId()

    fun getMaxOrder(): Int = bookGroupDao.maxOrder

    suspend fun getByID(id: Long): BookGroup? = bookGroupDao.getByID(id)

    suspend fun getIdsSum(): Long = bookGroupDao.idsSum

    suspend fun getGroupNames(id: Long): List<String> = bookGroupDao.getGroupNames(id).distinct()

    suspend fun clearCover(groupId: Long) {
        bookGroupDao.clearCover(groupId)
    }
}
