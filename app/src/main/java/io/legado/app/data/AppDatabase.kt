package io.legado.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookContentProcessDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.dao.BookMarkingDao
import io.legado.app.data.dao.BookmarkDao
import io.legado.app.data.dao.CacheDao
import io.legado.app.data.dao.CookieDao
import io.legado.app.data.dao.ExactChapterPageCountDao
import io.legado.app.data.dao.HighlightRuleDao
import io.legado.app.data.dao.HighlightTagRuleDao
import io.legado.app.data.dao.KeyboardAssistsDao
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.dao.ReplaceRuleDao
import io.legado.app.data.dao.SearchContentHistoryDao
import io.legado.app.data.dao.ServerDao
import io.legado.app.data.dao.TxtTocRuleDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookMarking
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.Cookie
import io.legado.app.data.entities.ExactChapterPageCountEntity
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.entities.HighlightTagRule
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.SearchContentHistory
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.help.DefaultData
import java.util.Locale
import org.intellij.lang.annotations.Language
import splitties.init.appCtx

val appDb by lazy {
    Room.databaseBuilder(appCtx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
        .fallbackToDestructiveMigrationFrom(false, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        .addMigrations(*DatabaseMigrations.migrations)
        .allowMainThreadQueries()
        .addCallback(AppDatabase.dbCallback)
        .build()
}

@Database(
    version = 113,
    exportSchema = true,
    entities = [
        Book::class, BookGroup::class, BookChapter::class,
        ReplaceRule::class, Cookie::class,
        Bookmark::class, ReadRecordDetail::class, ReadRecordSession::class,
        TxtTocRule::class, ReadRecord::class, Cache::class,
        KeyboardAssist::class, Server::class,
        SearchContentHistory::class,
        HighlightRule::class, HighlightTagRule::class,
        BookContentProcess::class,
        ExactChapterPageCountEntity::class,
        BookMarking::class,
    ],
    autoMigrations = [
        AutoMigration(from = 43, to = 44),
        AutoMigration(from = 44, to = 45),
        AutoMigration(from = 45, to = 46),
        AutoMigration(from = 46, to = 47),
        AutoMigration(from = 47, to = 48),
        AutoMigration(from = 48, to = 49),
        AutoMigration(from = 49, to = 50),
        AutoMigration(from = 50, to = 51),
        AutoMigration(from = 51, to = 52),
        AutoMigration(from = 52, to = 53),
        AutoMigration(from = 53, to = 54),
        AutoMigration(from = 54, to = 55, spec = DatabaseMigrations.Migration_54_55::class),
        AutoMigration(from = 55, to = 56),
        AutoMigration(from = 56, to = 57),
        AutoMigration(from = 57, to = 58),
        AutoMigration(from = 58, to = 59),
        AutoMigration(from = 59, to = 60),
        AutoMigration(from = 60, to = 61),
        AutoMigration(from = 61, to = 62),
        AutoMigration(from = 62, to = 63),
        AutoMigration(from = 63, to = 64),
        AutoMigration(from = 64, to = 65, spec = DatabaseMigrations.Migration_64_65::class),
        AutoMigration(from = 65, to = 66),
        AutoMigration(from = 66, to = 67),
        AutoMigration(from = 67, to = 68),
        AutoMigration(from = 68, to = 69),
        AutoMigration(from = 69, to = 70),
        AutoMigration(from = 70, to = 71),
        AutoMigration(from = 71, to = 72),
        AutoMigration(from = 72, to = 73),
        AutoMigration(from = 73, to = 74),
        AutoMigration(from = 74, to = 75),
        AutoMigration(from = 75, to = 76),
        AutoMigration(from = 76, to = 77),
        AutoMigration(from = 77, to = 78),
        AutoMigration(from = 78, to = 79),
        AutoMigration(from = 79, to = 80),
        AutoMigration(from = 80, to = 81),
        AutoMigration(from = 81, to = 82),
        AutoMigration(from = 82, to = 83),
        AutoMigration(from = 83, to = 84),
        AutoMigration(from = 84, to = 85),
        AutoMigration(from = 85, to = 86),
        AutoMigration(from = 86, to = 87),
        AutoMigration(from = 87, to = 88),
        AutoMigration(from = 88, to = 89),
        AutoMigration(from = 89, to = 90),
        AutoMigration(from = 90, to = 91),
        AutoMigration(from = 91, to = 92),
        AutoMigration(from = 92, to = 93),
        AutoMigration(from = 93, to = 94),
        AutoMigration(from = 94, to = 95),
        AutoMigration(from = 95, to = 96),
        AutoMigration(from = 96, to = 97),
        AutoMigration(from = 97, to = 98),
        AutoMigration(from = 100, to = 101, spec = DatabaseMigrations.Migration_100_101::class),
        // book_marks 新表：Room AutoMigration 支持新增表，自动 CREATE TABLE
        AutoMigration(from = 101, to = 102),
        // httpTTS 新增可空列 speed（源级语速）
        AutoMigration(from = 103, to = 104),
        AutoMigration(from = 104, to = 105),
        AutoMigration(from = 106, to = 107, spec = DatabaseMigrations.Migration_106_107::class),
        AutoMigration(from = 107, to = 108, spec = DatabaseMigrations.Migration_107_108::class),
        AutoMigration(from = 108, to = 109, spec = DatabaseMigrations.Migration_108_109::class),
        // book_groups 新增可空列 pattern(高级分组正则)
        AutoMigration(from = 109, to = 110),
    ],
)
abstract class AppDatabase : RoomDatabase() {

    abstract val bookDao: BookDao
    abstract val bookGroupDao: BookGroupDao
    abstract val bookChapterDao: BookChapterDao
    abstract val bookContentProcessDao: BookContentProcessDao
    abstract val replaceRuleDao: ReplaceRuleDao
    abstract val bookmarkDao: BookmarkDao
    abstract val bookMarkingDao: BookMarkingDao
    abstract val cookieDao: CookieDao
    abstract val txtTocRuleDao: TxtTocRuleDao
    abstract val readRecordDao: ReadRecordDao
    abstract val cacheDao: CacheDao
    abstract val exactChapterPageCountDao: ExactChapterPageCountDao
    abstract val keyboardAssistsDao: KeyboardAssistsDao
    abstract val serverDao: ServerDao
    abstract val searchContentHistoryDao: SearchContentHistoryDao
    abstract val highlightRuleDao: HighlightRuleDao
    abstract val highlightTagRuleDao: HighlightTagRuleDao

    companion object {

        const val DATABASE_NAME = "legado.db"

        const val BOOK_TABLE_NAME = "books"

        val dbCallback = object : Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                db.setLocale(Locale.CHINESE)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                @Language("sql")
                val insertBookGroupAllSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdAll}, '全部', -10, 1
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdAll})
                """.trimIndent()
                db.execSQL(insertBookGroupAllSql)
                db.query("select * from keyboardAssists order by serialNo").use {
                    if (it.count == 0) {
                        DefaultData.keyboardAssists.forEach { keyboardAssist ->
                            val contentValues = ContentValues().apply {
                                put("type", keyboardAssist.type)
                                put("key", keyboardAssist.key)
                                put("value", keyboardAssist.value)
                                put("serialNo", keyboardAssist.serialNo)
                            }
                            db.insert(
                                "keyboardAssists",
                                SQLiteDatabase.CONFLICT_REPLACE,
                                contentValues,
                            )
                        }
                    }
                }
            }
        }
    }
}
