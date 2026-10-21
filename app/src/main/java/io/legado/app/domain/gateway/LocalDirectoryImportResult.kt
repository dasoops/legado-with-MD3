package io.legado.app.domain.gateway

/**
 * 目录扫描结果. 失败数用于区分“目录扫描完成但部分文件未导入”与“目录内确实没有书”.
 */
data class LocalDirectoryImportResult(
    val imported: Int,
    val failed: Int,
)
