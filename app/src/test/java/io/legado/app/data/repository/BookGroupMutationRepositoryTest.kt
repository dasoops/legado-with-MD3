package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.model.BookGroupUpdate
import io.legado.app.domain.model.BookTags
import io.legado.app.domain.model.NewBookGroup
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BookGroupMutationRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: BookGroupMutationRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = BookGroupMutationRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `新增分组后分组落库`() = runBlocking {
        repository.addGroup(
            NewBookGroup(
                groupName = "Fantasy",
                bookSort = -1,
                enableRefresh = true,
                isPrivate = false,
                cover = null,
            )
        )

        val group = database.bookGroupDao.all.single()
        assertEquals("Fantasy", group.groupName)
        assertEquals(1L, group.groupId)
    }

    @Test
    fun `新增标签分组使用标签分组位`() = runBlocking {
        repository.addGroup(
            NewBookGroup(
                groupName = "Shared",
                bookSort = -1,
                enableRefresh = true,
                isPrivate = false,
                cover = null,
                isTag = true,
            )
        )

        assertEquals(BookTags.groupId("Shared"), database.bookGroupDao.all.single().groupId)
    }

    @Test
    fun `保存分组更新分组信息`() = runBlocking {
        database.bookGroupDao.insert(BookGroup(groupId = 1L, groupName = "Fantasy"))

        repository.saveGroup(
            BookGroupUpdate(
                groupId = 1L,
                groupName = "Renamed",
                cover = "cover.png",
                order = 5,
                enableRefresh = false,
                show = false,
                bookSort = 2,
                isPrivate = true,
            )
        )

        val group = database.bookGroupDao.getByID(1L)!!
        assertEquals("Renamed", group.groupName)
        assertEquals("cover.png", group.cover)
        assertEquals(5, group.order)
        assertEquals(false, group.enableRefresh)
        assertEquals(false, group.show)
        assertEquals(2, group.bookSort)
        assertEquals(true, group.isPrivate)
    }

    @Test
    fun `删除分组时清除书籍分组位`() = runBlocking {
        val group = BookGroup(groupId = 1L, groupName = "Fantasy")
        val book = Book(
            bookUrl = "book-1",
            name = "Book",
            author = "Author",
            group = group.groupId,
        )
        database.bookGroupDao.insert(group)
        database.bookDao.insert(book)

        repository.deleteGroup(group.groupId)

        assertTrue(database.bookGroupDao.all.isEmpty())
        assertEquals(0L, database.bookDao.getBook(book.bookUrl)?.group)
    }

    @Test
    fun `新增分组写入失败时回滚`() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_group_insert
            BEFORE INSERT ON book_groups
            BEGIN
                SELECT RAISE(ABORT, 'forced failure');
            END
            """.trimIndent()
        )

        val result = runCatching {
            repository.addGroup(
                NewBookGroup(
                    groupName = "Fantasy",
                    bookSort = -1,
                    enableRefresh = true,
                    isPrivate = false,
                    cover = null,
                )
            )
        }

        assertTrue(result.isFailure)
        assertTrue(database.bookGroupDao.all.isEmpty())
    }
}
