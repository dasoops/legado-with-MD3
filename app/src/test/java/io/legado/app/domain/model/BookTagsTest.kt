package io.legado.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class BookTagsTest {
    @Test
    fun `路径标签包含每一级目录且同名合并`() {
        assertEquals(listOf("Books", "ghs"), BookTags.directoryNames("/Books/ghs/book.epub"))
        assertEquals(listOf("A", "Shared"), BookTags.directoryNames("/A/Shared/Shared/book.txt"))
        assertEquals(emptyList<String>(), BookTags.directoryNames("/book.txt"))
        assertEquals(listOf("Books", "ghs"), BookTags.directoryNames("C:\\Books\\ghs\\book.txt"))
    }

    @Test
    fun `内置标签沿用进度判定且不接受手动覆盖`() {
        assertEquals(listOf("分类", "未读"), BookTags.display("已读,分类", null, 0, 0, 10))
        assertEquals(listOf("分类"), BookTags.display("未读,分类", null, 2, 1, 10))
        assertEquals(listOf("已读"), BookTags.display(null, null, 9, 0, 10))
        assertEquals(listOf("未读", "已读"), BookTags.display(null, null, 0, 0, 1))
        assertEquals(emptyList<String>(), BookTags.display(null, null, 1, 0, 0))
    }

    @Test
    fun `标签去重且 ID 不依赖列表位置`() {
        assertEquals(listOf("A", "B"), BookTags.editable(listOf(" A,A\nB ", "已读", "未读")))
        assertEquals(BookTags.groupId("Shared"), BookTags.groupId("Shared"))
        assertNotEquals(BookTags.groupId("Aa"), BookTags.groupId("BB"))
        assertTrue(BookTags.groupId("分类") < -1024)
    }
    @Test
    fun `元数据与其他展示标签一同参与分组`() {
        assertEquals(listOf("TXT", "分类", "未读"), BookTags.grouping(null, "TXT,33 b,13字,分类", 0, 0, 10))
    }
}
