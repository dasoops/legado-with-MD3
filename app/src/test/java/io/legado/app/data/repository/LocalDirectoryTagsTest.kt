package io.legado.app.data.repository

import android.app.Application
import io.legado.app.domain.model.BookTags
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LocalDirectoryTagsTest {

    @Test
    fun `目录标签以所选目录为基础且不含上级层级`() {
        val root = "file:///home/Awork/韩轻"
        assertEquals(
            listOf("韩轻"),
            BookTags.directoryTags("韩轻", relativeDirectoryOf(root, "/home/Awork/韩轻/a.txt"))
        )
        assertEquals(
            listOf("韩轻", "ggg"),
            BookTags.directoryTags("韩轻", relativeDirectoryOf(root, "/home/Awork/韩轻/ggg/c.txt"))
        )
    }
}