package io.legado.app.data.repository

import android.provider.DocumentsContract
import android.provider.DocumentsContract.getDocumentId
import android.provider.DocumentsContract.getTreeDocumentId
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.constant.AppPattern
import io.legado.app.domain.gateway.LocalDirectoryGateway
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.FileDoc
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.list
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File

class LocalDirectoryRepository(
    private val bookRepository: BookRepository,
) : LocalDirectoryGateway {

    override suspend fun directoryName(rootUri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = rootUri.toUri()
            if (uri.isContentScheme()) {
                DocumentFile.fromTreeUri(appCtx, uri)?.name
            } else {
                uri.path?.let { File(it).name }
            }
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: treeDocumentId(rootUri)?.substringAfterLast('/')?.substringAfter(':')
                ?.takeIf { it.isNotBlank() }
    }

    override suspend fun importDirectoryToGroup(
        groupId: Long,
        rootUri: String,
    ): Int = withContext(Dispatchers.IO) {
        val root = rootDirDoc(rootUri) ?: return@withContext 0
        val files = scanBookFiles(root)
        var count = 0
        files.forEach { file ->
            runCatching {
                val bookUrl = file.toString()
                val existing = bookRepository.getBook(bookUrl)
                // 已归入本组说明是重复扫描, 重新导入会重解析元数据并重置目录, 直接跳过
                if (existing != null && (existing.group and groupId) != 0L) return@forEach
                val book = LocalBook.importFile(file.uri)
                if ((book.group and groupId) == 0L) {
                    book.group = book.group or groupId
                    book.save()
                }
                count++
            }
        }
        count
    }

    override fun relativeDirectory(rootUri: String, bookUrl: String): List<String> {
        return runCatching {
            val root = rootUri.toUri()
            if (root.isContentScheme()) {
                val rootId = getTreeDocumentId(root)
                val bookId = getDocumentId(bookUrl.toUri())
                bookId.removePrefix(rootId).trim('/')
                    .split('/')
                    .dropLast(1)
                    .filter { it.isNotBlank() }
            } else {
                val rootPath = root.path ?: return emptyList()
                val bookPath = bookUrl.toUri().path ?: return emptyList()
                File(bookPath).relativeTo(File(rootPath)).path
                    .split('/')
                    .dropLast(1)
                    .filter { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    private fun treeDocumentId(rootUri: String): String? =
        runCatching { getTreeDocumentId(rootUri.toUri()) }.getOrNull()

    /**
     * 直接用传入 uri 构造根文档。不能用 FileDoc.fromUri(..., true):
     * 子目录 uri 会被 DocumentFile.fromTreeUri 退回树的根文档, 丢失子文档 id。
     */
    private fun rootDirDoc(rootUri: String): FileDoc? {
        val uri = rootUri.toUri()
        val name = if (uri.isContentScheme()) {
            runCatching { DocumentFile.fromTreeUri(appCtx, uri)?.name }.getOrNull()
        } else {
            uri.path?.let { File(it).name }
        } ?: uri.lastPathSegment ?: return null
        return FileDoc(name = name, isDir = true, size = 0, lastModified = 0, uri = uri)
    }

    private fun scanBookFiles(root: FileDoc): List<FileDoc> {
        val result = arrayListOf<FileDoc>()
        val queue = ArrayDeque<FileDoc>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            dir.list()?.forEach { child ->
                when {
                    child.name.startsWith(".") -> Unit
                    child.isDir -> queue.add(child)
                    child.name.matches(AppPattern.bookFileRegex) -> result.add(child)
                }
            }
        }
        return result
    }
}
