package io.legado.app.data.repository

import android.provider.DocumentsContract
import android.provider.DocumentsContract.getDocumentId
import android.provider.DocumentsContract.getTreeDocumentId
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.Book
import io.legado.app.domain.gateway.LocalDirectoryGateway
import io.legado.app.domain.model.BookTags
import io.legado.app.help.book.isLocal
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
        val rootName = directoryName(rootUri)
        var count = 0
        files.forEach { file ->
            runCatching {
                val bookUrl = file.toString()
                val directoryTags = directoryTagsOf(rootUri, rootName, bookUrl)
                val existing = bookRepository.getBook(bookUrl)
                // 已归入本组时避免重导入重置目录, 只补取旧条目缺少的封面.
                if (existing != null && (existing.group and groupId) != 0L) {
                    if (existing.isLocal && existing.coverUrl.isNullOrBlank()) {
                        LocalBook.upBookInfo(existing)
                        if (!existing.coverUrl.isNullOrBlank()) {
                            bookRepository.update(existing)
                        }
                    }
                    syncDirectoryTags(existing, directoryTags)
                    return@forEach
                }
                val book = LocalBook.importFile(file.uri, directoryTags = directoryTags)
                syncDirectoryTags(book, directoryTags)
                if ((book.group and groupId) == 0L) {
                    book.group = book.group or groupId
                    book.save()
                }
                count++
            }.onFailure {
                AppLog.put("导入目录书籍失败\n${file.uri}", it)
            }
        }
        count
    }

    /**
     * 用当前规则刷新书籍的目录标签: 先摘掉旧目录标签, 再补入新目录标签, 保留用户手动标签.
     * 历史导入曾把所选目录名及绝对路径层级写成标签, 会与本地目录分组名重复.
     */
    internal suspend fun syncDirectoryTags(book: Book, newTags: List<String>) {
        val oldTags = book.config.directoryTags.orEmpty()
        if (oldTags == newTags) return
        val userTags = BookTags.parse(book.customTag).filterNot { it in oldTags }
        book.config.directoryTags = newTags
        book.customTag = BookTags.editable(userTags + newTags).joinToString(",").ifBlank { null }
        bookRepository.update(book)
    }

    override fun relativeDirectory(rootUri: String, bookUrl: String): List<String> =
        relativeDirectoryOf(rootUri, bookUrl)

    private fun treeDocumentId(rootUri: String): String? =
        runCatching { getTreeDocumentId(rootUri.toUri()) }.getOrNull()

    /**
     * 构造根文档。不能用 FileDoc.fromUri(..., true):
     * 子目录 uri 会被 DocumentFile.fromTreeUri 退回树的根文档, 丢失子文档 id。
     */
    private fun rootDirDoc(rootUri: String): FileDoc? {
        val treeUri = rootUri.toUri()
        if (!treeUri.isContentScheme()) {
            val path = treeUri.path ?: return null
            return FileDoc(
                name = File(path).name,
                isDir = true,
                size = 0,
                lastModified = 0,
                uri = treeUri,
            )
        }
        val name = runCatching { DocumentFile.fromTreeUri(appCtx, treeUri)?.name }.getOrNull()
            ?: getTreeDocumentId(treeUri).substringAfterLast('/').substringAfter(':')
        // FileDoc.list 内部用 getDocumentId, 对 tree URI (2 段路径) 会抛 IllegalArgumentException;
        // 转成 document URI (4 段) 后根目录与子目录列举才能正常工作
        val documentUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, getTreeDocumentId(treeUri))
        }.getOrDefault(treeUri)
        return FileDoc(name = name, isDir = true, size = 0, lastModified = 0, uri = documentUri)
    }

    private fun scanBookFiles(root: FileDoc): List<FileDoc> {
        val result = arrayListOf<FileDoc>()
        val queue = ArrayDeque<FileDoc>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            val children = runCatching { dir.list() }
                .onFailure { AppLog.put("读取目录失败\n${dir.uri}", it) }
                .getOrNull() ?: continue
            children.forEach { child ->
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

/**
 * 计算 bookUrl 相对 rootUri 的目录层级 (不含文件名). 本地目录分组据此生成相对标签,
 * 不能让绝对路径里所选目录之上的层级混入标签.
 */
internal fun relativeDirectoryOf(rootUri: String, bookUrl: String): List<String> {
    return runCatching {
        val root = rootUri.toUri()
        if (root.isContentScheme()) {
            val rootId = getTreeDocumentId(root)
            val bookId = getDocumentId(bookUrl.toUri())
            val relativeId = bookId
                .takeIf { it.startsWith("$rootId/") }
                ?.removePrefix("$rootId/")
                ?: return emptyList()
            relativeId
                .split('/')
                .dropLast(1)
                .filter { it.isNotBlank() }
        } else {
            val rootPath = root.path ?: return emptyList()
            val bookPath = bookUrl.toUri().path ?: return emptyList()
            // 测试/构建可能运行在 Windows 上, File.relativeTo 的路径分隔符随平台变化.
            File(bookPath).relativeTo(File(rootPath)).path
                .replace('\\', '/')
                .split('/')
                .dropLast(1)
                .filter { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())
}

/**
 * 本地目录分组的书籍目录标签: 取所选目录之下的相对子目录, 并剔除与分组同名者.
 * 所选目录名本身即分组名, 作为标签只会与分组重复, 因此不生成.
 */
internal fun directoryTagsOf(rootUri: String, rootName: String?, bookUrl: String): List<String> =
    BookTags.editable(
        relativeDirectoryOf(rootUri, bookUrl).filter { it != rootName }
    )
