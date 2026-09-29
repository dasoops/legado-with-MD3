package io.legado.app.data.repository

import io.legado.app.domain.model.AdvancedGroup
import io.legado.app.domain.model.BookTags
import io.legado.app.data.AppDatabase
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.GroupBookCount
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.help.book.isLocal
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.main.bookshelf.BookShelfItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BookRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val appDb: AppDatabase,
) {
    fun flowBook(bookUrl: String): Flow<Book?> {
        return bookDao.flowGetBook(bookUrl)
    }

    fun flowChapters(bookUrl: String): Flow<List<BookChapter>> {
        return bookChapterDao.getChapterListFlow(bookUrl)
    }

    fun getAllBooks(): Flow<List<Book>> {
        return bookDao.flowAll()
    }

    suspend fun getBookCoverByNameAndAuthor(bookName: String, bookAuthor: String): String? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(bookName, bookAuthor)?.getDisplayCover()
        }
    }

    suspend fun getChapterTitle(bookName: String, bookAuthor: String, chapterIndex: Int): String? {
        return withContext(Dispatchers.IO) {
            val book = bookDao.getBook(bookName, bookAuthor)
            val bookUrl = book?.bookUrl
            if (bookUrl.isNullOrEmpty()) return@withContext null

            bookChapterDao.getChapterTitleByUrlAndIndex(bookUrl, chapterIndex)
        }
    }

    suspend fun getBook(bookUrl: String): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(bookUrl)
        }
    }

    suspend fun backfillLocalBookCoverIfMissing(bookUrl: String) {
        withContext(Dispatchers.IO) {
            val book = bookDao.getBook(bookUrl) ?: return@withContext
            if (!book.isLocal || !book.coverUrl.isNullOrBlank()) return@withContext
            LocalBook.upBookInfo(book)
            if (!book.coverUrl.isNullOrBlank()) {
                bookDao.update(book)
            }
        }
    }

    suspend fun getBook(name: String, author: String): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.getBook(name, author)
        }
    }

    suspend fun getShelfBookConflict(name: String, author: String): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.getShelfBookConflict(name, author)
        }
    }

    fun flowBookShelfByGroup(groupId: Long): Flow<List<BookShelfItem>> {
        return when {
            groupId < -100 && groupId != Long.MIN_VALUE -> bookDao.flowBookShelf().map { books ->
                books.filterNot { it.isNotShelf }.filter { book ->
                    BookTags.grouping(
                        book.customTag, book.kind, book.durChapterIndex, book.durChapterPos, book.totalChapterNum
                    ).any { BookTags.groupId(it) == groupId }
                }
            }
            // 高级分组 id 为正数且已落库, 需按正则动态匹配, 不能走位掩码查询.
            groupId > 0 -> flow {
                val group = appDb.bookGroupDao.getByID(groupId)
                emitAll(
                    if (group?.isAdvanced == true) flowBookShelfByGroup(group)
                    else bookDao.flowBookShelfByGroup(groupId)
                )
            }
            else -> bookDao.flowBookShelfByGroup(groupId)
        }
    }

    fun flowTagNames(): Flow<List<String>> = bookDao.flowBookShelf().map { books ->
        books.filterNot { it.isNotShelf }.flatMap { book ->
            BookTags.grouping(
                book.customTag, book.kind, book.durChapterIndex, book.durChapterPos, book.totalChapterNum
            )
        }.distinct().sorted()
    }

    fun flowBookShelfByGroup(group: BookGroup): Flow<List<BookShelfItem>> =
        when {
            group.isAdvanced -> {
                val regex = group.pattern?.takeIf(String::isNotBlank)
                    ?.let { runCatching { Regex(it) }.getOrNull() }
                if (regex == null) {
                    flowOf(emptyList())
                } else {
                    combine(bookDao.flowBookShelf(), appDb.bookGroupDao.flowAll()) { books, groups ->
                        val localGroupNames = groups
                            .filter { it.isLocalDirectory }
                            .associate { it.groupId to it.groupName }
                        books.filterNot { it.isNotShelf }.filter { book ->
                            regex.containsMatchIn(book.advancedGroupText(localGroupNames))
                        }
                    }
                }
            }
            group.isTag -> bookDao.flowBookShelf().map { books ->
                books.filterNot { it.isNotShelf }.filter { book ->
                    group.groupName in BookTags.grouping(
                        book.customTag, book.kind, book.durChapterIndex, book.durChapterPos, book.totalChapterNum
                    )
                }
            }
            else -> bookDao.flowBookShelfByGroup(group.groupId)
        }

    private fun BookShelfItem.advancedGroupText(localGroupNames: Map<Long, String> = emptyMap()) = AdvancedGroup.serialize(
        name = name,
        author = author,
        // 本地目录名不再写入书籍标签, 但高级分组仍需能按目录身份筛选.
        tags = (BookTags.grouping(
            customTag, kind, durChapterIndex, durChapterPos, totalChapterNum
        ) + localGroupNames.filterKeys { (group and it) != 0L }.values).distinct(),
    )

    suspend fun addTags(bookUrls: Set<String>, tags: Set<String>) = withContext(Dispatchers.IO) {
        val additions = BookTags.editable(tags)
        if (additions.isEmpty()) return@withContext
        appDb.runInTransaction {
            bookUrls.forEach { url ->
                val book = bookDao.getBook(url) ?: return@forEach
                val merged = (BookTags.parse(book.customTag) + additions).distinct()
                bookDao.updateCustomTag(url, merged.joinToString(","))
            }
        }
    }

    fun flowSystemGroupCounts(): Flow<List<GroupBookCount>> {
        return bookDao.flowSystemGroupCounts()
    }

    fun flowAllBookShelfCount(): Flow<Int> {
        return bookDao.flowAllBookShelfCount()
    }

    fun flowUserGroupBookCount(groupId: Long): Flow<Int> {
        return bookDao.flowUserGroupBookCount(groupId)
    }

    fun flowGroupPreview(groupId: Long): Flow<List<BookShelfItem>> {
        return bookDao.flowGroupPreview(groupId)
    }

    suspend fun getChapter(bookUrl: String, index: Int): BookChapter? {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapter(bookUrl, index)
        }
    }

    suspend fun getChapterCount(bookUrl: String): Int {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterCount(bookUrl)
        }
    }

    suspend fun getVolumeCount(bookUrl: String): Int {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getVolumeCount(bookUrl)
        }
    }

    suspend fun getChapters(bookUrl: String): List<BookChapter> {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterList(bookUrl)
        }
    }

    suspend fun getChapters(bookUrl: String, start: Int, end: Int): List<BookChapter> {
        return withContext(Dispatchers.IO) {
            bookChapterDao.getChapterList(bookUrl, start, end)
        }
    }

    suspend fun update(vararg book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.update(*book)
        }
    }

    suspend fun getMinOrder(): Int {
        return withContext(Dispatchers.IO) {
            bookDao.minOrder
        }
    }

    suspend fun insert(book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.insert(book)
        }
    }

    suspend fun insertChapters(vararg chapters: BookChapter) {
        withContext(Dispatchers.IO) {
            bookChapterDao.insert(*chapters)
        }
    }

    suspend fun getHasUpdateBooks(): List<Book> {
        return withContext(Dispatchers.IO) {
            bookDao.hasUpdateBooks
        }
    }

    suspend fun getAll(): List<Book> {
        return withContext(Dispatchers.IO) {
            bookDao.getAll()
        }
    }

    suspend fun getLastReadBook(): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.lastReadBook
        }
    }

    suspend fun replace(oldBook: Book, newBook: Book) {
        withContext(Dispatchers.IO) {
            bookDao.replace(oldBook, newBook)
        }
    }

    suspend fun delete(vararg book: Book) {
        withContext(Dispatchers.IO) {
            bookDao.delete(*book)
        }
    }

    suspend fun deleteChaptersByBook(bookUrl: String) {
        withContext(Dispatchers.IO) {
            bookChapterDao.delByBook(bookUrl)
        }
    }

    suspend fun replaceChaptersAndUpdateBook(book: Book, chapters: List<BookChapter>) {
        withContext(Dispatchers.IO) {
            appDb.runInTransaction {
                bookChapterDao.delByBook(book.bookUrl)
                bookChapterDao.insert(*chapters.toTypedArray())
                bookDao.update(book)
            }
        }
    }

}
