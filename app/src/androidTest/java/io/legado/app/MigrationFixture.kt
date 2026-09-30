package io.legado.app

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import java.io.FileNotFoundException
import org.json.JSONObject

internal class MigrationFixture(private val version: Int) {

    val retainedTables = setOf(
        "books", "book_groups", "chapters", "replace_rules", "cookies", "bookmarks",
        "readRecordDetail", "readRecordSession", "txtTocRules", "readRecord", "caches",
        "keyboardAssists", "servers", "search_content_history", "highlightRules",
        "highlight_tag_rules", "book_content_processes", "exact_chapter_page_counts", "book_marks",
    )

    val deletedTables = setOf(
        "ai_memory", "ai_chat_messages", "ai_chat_conversations", "ai_artifacts",
        "ai_prompt_presets", "ai_task_presets", "ai_model_profiles", "ai_provider_profiles",
        "book_outline_nodes", "book_knowledge_entries", "book_character_relations",
        "book_character_events", "book_character_profiles", "chapter_speech_segments",
        "chapter_speech_analysis", "book_voice_bindings", "read_aloud_voices",
        "book_sources", "searchBooks", "search_keywords", "ruleSubs",
        "homepage_modules", "homepage_custom_sets",
    )

    val statisticsTables = setOf("readRecord", "readRecordDetail", "readRecordSession")
    val bookUris = listOf(TXT_URI, EPUB_URI, MOBI_URI, PDF_URI)

    private val entities = loadEntities()

