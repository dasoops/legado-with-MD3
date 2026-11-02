package io.legado.app.data.repository

import io.legado.app.data.entities.BookIdentityRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadRecordBookMatcherTest {
    @Test
    fun `resolves book when book name keeps original whitespace`() {
        val books = listOf(ref("u1", " 乖巧女友与学弟 ", "全本完结版"))

        val resolved = ReadRecordBookMatcher.resolve("乖巧女友与学弟", "全本完结版", books)

        assertEquals("u1", resolved?.bookUrl)
    }

    @Test
    fun `resolves book when record collapsed inner whitespace`() {
        val books = listOf(ref("u1", "轻青诗语1-264章  【都市NTL】", ""))

        val resolved = ReadRecordBookMatcher.resolve("轻青诗语1-264章 【都市NTL】", "", books)

        assertEquals("u1", resolved?.bookUrl)
    }

    @Test
    fun `blank author record matches book without author`() {
        val books = listOf(ref("u1", "某书", ""))

        val resolved = ReadRecordBookMatcher.resolve("某书", "", books)

        assertEquals("u1", resolved?.bookUrl)
    }

    @Test
    fun `blank author record does not fall back to authored book`() {
        val books = listOf(ref("u1", "某书", "真实作者"))

        assertNull(ReadRecordBookMatcher.resolve("某书", "", books))
    }

    @Test
    fun `stale author still resolves when name is unique`() {
        val books = listOf(ref("u1", "某书", "新作者"))

        assertEquals("u1", ReadRecordBookMatcher.resolve("某书", "旧作者", books)?.bookUrl)
    }

    @Test
    fun `stale author does not guess between same name books`() {
        val books =
            listOf(
                ref("u1", "某书", "作者甲"),
                ref("u2", "某书", "作者乙"),
            )

        assertNull(ReadRecordBookMatcher.resolve("某书", "旧作者", books))
    }

    @Test
    fun `same identity duplicates prefer most recently read book`() {
        val books =
            listOf(
                ref("u1", "某书", "作者", durChapterTime = 1_000),
                ref("u2", "某书", "作者", durChapterTime = 5_000),
            )

        assertEquals("u2", ReadRecordBookMatcher.resolve("某书", "作者", books)?.bookUrl)
    }

    @Test
    fun `returns null when no book shares the normalized name`() {
        val books = listOf(ref("u1", "另一本书", ""))

        assertNull(ReadRecordBookMatcher.resolve("某书", "", books))
    }

    @Test
    fun `blank record name never resolves`() {
        val books = listOf(ref("u1", "某书", ""))

        assertNull(ReadRecordBookMatcher.resolve("  ", "", books))
    }

    private fun ref(
        bookUrl: String,
        name: String,
        author: String,
        durChapterTime: Long = 0L,
    ): BookIdentityRef = BookIdentityRef(
        bookUrl = bookUrl,
        name = name,
        author = author,
        durChapterTime = durChapterTime,
        origin = null,
        coverUrl = null,
        customCoverUrl = null,
        durChapterTitle = null,
        totalChapterNum = null,
        durChapterIndex = null,
    )
}
