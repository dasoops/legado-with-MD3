package io.legado.app.data.repository

import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.gateway.ExploreBooksGateway
import io.legado.app.model.webBook.WebBook

class ExploreRepositoryImpl(
    private val appDb: AppDatabase
) : ExploreBooksGateway {

    override suspend fun getBookSource(sourceUrl: String): BookSource? {
        return appDb.bookSourceDao.getBookSource(sourceUrl)
    }

    override suspend fun exploreBooks(
        bookSource: BookSource,
        url: String,
        page: Int,
        key: String?
    ): List<SearchBook> {
        return WebBook.exploreBookSuspend(bookSource, url, page, key = key, isSearch = key != null)
    }

    override suspend fun saveSearchBooks(books: List<SearchBook>) {
        if (books.isNotEmpty()) {
            appDb.searchBookDao.insert(books)
        }
    }
}
