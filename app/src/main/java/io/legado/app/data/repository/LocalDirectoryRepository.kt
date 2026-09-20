package io.legado.app.data.repository

import androidx.core.net.toUri
import io.legado.app.constant.AppPattern
import io.legado.app.domain.gateway.LocalDirectoryGateway
import io.legado.app.domain.model.LocalBookProgress
import io.legado.app.domain.model.LocalDirectoryEntry
import io.legado.app.utils.FileDoc
import io.legado.app.utils.list
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalDirectoryRepository(
    private val bookImportRepository: BookImportRepository,
) : LocalDirectoryGateway {

    override suspend fun rootDocument(treeUri: String): LocalDirectoryEntry? =
        withContext(Dispatchers.IO) {
            // SAF 的 fromTreeUri 在权限失效等情况下会返回 null 并触发 !! 崩溃, 故整体兜底
            runCatching {
                FileDoc.fromUri(treeUri.toUri(), true)
            }.getOrNull()?.toEntry()
        }

    override suspend fun listChildren(dirUri: String): List<LocalDirectoryEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                FileDoc.fromUri(dirUri.toUri(), true)
                    .list { item ->
                        // 隐藏项与压缩包不参与目录浏览
                        !item.name.startsWith(".") &&
                            (item.isDir || item.name.matches(AppPattern.bookFileRegex))
                    }
                    .orEmpty()
                    .map { it.toEntry() }
            }.getOrDefault(emptyList())
        }

    override fun flowLocalBookProgress(): Flow<Map<String, LocalBookProgress>> =
        bookImportRepository.flowLocalBooks().map { books ->
            books.associate { book ->
                book.originName to LocalBookProgress(
                    bookUrl = book.bookUrl,
                    durChapterIndex = book.durChapterIndex,
                    totalChapterNum = book.totalChapterNum,
                    durChapterTitle = book.durChapterTitle,
                )
            }
        }

    private fun FileDoc.toEntry() = LocalDirectoryEntry(
        uri = uri.toString(),
        name = name,
        isDir = isDir,
        size = size,
        lastModified = lastModified,
    )
}
