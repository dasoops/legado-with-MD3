package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.constant.BookType
import io.legado.app.data.AppDatabase
import io.legado.app.data.DatabaseMigrations
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.domain.model.BookTags
import kotlinx.coroutines.flow.first
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
class BookTagsRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var books: BookRepository
    private lateinit var groups: BookGroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        books = BookRepository(db.bookDao, db.bookChapterDao, db)
        groups = BookGroupRepository(db.bookGroupDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `批量添加合并标签且保持目录位和阅读信息`() = runBlocking {
        db.bookDao.insert(
            Book(bookUrl = "a", customTag = "原标签", group = 3, durChapterIndex = 2),
            Book(bookUrl = "b", customTag = "Shared", group = 4),
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
    fun `书籍标签不再自动生成标签分组`() = runBlocking {
        db.bookGroupDao.insert(BookGroup(1, "目录", localDirectoryUri = "file:///Books"))
        db.bookDao.insert(Book(bookUrl = "a", customTag = "Shared", totalChapterNum = 10))
        val all = groups.flowAll().first { list -> list.any { it.isLocalDirectory } }
        assertTrue(all.any { it.isLocalDirectory })
        assertFalse(all.any { it.isTag })
        // 标签分组改为手动维护, 但书籍标签与阅读状态仍可用于动态筛选书目
        assertEquals(1, books.flowBookShelfByGroup(BookTags.groupId("Shared")).first().size)
        assertTrue(books.flowTagNames().first().contains(BookTags.UNREAD))
        assertEquals(listOf("目录"), db.bookGroupDao.all.map { it.groupName })
    }

    @Test
    fun `手动标签分组的显示与排序可持久化`() = runBlocking {
        db.bookGroupDao.insert(BookGroup(BookGroup.IdAll, "全部", order = -10))
        db.bookDao.insert(Book(bookUrl = "a", customTag = "Shared", durChapterIndex = 1))
        val tagId = BookTags.groupId("Shared")
        groups.insert(BookGroup(tagId, "Shared", order = 5))
        assertTrue(groups.flowAll().first().any { it.groupId == tagId })

        groups.upsert(BookGroup(tagId, "Shared", order = 5, show = false))
        assertFalse(groups.flowShow().first().any { it.groupId == tagId })
        assertTrue(db.bookGroupDao.all.any { it.groupId == tagId && !it.show })

        groups.upsert(BookGroup(tagId, "Shared", order = -9, show = true))
        assertEquals(
            listOf(BookGroup.IdAll, tagId),
            groups.flowAll().first()
                .map { it.groupId }
                .filter { it == BookGroup.IdAll || it == tagId },
        )
    }

    @Test
    fun `迁移清理旧普通分组及无本地来源书籍`() {
        db.bookGroupDao.insert(
            BookGroup(-1, "全部"),
            BookGroup(-23, "连载已读"),
            BookGroup(BookTags.groupId("标签"), "标签"),
            BookGroup(1, "旧普通"),
            BookGroup(2, "Books", localDirectoryUri = "file:///Books"),
            BookGroup(4, "高级", pattern = "author=作者"),
        )
        db.bookDao.insert(
            Book(bookUrl = "old", group = 1),
            Book(bookUrl = "shared", group = 3),
            Book(bookUrl = "local", group = 2),
            Book(bookUrl = "advanced", author = "作者"),
            Book(bookUrl = "tag", customTag = "标签"),
        )
        val sqlite = db.openHelper.writableDatabase
        DatabaseMigrations.Migration_111_112().migrate(sqlite)

        assertEquals(
            setOf(-23L, -1L, BookTags.groupId("标签"), 2L, 4L),
            db.bookGroupDao.all.map { it.groupId }.toSet(),
        )
        assertNull(db.bookDao.getBook("old"))
        assertEquals(2L, db.bookDao.getBook("shared")?.group)
        assertNotNull(db.bookDao.getBook("local"))
        assertNotNull(db.bookDao.getBook("advanced"))
        assertNotNull(db.bookDao.getBook("tag"))
    }

    @Test
    fun `早期迁移清理普通分组且不依赖旧标签规则表`() {
        db.bookGroupDao.insert(
            BookGroup(1, "旧普通"),
            BookGroup(2, "Books", localDirectoryUri = "file:///Books"),
        )
        db.bookDao.insert(
            Book(bookUrl = "old", group = 1),
            Book(bookUrl = "shared", group = 3),
        )

        DatabaseMigrations.Migration_107_108().onPostMigrate(db.openHelper.writableDatabase)

        assertEquals(listOf(2L), db.bookGroupDao.all.map { it.groupId })
        assertNull(db.bookDao.getBook("old"))
        assertEquals(2L, db.bookDao.getBook("shared")?.group)
    }

    @Test
    fun `批量添加失败时事务回滚`() = runBlocking {
        db.bookDao.insert(Book(bookUrl = "a", customTag = "原标签"), Book(bookUrl = "b"))
        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_tag BEFORE UPDATE OF customTag ON books
            WHEN NEW.bookUrl = 'b' BEGIN SELECT RAISE(ABORT, 'test failure'); END
            """.trimIndent(),
        )
        assertTrue(runCatching { books.addTags(linkedSetOf("a", "b"), setOf("新标签")) }.isFailure)
        assertEquals("原标签", db.bookDao.getBook("a")!!.customTag)
        assertNull(db.bookDao.getBook("b")!!.customTag)
    }

    @Test
    fun `刷新目录标签摘除旧目录标签并保留用户标签`() = runBlocking {
        val directoryRepository = LocalDirectoryRepository(books)
        val book = Book(bookUrl = "a", customTag = "层级,Novel,用户标签")
        book.config.directoryTags = listOf("层级", "Novel")
        db.bookDao.insert(book)

        directoryRepository.syncDirectoryTags(db.bookDao.getBook("a")!!, emptyList())

        val updated = db.bookDao.getBook("a")!!
        assertEquals(listOf("用户标签"), BookTags.parse(updated.customTag))
        assertEquals(emptyList<String>(), updated.config.directoryTags)
    }

    @Test
    fun `刷新目录标签可补入新的相对子目录标签`() = runBlocking {
        val directoryRepository = LocalDirectoryRepository(books)
        db.bookDao.insert(Book(bookUrl = "a", customTag = "用户标签"))

        directoryRepository.syncDirectoryTags(db.bookDao.getBook("a")!!, listOf("ggg"))

        val updated = db.bookDao.getBook("a")!!
        assertEquals(listOf("用户标签", "ggg"), BookTags.parse(updated.customTag))
        assertEquals(listOf("ggg"), updated.config.directoryTags)
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
    fun `高级分组始终出现在分组列表`() = runBlocking {
        db.bookGroupDao.insert(BookGroup(5L, "科幻", pattern = "author=刘慈欣"))
        val group = groups.flowAll().first { list -> list.any { it.groupId == 5L } }
        assertTrue(group.any { it.groupId == 5L && it.isAdvanced })
    }

    @Test
    fun `高级分组按作者正则动态匹配书目`() = runBlocking {
        db.bookDao.insert(
            Book(bookUrl = "a", name = "三体", author = "刘慈欣", customTag = "科幻,宇宙"),
            Book(bookUrl = "b", name = "诡秘之主", author = "爱潜水的乌贼", customTag = "奇幻,冒险"),
            Book(bookUrl = "c", name = "庆余年", author = "猫腻", customTag = "历史,权谋"),
        )
        val group = BookGroup(5L, "科幻", pattern = "author=刘慈欣")

        val matched = books.flowBookShelfByGroup(group)
            .first { list -> list.isNotEmpty() }
        assertEquals(listOf("三体"), matched.map { it.name })
    }

    @Test
    fun `高级分组按标签正则动态匹配书目`() = runBlocking {
        db.bookDao.insert(
            Book(bookUrl = "a", name = "三体", author = "刘慈欣", customTag = "科幻,宇宙"),
            Book(bookUrl = "c", name = "庆余年", author = "猫腻", customTag = "历史,权谋"),
        )
        val group = BookGroup(6L, "历史", pattern = "tags=.*历史.*")

        val matched = books.flowBookShelfByGroup(group)
            .first { list -> list.isNotEmpty() }
        assertEquals(listOf("庆余年"), matched.map { it.name })
    }

    @Test
    fun `高级分组按本地目录名与格式组合匹配`() = runBlocking {
        db.bookGroupDao.insert(BookGroup(1L, "韩轻", localDirectoryUri = "file:///books/韩轻"))
        db.bookDao.insert(
            Book(bookUrl = "a", name = "本地书", customTag = null, kind = "epub", group = 1L),
            Book(bookUrl = "b", name = "其他书", customTag = "韩轻", kind = "txt"),
        )
        val group = BookGroup(
            2L,
            "韩轻 EPUB",
            pattern = "(?i)(?=.*(?:^|\\t)tags=[^\\t]*韩轻)(?=.*(?:^|\\t)tags=[^\\t]*epub)",
        )

        val matched = books.flowBookShelfByGroup(group)
            .first { list -> list.isNotEmpty() }
        assertEquals(listOf("本地书"), matched.map { it.name })
    }

    @Test
    fun `高级分组非法正则返回空`() = runBlocking {
        db.bookDao.insert(Book(bookUrl = "a", name = "三体", author = "刘慈欣"))
        val group = BookGroup(7L, "坏正则", pattern = "[")

        assertTrue(books.flowBookShelfByGroup(group).first().isEmpty())
    }

    @Test
    fun `按分组 id 查询高级分组同样动态匹配`() = runBlocking {
        db.bookDao.insert(
            Book(bookUrl = "a", name = "三体", author = "刘慈欣"),
            Book(bookUrl = "c", name = "庆余年", author = "猫腻"),
        )
        db.bookGroupDao.insert(BookGroup(8L, "科幻", pattern = "author=刘慈欣"))

        val matched = books.flowBookShelfByGroup(8L)
            .first { list -> list.isNotEmpty() }
        assertEquals(listOf("三体"), matched.map { it.name })
    }

    @Test
    fun `详情页分组名称去重`() = runBlocking {
        db.bookGroupDao.insert(
            BookGroup(1, "Books", localDirectoryUri = "content://books"),
            BookGroup(2, "Books", localDirectoryUri = "content://books"),
        )
        assertEquals(listOf("Books"), groups.getGroupNames(3L))
    }
}
