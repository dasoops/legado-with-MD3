package io.legado.app.data.repository

import io.legado.app.exception.EmptyFileException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDirectoryImportFailureTest {
    @Test
    fun `空文件归类为跳过`() {
        assertEquals(
            DirectoryImportFailureKind.EMPTY_FILE,
            classifyDirectoryImportFailure("empty.txt", EmptyFileException("empty")),
        )
    }

    @Test
    fun `加密 PDF 归类为跳过`() {
        assertEquals(
            DirectoryImportFailureKind.ENCRYPTED_PDF,
            classifyDirectoryImportFailure(
                "protected.pdf",
                SecurityException("Password required to access document"),
            ),
        )
    }

    @Test
    fun `PDF 的其他安全异常仍视为失败`() {
        assertEquals(
            DirectoryImportFailureKind.UNEXPECTED,
            classifyDirectoryImportFailure(
                "book.pdf",
                SecurityException("Permission denied"),
            ),
        )
    }

    @Test
    fun `非 PDF 的安全异常仍视为失败`() {
        assertEquals(
            DirectoryImportFailureKind.UNEXPECTED,
            classifyDirectoryImportFailure(
                "book.txt",
                SecurityException("Password required to access document"),
            ),
        )
    }

    @Test
    fun `其他解析异常仍视为失败`() {
        assertEquals(
            DirectoryImportFailureKind.UNEXPECTED,
            classifyDirectoryImportFailure("book.epub", IOException("broken zip")),
        )
    }
}
