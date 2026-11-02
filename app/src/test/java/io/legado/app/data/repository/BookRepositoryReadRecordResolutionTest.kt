package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 阅读记录身份与 books 表原始身份不一致时的解析回归测试。 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BookRepositoryReadRecordResolutionTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: BookRepository

    @Before
    fun setUp() {
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            BookRepository(
                database.bookDao,
                database.bookChapterDao,
                database,
                ReadRecordRepository(database.readRecordDao, database, SettingsRepository()),
            )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `resolves book whose name keeps surrounding whitespace`() = runBlocking {
        database.bookDao.insert(Book(bookUrl = "u1", name = " 乖巧女友与学弟 ", author = "全本完结版"))

        val book = repository.resolveBookForReadRecord("乖巧女友与学弟", "全本完结版")

        assertEquals("u1", book?.bookUrl)
    }

    @Test
    fun `blank author record does not fall back to authored book`() = runBlocking {
        database.bookDao.insert(Book(bookUrl = "u1", name = "某书", author = "真实作者"))

        assertNull(repository.resolveBookForReadRecord("某书", ""))
    }

    @Test
    fun `prefers exact identity when several books share the name`() = runBlocking {
        database.bookDao.insert(Book(bookUrl = "u1", name = "某书", author = "作者甲"))
        database.bookDao.insert(Book(bookUrl = "u2", name = "某书", author = "作者乙"))

        val book = repository.resolveBookForReadRecord("某书", "作者乙")

        assertEquals("u2", book?.bookUrl)
    }

    @Test
    fun `returns null when record identity matches no book`() = runBlocking {
        database.bookDao.insert(Book(bookUrl = "u1", name = "另一本书", author = ""))

        assertNull(repository.resolveBookForReadRecord("某书", ""))
    }
}
