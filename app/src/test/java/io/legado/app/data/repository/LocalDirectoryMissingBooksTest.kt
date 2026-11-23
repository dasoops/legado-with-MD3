package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LocalDirectoryMissingBooksTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LocalDirectoryRepository

    @Before
    fun setUp() {
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            LocalDirectoryRepository(
                BookRepository(
                    database.bookDao,
                    database.bookChapterDao,
                    database,
                    ReadRecordRepository(database.readRecordDao, database, SettingsRepository()),
                ),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `刷新目录时移除当前目录下已被删除的文件`() = runBlocking {
        val removed = insertLocalBook("/Books/removed.txt")
        val kept = insertLocalBook("/Books/kept.txt")
        val outside = insertLocalBook("/Other/book.txt")

        val count =
            repository.removeMissingDirectoryBooks(
                groupId = GROUP_ID,
                rootUri = ROOT_URI,
                scannedBookUrls = setOf(kept.bookUrl, outside.bookUrl),
                scanComplete = true,
            )

        assertEquals(1, count)
        assertNull(database.bookDao.getBook(removed.bookUrl))
        assertEquals(0, database.bookChapterDao.getChapterCount(removed.bookUrl))
        assertNotNull(database.bookDao.getBook(kept.bookUrl))
        // 分组里的书不一定都来自当前目录, 不能因为不在本次扫描结果里就删掉.
        assertNotNull(database.bookDao.getBook(outside.bookUrl))
    }

    @Test
    fun `扫描不完整时不移除任何书籍`() = runBlocking {
        val book = insertLocalBook("/Books/book.txt")

        val count =
            repository.removeMissingDirectoryBooks(
                groupId = GROUP_ID,
                rootUri = ROOT_URI,
                scannedBookUrls = emptySet(),
                scanComplete = false,
            )

        assertEquals(0, count)
        assertNotNull(database.bookDao.getBook(book.bookUrl))
    }

    @Test
    fun `大书库刷新时不读取完整书籍避免 CursorWindow 溢出`() = runBlocking {
        // 300 本 x 8KB 简介(约 2.4MB)超过默认 CursorWindow; 刷新只应读取 bookUrl.
        val bigIntro = "x".repeat(8 * 1024)
        val books =
            (0 until 300).map { index ->
                Book(
                    bookUrl = "/Books/big-$index.txt",
                    name = "big-$index",
                    group = GROUP_ID,
                    type = BookType.text or BookType.local,
                    intro = bigIntro,
                )
            }
        database.bookDao.insert(*books.toTypedArray())
        val missingUrl = books.first().bookUrl

        val count =
            repository.removeMissingDirectoryBooks(
                groupId = GROUP_ID,
                rootUri = ROOT_URI,
                scannedBookUrls = books.drop(1).mapTo(HashSet()) { it.bookUrl },
                scanComplete = true,
            )

        assertEquals(1, count)
        assertNull(database.bookDao.getBook(missingUrl))
    }

    @Test
    fun `目录范围判断不把其他路径视为当前目录`() {
        assertTrue(isUnderDirectoryRoot(ROOT_URI, "/Books/a.txt"))
        assertTrue(isUnderDirectoryRoot(ROOT_URI, "/Books/sub/a.txt"))
        assertFalse(isUnderDirectoryRoot(ROOT_URI, "/Other/a.txt"))
        // 前缀相同的兄弟目录不能被当作子目录.
        assertFalse(isUnderDirectoryRoot(ROOT_URI, "/BooksExtra/a.txt"))
    }

    private fun insertLocalBook(bookUrl: String): Book {
        val book =
            Book(
                bookUrl = bookUrl,
                name = bookUrl.substringAfterLast('/'),
                group = GROUP_ID,
                type = BookType.text or BookType.local,
            )
        database.bookDao.insert(book)
        database.bookChapterDao.insert(BookChapter(url = "chapter:$bookUrl", bookUrl = bookUrl))
        return book
    }

    private companion object {
        const val GROUP_ID = 1L
        const val ROOT_URI = "file:///Books"
    }
}
