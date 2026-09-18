package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.gateway.BookSearchGateway
import io.legado.app.domain.model.BookSearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

interface SearchRepository {
    val enabledGroups: Flow<List<String>>
    val enabledSources: Flow<List<BookSourcePart>>

    suspend fun getEnableHasCover(name: String, author: String): List<SearchBook>
    suspend fun getSearchBook(bookUrl: String): SearchBook?
    suspend fun saveSearchBooks(books: List<SearchBook>)
    suspend fun saveSearchBook(book: SearchBook)
    suspend fun deleteSearchBooks(books: List<SearchBook>)
    suspend fun getBookSourcePart(sourceUrl: String): BookSourcePart?
    suspend fun getBookSource(sourceUrl: String): BookSource?
}

class SearchRepositoryImpl(
    private val appDb: AppDatabase,
) : SearchRepository, BookSearchGateway {

    override val enabledGroups: Flow<List<String>> = appDb.bookSourceDao.flowEnabledGroups()
    override val enabledSources: Flow<List<BookSourcePart>> = appDb.bookSourceDao.flowEnabled()

    override suspend fun getBookSourceParts(scope: BookSearchScope): List<BookSourcePart> =
        withContext(Dispatchers.IO) {
            val selectedSources = linkedSetOf<BookSourcePart>()
            when {
                scope.isAll -> selectedSources.addAll(appDb.bookSourceDao.allEnabledPart)
                scope.isSource -> scope.sourceUrls.forEach { sourceUrl ->
                    appDb.bookSourceDao.getBookSourcePart(sourceUrl)?.let { selectedSources.add(it) }
                }

                else -> scope.groupNames.forEach { groupName ->
                    selectedSources.addAll(appDb.bookSourceDao.getEnabledPartByGroup(groupName))
                }
            }

            if (selectedSources.isEmpty()) {
                appDb.bookSourceDao.allEnabledPart
            } else {
                selectedSources.toList().sortedBy { it.customOrder }
            }
        }

    override suspend fun getBookSource(sourceUrl: String) = withContext(Dispatchers.IO) {
        appDb.bookSourceDao.getBookSource(sourceUrl)
    }

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

    override suspend fun getBookSourcePart(sourceUrl: String): BookSourcePart? =
        withContext(Dispatchers.IO) {
            appDb.bookSourceDao.getBookSourcePart(sourceUrl)
        }

}