    fun insert(database: SupportSQLiteDatabase) {
        database.setForeignKeyConstraintsEnabled(true)
        database.beginTransaction()
        try {
            insertBooksAndChapters(database)
            insertLocalData(database)
            insertProcesses(database)
            insertStatistics(database)
            insertDeletedData(database)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun snapshot(database: SupportSQLiteDatabase): Map<String, List<Map<String, String?>>> {
        val tables = rows(database, "SELECT name FROM sqlite_master WHERE type = 'table'")
            .mapNotNull { it["name"] }.toSet()
        return (retainedTables + deletedTables).intersect(tables).associateWith { table ->
            rows(database, "SELECT * FROM `$table`")
        }
    }

    private fun insertBooksAndChapters(database: SupportSQLiteDatabase) {
        if (version >= 107) {
            insertRow(
                database,
                "book_groups",
                "groupId" to 2,
                "groupName" to "本地文件目录",
                "order" to 2,
                "show" to 1,
                "localDirectoryUri" to "file:///storage/emulated/0/Books",
                "pattern" to null,
            )
            insertRow(
                database,
                "book_groups",
                "groupId" to 4,
                "groupName" to "授权文档目录",
                "order" to 4,
                "show" to 1,
                "localDirectoryUri" to "content://com.example.documents/tree/library",
                "pattern" to null,
            )
        }
        if (version < 112) {
            insertRow(
                database,
                "book_groups",
                "groupId" to 1,
                "groupName" to "历史普通组",
                "order" to 1,
                "show" to 1,
                "localDirectoryUri" to null,
                "pattern" to null,
            )
        }
        bookUris.forEachIndexed { index, uri ->
            val directoryGroup = if (index % 2 == 0) 2 else 4
            val group = when {
                version < 107 -> 0
                uri == MOBI_URI && version < 112 -> directoryGroup or 1
                else -> directoryGroup
            }
            insertRow(
                database, "books",
                "bookUrl" to uri, "tocUrl" to "$uri/toc", "origin" to "loc_book",
                "originName" to "本地导入", "name" to "Fixture ${uri.substringAfterLast('.')} book",
                "author" to "Fixture author", "kind" to uri.substringAfterLast('.').uppercase(),
                "type" to if (version == 50) 0 else 264, "group" to group,
                "intro" to "迁移前简介", "listIntro" to "保留的列表简介", "charset" to "UTF-8",
                "customTag" to "本地,收藏", "readConfig" to "{\"reverseToc\":false}",
                "variable" to "{\"local\":true}", "order" to index, "originOrder" to index,
                "totalChapterNum" to 3, "durChapterIndex" to 1, "durChapterPos" to 37,
                "durChapterTitle" to "第2章", "durChapterTime" to 1700000001234L,
                "wordCount" to "900", "canUpdate" to 0,
            )
            // 故意打乱插入顺序, 避免章节顺序断言只是碰巧等于 rowid 顺序.
            listOf(2, 0, 1).forEach { chapter ->
                insertRow(
                    database, "chapters",
                    "bookUrl" to uri, "url" to "$uri#chapter-$chapter", "baseUrl" to uri,
                    "index" to chapter, "title" to "第${chapter + 1}章", "isVolume" to 0,
                    "isVip" to 0, "isPay" to 0, "tocLevel" to chapter,
                    "start" to chapter * 300L, "end" to (chapter + 1) * 300L,
                    "wordCount" to "300", "startFragmentId" to "start-$chapter",
                    "endFragmentId" to "end-$chapter", "variable" to "{}",
                )
            }
        }
        if (version in 107..111) {
            insertRow(
                database,
                "books",
                "bookUrl" to OBSOLETE_BOOK_URI,
                "name" to "仅属于普通组的旧书",
                "author" to "旧作者",
                "origin" to "loc_book",
                "group" to 1,
                "type" to 264,
            )
        }
    }

    private fun insertLocalData(database: SupportSQLiteDatabase) {
        insertRow(
            database, "bookmarks",
            "time" to 1700000010000L, "bookUrl" to EPUB_URI, "bookName" to EPUB_BOOK_NAME,
            "bookAuthor" to "Fixture author", "chapterIndex" to 1, "chapterPos" to 23,
            "chapterName" to "第2章", "bookText" to "保留的书签正文", "content" to "书签批注",
        )
        insertRow(
            database, "replace_rules",
            "id" to 701L, "name" to "本地替换", "group" to "正文校正", "pattern" to "错字",
            "replacement" to "正字", "scope" to EPUB_BOOK_NAME, "isEnabled" to 1, "isRegex" to 0,
            "scopeTitle" to 0, "scopeContent" to 1, "sortOrder" to 17, "timeoutMillisecond" to 3000,
        )
        insertRow(
            database, "txtTocRules",
            "id" to 702L, "name" to "中文章节", "rule" to "^第.+章.*$", "chapterRule" to "^第.+章.*$",
            "volumeRule" to "^第.+卷.*$", "example" to "第1章 开篇", "serialNumber" to 19, "enable" to 1,
        )
        insertRow(
            database, "highlightRules",
            "id" to "highlight-1", "name" to "重点", "pattern" to "重点", "sampleText" to "重点内容",
            "enabled" to 1, "textColor" to -65536, "bgColor" to -256, "fontWeight" to 700,
            "isItalic" to 1, "fontSizeOffset" to 2, "underlineWidth" to 1.5,
        )
        // highlight_tag_rules.id 在所有历史版本都是 INTEGER PRIMARY KEY, 传字符串会触发
        // SQLITE_MISMATCH; 该表主键不使用 UUID, 必须给整数.
        insertRow(
            database,
            "highlight_tag_rules",
            "id" to 706L,
            "title" to "人物",
            "pattern" to "主角",
            "enabled" to 1,
            "order" to 9,
        )
        insertRow(
            database, "book_marks",
            "id" to "mark-1", "bookUrl" to EPUB_URI, "bookName" to EPUB_BOOK_NAME,
            "bookAuthor" to "Fixture author", "chapterIndex" to 1, "chapterName" to "第2章",
            "anchorJson" to "{\"start\":10,\"end\":20}", "styleJson" to "{\"color\":\"yellow\"}",
            "note" to "保留划线批注", "enabled" to 1,
        )
        insertRow(
            database,
            "servers",
            "id" to 703L,
            "name" to "测试 WebDAV",
            "type" to "WEBDAV",
            "sortNumber" to 21,
            "config" to "{\"url\":\"https://dav.example.test/library\",\"username\":\"reader\",\"password\":\"fixture\"}",
        )
        insertRow(
            database, "exact_chapter_page_counts",
            "bookId" to EPUB_URI, "chapterId" to "$EPUB_URI#chapter-1", "chapterIndex" to 1,
            "contentHash" to 123456L, "layoutSignature" to 654321L, "engineVersion" to 3, "pageCount" to 7,
        )
        insertRow(database, "cookies", "url" to "https://dav.example.test", "cookie" to "fixture=reader")
        insertRow(database, "caches", "key" to "local-cache", "value" to "保留缓存", "deadline" to 1900000000000L)
        insertRow(database, "keyboardAssists", "type" to 0, "key" to "fixture", "value" to "[]", "serialNo" to 5)
        insertRow(
            database,
            "search_content_history",
            "id" to 704L,
            "bookName" to EPUB_BOOK_NAME,
            "bookAuthor" to "Fixture author",
            "query" to "正文关键词",
        )
    }

    private fun insertProcesses(database: SupportSQLiteDatabase) {
        listOf(
            "ai_clean" to "ai",
            "ai_rewrite" to "ai",
            "manual_replacement" to "user",
            "user_highlight" to "user",
            "ai_clean" to "user",
            "manual_replacement" to "ai",
        ).forEachIndexed { index, (kind, source) ->
            insertRow(
                database, "book_content_processes",
                "id" to "process-$index", "bookUrl" to EPUB_URI,
                "chapterIndex" to if (index == 0) null else 1,
                "kind" to kind, "source" to source, "stage" to "content", "target" to "selection",
                "anchorJson" to "{\"start\":$index,\"end\":20}",
                "actionJson" to "{\"replacement\":\"处理后的正文 $index\"}",
                "styleJson" to if (index == 0) null else "{\"color\":\"yellow\"}",
                "aiArtifactId" to if (source == "ai") "legacy-ai_artifacts-id" else null,
                "sourceContentHash" to "legacy-source-hash-$index",
                "enabled" to index % 2, "sortOrder" to 20 - index, "status" to index % 4,
                "schemaVersion" to 1, "createdAt" to 1700000000000L + index, "updatedAt" to 1700000010000L + index,
            )
        }
    }

    private fun insertStatistics(database: SupportSQLiteDatabase) {
        val identity = arrayOf(
            "deviceId" to if (version > 102) "" else "old-device",
            "bookName" to EPUB_BOOK_NAME,
            "bookAuthor" to "Fixture author",
        )
        insertRow(database, "readRecord", *identity, "readTime" to 3600L, "lastRead" to 1700003600000L)
        insertRow(
            database,
            "readRecordDetail",
            *identity,
            "date" to "2026-09-30",
            "readTime" to 3600L,
            "readWords" to 2400L,
            "firstReadTime" to 1700000000000L,
            "lastReadTime" to 1700003600000L,
        )
        insertRow(
            database,
            "readRecordSession",
            *identity,
            "id" to 705L,
            "startTime" to 1700000000000L,
            "endTime" to 1700003600000L,
            "words" to 2400L,
        )
    }

    private fun insertDeletedData(database: SupportSQLiteDatabase) {
        // searchBooks 的外键在历史版本就存在, 父书源须先插入.
        insertRow(database, "book_sources", "bookSourceUrl" to SOURCE_URL, "bookSourceName" to "待删除书源")
        insertRow(
            database,
            "searchBooks",
            "bookUrl" to "https://source.example.test/book",
            "origin" to SOURCE_URL,
            "name" to "待删除搜索缓存",
            "author" to "缓存作者",
            "intro" to "不应污染本地简介",
        )
        (deletedTables - setOf("book_sources", "searchBooks")).forEach { table ->
            insertRow(
                database, table,
                "bookUrl" to EPUB_URI, "sourceUrl" to SOURCE_URL,
                "providerId" to "legacy-ai_provider_profiles-id", "modelProfileId" to "legacy-ai_model_profiles-id",
                "conversationId" to "legacy-ai_chat_conversations-id", "analysisId" to "legacy-chapter_speech_analysis-id",
                "voiceId" to "legacy-read_aloud_voices-id", "characterId" to "legacy-book_character_profiles-id",
            )
        }
        if (version >= 82) {
            check(rows(database, "SELECT * FROM book_sources_part").isNotEmpty())
        }
    }

    private fun insertRow(database: SupportSQLiteDatabase, table: String, vararg values: Pair<String, Any?>) {
        val entity = entities[table] ?: return
        val overrides = values.toMap()
        val fields = entity.getJSONArray("fields")
        val columns = (0 until fields.length()).map { fields.getJSONObject(it) }
        val arguments = columns.map { field ->
            val name = field.getString("columnName")
            if (name in overrides) {
                overrides[name]
            } else if (!field.optBoolean("notNull")) {
                null
            } else {
                when (field.getString("affinity")) {
                    "INTEGER" -> 7L
                    "REAL" -> 1.25
                    "BLOB" -> byteArrayOf(1, 2, 3)
                    else -> "legacy-$table-$name"
                }
            }
        }.toTypedArray()
        val names = columns.joinToString { "`${it.getString("columnName")}`" }
        val placeholders = columns.joinToString { "?" }
        database.execSQL("INSERT INTO `$table` ($names) VALUES ($placeholders)", arguments)
    }

    private fun loadEntities(): Map<String, JSONObject> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val path = "${AppDatabase::class.java.canonicalName}/$version.json"
        val input = try {
            instrumentation.context.assets.open(path)
        } catch (_: FileNotFoundException) {
            instrumentation.targetContext.assets.open(path)
        }
        val entities = input.bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database").getJSONArray("entities")
        }
        return (0 until entities.length()).associate { index ->
            val entity = entities.getJSONObject(index)
            entity.getString("tableName") to entity
        }
    }

    companion object {
        const val TXT_URI = "file:///storage/emulated/0/Books/fixture.txt"
        const val EPUB_URI = "content://com.example.documents/document/fixture.epub"
        const val MOBI_URI = "file:///storage/emulated/0/Books/fixture.mobi"
        const val PDF_URI = "content://com.example.documents/document/fixture.pdf"
        const val EPUB_BOOK_NAME = "Fixture epub book"
        const val OBSOLETE_BOOK_URI = "file:///storage/emulated/0/Books/ordinary-only.txt"
        private const val SOURCE_URL = "https://source.example.test"

        fun rows(
            database: SupportSQLiteDatabase,
            sql: String,
            arguments: Array<out String> = emptyArray(),
        ): List<Map<String, String?>> = database.query(sql, arguments).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.columnNames.mapIndexed { index, name -> name to cursor.getString(index) }.toMap())
                }
            }
        }
    }
}
