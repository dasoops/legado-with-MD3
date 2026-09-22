package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.DatabaseMigrations
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.domain.model.BookTags
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BookTagsRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var books: BookRepository
    private lateinit var groups: BookGroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        books = BookRepository(db.bookDao, db.bookChapterDao, db)
        groups = BookGroupRepository(db.bookGroupDao, books)
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `批量添加合并标签且保持目录位和阅读信息`() = runBlocking {
        db.bookDao.insert(
            Book(bookUrl = "a", customTag = "原标签", group = 3, durChapterIndex = 2),
            Book(bookUrl = "b", customTag = "Shared", group = 4)
        )
        books.addTags(setOf("a", "b", "missing"), setOf(" Shared ", "新标签", "已读"))
        val a = db.bookDao.getBook("a")!!
        val b = db.bookDao.getBook("b")!!
        assertEquals(listOf("原标签", "Shared", "新标签"), BookTags.parse(a.customTag))
        assertEquals(listOf("Shared", "新标签"), BookTags.parse(b.customTag))
        assertEquals(3L, a.group)
        assertEquals(4L, b.group)
        assertEquals(2, a.durChapterIndex)
    }

    @Test
    fun `标签分组随书籍标签和阅读状态变化且不落库`() = runBlocking {
        db.bookGroupDao.insert(BookGroup(1, "目录", localDirectoryUri = "file:///Books"))
        db.bookDao.insert(Book(bookUrl = "a", customTag = "Shared", totalChapterNum = 10))
        assertTrue(groups.flowAll().first { list -> list.any { it.isLocalDirectory } }.any { it.isLocalDirectory })
        assertTrue(groups.flowAll().first { list -> list.any { it.groupName == "Shared" && it.isTag } }
            .any { it.groupName == "Shared" && it.isTag })
        assertEquals(1, books.flowBookShelfByGroup(BookTags.groupId("Shared")).first().size)
        assertTrue(groups.flowAll().first { list -> list.any { it.groupName == BookTags.UNREAD } }
            .any { it.groupName == BookTags.UNREAD })
        db.bookDao.update(db.bookDao.getBook("a")!!.copy(customTag = null, durChapterIndex = 9))
        val updated = groups.flowAll().first { list ->
            list.any { it.groupName == BookTags.READ } &&
                list.none { it.groupName == "Shared" || it.groupName == BookTags.UNREAD }
        }
        assertFalse(updated.any { it.groupName == "Shared" || it.groupName == BookTags.UNREAD })
        assertTrue(updated.any { it.groupName == BookTags.READ })
        assertEquals(listOf("目录"), db.bookGroupDao.all.map { it.groupName })
    }

    @Test
    fun `标签分组的显示与排序可持久化`() = runBlocking {
        db.bookGroupDao.insert(BookGroup(BookGroup.IdAll, "全部", order = -10))
        db.bookDao.insert(Book(bookUrl = "a", customTag = "Shared", durChapterIndex = 1))
        val tagId = BookTags.groupId("Shared")
        val tag = groups.flowAll().first { list -> list.any { it.groupId == tagId } }
            .first { it.groupId == tagId }

        groups.upsert(tag.copy(show = false, order = 5))
        assertFalse(groups.flowShow().first().any { it.groupId == tagId })
        assertTrue(db.bookGroupDao.all.any { it.groupId == tagId && !it.show })

        groups.upsert(tag.copy(show = true, order = -9))
        assertEquals(
            listOf(BookGroup.IdAll, tagId),
            groups.flowAll().first()
                .map { it.groupId }
                .filter { it == BookGroup.IdAll || it == tagId }
        )
    }

    @Test
    fun `迁移只清理旧分组和规则并保留书籍标签目录`() {
        db.bookGroupDao.insert(
            BookGroup(-1, "全部"), BookGroup(-23, "连载已读"),
            BookGroup(1, "手动"), BookGroup(2, "Books", localDirectoryUri = "file:///Books")
        )
        db.bookDao.insert(Book(bookUrl = "a", customTag = "连载,旧标签", kind = "完本", group = 3))
        runBlocking { db.tagGroupRuleDao.insert(TagGroupRule(pattern = "连载", groupName = "手动")) }
        DatabaseMigrations.Migration_107_108().onPostMigrate(db.openHelper.writableDatabase)
        assertEquals(setOf(-1L, 2L), db.bookGroupDao.all.map { it.groupId }.toSet())
        assertTrue(db.tagGroupRuleDao.getAll().isEmpty())
        val book = db.bookDao.getBook("a")!!
        assertEquals(2L, book.group)
        assertEquals("连载,旧标签", book.customTag)
        assertEquals("完本", book.kind)
    }
    @Test
    fun `批量添加失败时事务回滚`() = runBlocking {
        db.bookDao.insert(Book(bookUrl = "a", customTag = "原标签"), Book(bookUrl = "b"))
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_tag BEFORE UPDATE OF customTag ON books
            WHEN NEW.bookUrl = 'b' BEGIN SELECT RAISE(ABORT, 'test failure'); END
        """.trimIndent())
        assertTrue(runCatching { books.addTags(linkedSetOf("a", "b"), setOf("新标签")) }.isFailure)
        assertEquals("原标签", db.bookDao.getBook("a")!!.customTag)
        assertNull(db.bookDao.getBook("b")!!.customTag)
    }

    @Test
    fun `非书架记录不生成标签页`() = runBlocking {
        db.bookDao.insert(Book(bookUrl = "preview", customTag = "预览", type = BookType.text or BookType.notShelf))
        assertTrue(books.flowTagNames().first().isEmpty())
        assertTrue(books.flowBookShelfByGroup(BookTags.groupId("预览")).first().isEmpty())
    }

    @Test
    fun `标签增长超过旧长度限制后仍完整保留`() = runBlocking {
        val original = (1..300).joinToString(",") { "分类$it" }
        db.bookDao.insert(Book(bookUrl = "a", customTag = original))
        books.addTags(setOf("a"), setOf("新标签"))
        assertEquals("$original,新标签", db.bookDao.getBook("a")!!.customTag)
    }

    @Test
    fun `详情页分组名称去重`() = runBlocking {
        db.bookGroupDao.insert(
            BookGroup(1, "Books", localDirectoryUri = "content://books"),
            BookGroup(2, "Books", localDirectoryUri = "content://books")
        )
        assertEquals(listOf("Books"), groups.getGroupNames(3L))
    }

}
