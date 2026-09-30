package io.legado.app.data.repository

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LocalDirectoryTagsTest {

    @Test
    fun `目录根文件使用所选目录名作为标签`() {
        val root = "file:///mnt/Awork/Books"
        assertEquals(
            listOf("Books"),
            directoryTagsOf(root, "Books", "/mnt/Awork/Books/a.txt")
        )
    }

    @Test
    fun `上级目录分组包含根目录名和相对子目录且自动去重`() {
        val root = "file:///mnt/Awork"
        assertEquals(
            listOf("Awork", "Books"),
            directoryTagsOf(root, "Awork", "/mnt/Awork/Books/a.txt")
        )
    }

    @Test
    fun `文档不在所选树下时不泄露绝对文档目录`() {
        val root = "content://com.android.externalstorage.documents/tree/primary%3ANovel"
        val book = "content://com.android.externalstorage.documents/document/primary%3AAWork%2Fbook.epub"

        assertEquals(emptyList<String>(), relativeDirectoryOf(root, book))
    }

    @Test
    fun `文档 URI 只返回所选树下的相对子目录`() {
        val root = "content://com.android.externalstorage.documents/tree/primary%3AAWork%2FNovel"
        val book = "content://com.android.externalstorage.documents/document/primary%3AAWork%2FNovel%2Fggg%2Fbook.epub"

        assertEquals(listOf("ggg"), relativeDirectoryOf(root, book))
    }
}
