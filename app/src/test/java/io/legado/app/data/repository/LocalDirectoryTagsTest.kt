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
}
