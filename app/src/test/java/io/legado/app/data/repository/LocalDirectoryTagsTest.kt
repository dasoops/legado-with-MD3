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
    fun `目录标签剔除与分组同名的所选目录并保留相对子目录`() {
        val root = "file:///home/Awork/Novel"
        // aaa.txt 直接位于所选目录 Novel, 标签与分组同名, 不再生成
        assertEquals(
            emptyList<String>(),
            directoryTagsOf(root, "Novel", "/home/Awork/Novel/aaa.txt")
        )
        // ggg/c.txt 仅在 Novel 之下, 保留相对子目录 ggg
        assertEquals(
            listOf("ggg"),
            directoryTagsOf(root, "Novel", "/home/Awork/Novel/ggg/c.txt")
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
