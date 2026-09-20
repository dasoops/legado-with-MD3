package io.legado.app.domain.gateway

interface LocalDirectoryGateway {

    /** 目录显示名 (最后一级), 无法解析返回 null */
    suspend fun directoryName(rootUri: String): String?

    /** 递归扫描 rootUri 内书籍并导入书架、归入 groupId, 返回本次处理的书籍数 */
    suspend fun importDirectoryToGroup(groupId: Long, rootUri: String): Int

    /** bookUrl 相对于 rootUri 的目录层级 (不含文件名), 解析失败返回空 */
    fun relativeDirectory(rootUri: String, bookUrl: String): List<String>
}
