package io.legado.app.data.repository

import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.entities.BookGroup
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

class BookGroupRepository(
    private val bookGroupDao: BookGroupDao,
) {

    // 分组由用户(或本地目录/高级分组创建流程)显式落库, 不再从书籍标签实时生成,
    // 否则本地目录路径等标签会凭空变成一堆不可维护的标签分组.
    fun flowAll(): Flow<List<BookGroup>> {
        return bookGroupDao.flowAll()
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
        return bookGroupDao.getByID(id)
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
