package io.legado.app.data.entities.readRecord

/** 首页最近阅读使用的阅读记录条目：书籍信息在仓储层按归一化身份解析。 */
data class RecentReadRecordRow(
    val recordName: String,
    val recordAuthor: String,
    val lastRead: Long,
)
