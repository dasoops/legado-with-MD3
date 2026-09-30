package io.legado.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.DatabaseMigrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    // Room 2.8 的字符串构造器不会加载生成的自动迁移, 必须传数据库 Class.
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate50To113() = migrateFrom(50)

    @Test
    fun migrate82To113() = migrateFrom(82)

    @Test
    fun migrate98To113() = migrateFrom(98)

    @Test
    fun migrate102To113() = migrateFrom(102)

    @Test
    fun migrate105To113() = migrateFrom(105)

    @Test
    fun migrate107To113() = migrateFrom(107)

    @Test
    fun migrate110To113() = migrateFrom(110)

    @Test
    fun migrate111To113() = migrateFrom(111)

    @Test
    fun migrate112To113() = migrateFrom(112)

    private fun migrateFrom(version: Int) {
        val name = "migration-$version-to-113"
        val fixture = MigrationFixture(version)
        val before = helper.createDatabase(name, version).use { database ->
            fixture.insert(database)
            assertForeignKeyIntegrity(database)
            val snapshot = fixture.snapshot(database)
            fixture.deletedTables.intersect(snapshot.keys).forEach { table ->
                assertTrue("v$version 的待删表 $table 必须有数据", snapshot.getValue(table).isNotEmpty())
            }
            if (version == 112) {
                assertTrue(snapshot.keys.containsAll(fixture.deletedTables))
            }
            snapshot
        }

        helper.runMigrationsAndValidate(name, 113, true, *DatabaseMigrations.migrations).use { database ->
            assertEquals("113", MigrationFixture.rows(database, "PRAGMA user_version").single()["user_version"])
            assertFinalSchema(database, fixture)
            assertForeignKeyIntegrity(database)
            assertPreservedRows(database, fixture, before, version)
            assertChapterOrderAndProgress(database, fixture)
            assertStatistics(database, before, version)
        }
    }

    private fun assertFinalSchema(database: SupportSQLiteDatabase, fixture: MigrationFixture) {
        val objects = MigrationFixture.rows(
            database,
            "SELECT name FROM sqlite_master WHERE type IN ('table', 'view') " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata')",
        ).map { it.getValue("name") }.toSet()
        assertEquals(fixture.retainedTables, objects)
        assertFalse("书源视图必须删除", "book_sources_part" in objects)

        val processColumns = MigrationFixture.rows(database, "PRAGMA table_info(`book_content_processes`)")
            .map { it.getValue("name") }.toSet()
        assertEquals(
            setOf(
                "id", "bookUrl", "chapterIndex", "kind", "stage", "target", "anchorJson",
                "actionJson", "styleJson", "source", "enabled", "sortOrder", "status",
                "schemaVersion", "createdAt", "updatedAt",
            ),
            processColumns,
        )
        val indices = MigrationFixture.rows(database, "PRAGMA index_list(`book_content_processes`)")
            .filter { it["origin"] != "pk" }
        assertEquals(
            setOf(
                "index_book_content_processes_bookUrl_chapterIndex_enabled_sortOrder",
                "index_book_content_processes_bookUrl_kind",
            ),
            indices.map { it.getValue("name") }.toSet(),
        )

        val foreignKeys = mapOf("chapters" to "bookUrl", "exact_chapter_page_counts" to "bookId")
        fixture.retainedTables.forEach { table ->
            val keys = MigrationFixture.rows(database, "PRAGMA foreign_key_list(`$table`)")
            val childColumn = foreignKeys[table]
            if (childColumn == null) {
                assertTrue("$table 出现意外外键", keys.isEmpty())
            } else {
                assertEquals("$table 外键数量", 1, keys.size)
                val key = keys.single()
                assertEquals("books", key["table"])
                assertEquals(childColumn, key["from"])
                assertEquals("bookUrl", key["to"])
                assertEquals("CASCADE", key["on_delete"])
                assertEquals("NO ACTION", key["on_update"])
            }
        }
    }

    private fun assertPreservedRows(
        database: SupportSQLiteDatabase,
        fixture: MigrationFixture,
        before: Map<String, List<Map<String, String?>>>,
        version: Int,
    ) {
        val after = fixture.snapshot(database)
        fixture.retainedTables.filterNot { it in fixture.statisticsTables }.forEach { table ->
            val originalRows = before[table].orEmpty()
            val expected = originalRows.mapNotNull { original ->
                val row = original.toMutableMap()
                when (table) {
                    "books" -> {
                        if (version < 112 && row["bookUrl"] == MigrationFixture.OBSOLETE_BOOK_URI) {
                            return@mapNotNull null
                        }
                        if (version == 50) row["type"] = "264"
                        if (version in 107..111) {
                            row["group"] = (row.getValue("group")!!.toLong() and 1L.inv()).toString()
                        }
                    }
                    "book_groups" -> {
                        if (version < 107) return@mapNotNull null
                        if (version < 112 && row["groupId"] == "1") return@mapNotNull null
                    }
                    "txtTocRules" -> if (row.containsKey("rule")) {
                        row["chapterRule"] = row.remove("rule")
                    }
                    "book_content_processes" -> {
                        if (row["kind"] in setOf("ai_clean", "ai_rewrite")) row["kind"] = "manual_replacement"
                        if (row["source"] == "ai") row["source"] = "user"
                    }
                }
                row
            }
            val actual = after.getValue(table)
            assertEquals("v$version -> 113: $table 行数", expected.size, actual.size)
            if (expected.isNotEmpty()) {
                // 旧版本尚无的列(如 listIntro/syncTime)由 Room schema 校验; 旧列连同主键必须逐值保留.
                // 必须按"两边都存在的列"取交集: 只过滤期望值会让新表的额外列以 null 出现,
                // 与旧快照的"缺键"被误判为差异.
                val columns = expected.flatMap { it.keys }.toSet() intersect actual.flatMap { it.keys }.toSet()
                assertEquals(
                    "v$version -> 113: $table 字段或主键发生变化",
                    expected.map { row -> row.filterKeys { it in columns } }.toSet(),
                    actual.map { row -> row.filterKeys { it in columns } }.toSet(),
                )
            }
        }
    }

    private fun assertChapterOrderAndProgress(database: SupportSQLiteDatabase, fixture: MigrationFixture) {
        fixture.bookUris.forEach { uri ->
            val chapters = MigrationFixture.rows(
                database,
                "SELECT url, `index`, title FROM chapters WHERE bookUrl = ? ORDER BY `index`",
                arrayOf(uri),
            )
            assertEquals(listOf("0", "1", "2"), chapters.map { it["index"] })
            assertEquals((0..2).map { "$uri#chapter-$it" }, chapters.map { it["url"] })
            assertEquals((0..2).map { "第${it + 1}章" }, chapters.map { it["title"] })
            val book = MigrationFixture.rows(
                database,
                "SELECT durChapterIndex, durChapterPos, durChapterTitle, totalChapterNum FROM books WHERE bookUrl = ?",
                arrayOf(uri),
            ).single()
            assertEquals("1", book["durChapterIndex"])
            assertEquals("37", book["durChapterPos"])
            assertEquals("第2章", book["durChapterTitle"])
            assertEquals("3", book["totalChapterNum"])
        }
    }

    private fun assertStatistics(
        database: SupportSQLiteDatabase,
        before: Map<String, List<Map<String, String?>>>,
        version: Int,
    ) {
        listOf("readRecord", "readRecordDetail", "readRecordSession").forEach { table ->
            val actual = MigrationFixture.rows(database, "SELECT * FROM `$table`")
            if (version <= 110) {
                // 110 -> 111 已有统计口径重置, 不能归咎于本次清理或要求恢复旧汇总.
                assertTrue("v$version 应经过统计重置: $table", actual.isEmpty())
            } else {
                val expected = before.getValue(table)
                assertTrue("必须以非空统计数据验证 $table", expected.isNotEmpty())
                assertEquals("v$version 之后不得再次重置 $table", expected, actual)
            }
        }
    }

    private fun assertForeignKeyIntegrity(database: SupportSQLiteDatabase) {
        assertTrue(
            "fixture 或迁移产生了悬空外键",
            MigrationFixture.rows(database, "PRAGMA foreign_key_check").isEmpty(),
        )
        assertEquals(
            listOf(mapOf("integrity_check" to "ok")),
            MigrationFixture.rows(database, "PRAGMA integrity_check"),
        )
    }
}
