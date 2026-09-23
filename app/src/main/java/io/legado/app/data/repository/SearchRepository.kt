package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.SearchBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SearchRepository {
    suspend fun getEnableHasCover(name: String, author: String): List<SearchBook>
    suspend fun getSearchBook(bookUrl: String): SearchBook?
    suspend fun saveSearchBooks(books: List<SearchBook>)
    suspend fun saveSearchBook(book: SearchBook)
    suspend fun deleteSearchBooks(books: List<SearchBook>)
}

class SearchRepositoryImpl(
    private val appDb: AppDatabase,
) : SearchRepository {

    override suspend fun getEnableHasCover(name: String, author: String): List<SearchBook> =
        withContext(Dispatchers.IO) {
            appDb.searchBookDao.getEnableHasCover(name, author)
        }

    override suspend fun getSearchBook(bookUrl: String): SearchBook? =
        withContext(Dispatchers.IO) {
            appDb.searchBookDao.getSearchBook(bookUrl)
        }

    override suspend fun saveSearchBooks(books: List<SearchBook>): Unit =
        withContext(Dispatchers.IO) {
        if (books.isNotEmpty()) {
            appDb.searchBookDao.insert(books)
        }
    }

    override suspend fun saveSearchBook(book: SearchBook): Unit = withContext(Dispatchers.IO) {
        appDb.searchBookDao.insert(book)
    }

    override suspend fun deleteSearchBooks(books: List<SearchBook>): Unit =
        withContext(Dispatchers.IO) {
            if (books.isNotEmpty()) {
                appDb.searchBookDao.delete(*books.toTypedArray())
            }
        }

}
