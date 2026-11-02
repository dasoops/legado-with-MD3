package io.legado.app.data.repository

import io.legado.app.data.entities.BookIdentityRef
import io.legado.app.data.entities.readRecord.ReadRecordIdentity

/**
 * 阅读记录身份 → 书籍身份的匹配规则。
 *
 * 阅读记录写入时会做空白归一化（折叠连续空白并 trim），而 books 表保存原始书名/作者，
 * 因此不能用精确等值反查书籍。匹配规则：
 * 1. 书名、作者归一化后都相同才视为同一身份；
 * 2. 记录作者已过期（改作者）时，书名在书架中唯一才回退，避免把记录接到同名异书；
 * 3. 作者为空的记录不回退：未知作者的书籍归属能力已废弃，不再按书名唯一性猜测。
 */
internal object ReadRecordBookMatcher {
    fun resolve(
        recordName: String,
        recordAuthor: String,
        books: List<BookIdentityRef>,
    ): BookIdentityRef? {
        val name = ReadRecordIdentity.bookName(recordName)
        if (name.isEmpty()) return null
        val candidates = books.filter { ReadRecordIdentity.bookName(it.name) == name }
        if (candidates.isEmpty()) return null
        val author = ReadRecordIdentity.author(recordAuthor)
        val sameAuthor = candidates.filter { ReadRecordIdentity.author(it.author) == author }
        if (sameAuthor.isNotEmpty()) return mostRecent(sameAuthor)
        // 未知作者（作者为空）记录不再回退到“书名唯一”的书籍。
        if (author.isEmpty()) return null
        return candidates.singleOrNull()
    }

    private fun mostRecent(candidates: List<BookIdentityRef>): BookIdentityRef = candidates.maxWithOrNull(compareBy({ it.durChapterTime }, { it.bookUrl })) ?: candidates.first()
}
