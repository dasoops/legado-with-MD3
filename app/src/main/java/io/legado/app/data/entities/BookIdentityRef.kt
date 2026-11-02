package io.legado.app.data.entities

/**
 * 书籍身份快照。阅读记录反查书籍时只需要身份与首页展示字段，
 * 用轻量投影避免为每条记录加载完整 [Book]。
 */
data class BookIdentityRef(
    val bookUrl: String,
    val name: String,
    val author: String,
    val durChapterTime: Long,
    val origin: String?,
    val coverUrl: String?,
    val customCoverUrl: String?,
    val durChapterTitle: String?,
    val totalChapterNum: Int?,
    val durChapterIndex: Int?,
) {
    val displayCover: String?
        get() = if (customCoverUrl.isNullOrEmpty()) coverUrl else customCoverUrl

    val chapterProgress: Float?
        get() =
            if (totalChapterNum != null && totalChapterNum > 0 && durChapterIndex != null) {
                (durChapterIndex + 1).coerceIn(0, totalChapterNum).toFloat() / totalChapterNum
            } else {
                null
            }
}
