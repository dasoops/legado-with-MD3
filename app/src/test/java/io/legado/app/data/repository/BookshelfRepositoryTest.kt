package io.legado.app.data.repository

import io.legado.app.ui.main.bookshelf.BookShelfItem
import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfRepositoryTest {
    private val repository = BookshelfRepository()

    private fun book(
        url: String,
        durChapterTime: Long = 0L,
        order: Int = 0,
    ) = BookShelfItem(
        bookUrl = url,
        name = url,
        author = "",
        origin = "",
        originName = "",
        coverUrl = null,
        customCoverUrl = null,
        durChapterTitle = null,
        durChapterTime = durChapterTime,
        durChapterPos = 0,
        latestChapterTitle = null,
        latestChapterTime = 0L,
        lastCheckCount = 0,
        totalChapterNum = 0,
        durChapterIndex = 0,
        type = 0,
        group = 0L,
        order = order,
    )

    @Test
    fun `按阅读时间倒序`() {
        val books = listOf(book("a", 100), book("b", 300), book("c", 200))

        val sorted = repository.sortBooks(books, sort = 0, sortOrder = 1)

        assertEquals(listOf("b", "c", "a"), sorted.map { it.bookUrl })
    }

    @Test
    fun `按阅读时间升序`() {
        val books = listOf(book("a", 100), book("b", 300), book("c", 200))

        val sorted = repository.sortBooks(books, sort = 0, sortOrder = 0)

        assertEquals(listOf("a", "c", "b"), sorted.map { it.bookUrl })
    }

    @Test
    fun `手动排序按 order`() {
        val books =
            listOf(
                book("a", order = 3),
                book("b", order = 1),
                book("c", order = 2),
            )

        val sorted = repository.sortBooks(books, sort = 3, sortOrder = 0)

        assertEquals(listOf("b", "c", "a"), sorted.map { it.bookUrl })
    }
}
