package io.legado.app.domain.gateway

/**
 * 目录扫描结果. 失败数只统计非预期异常; 空文件和加密 PDF 属于可预期跳过,
 * 分开计数是为了提示准确原因, 避免把无法修复的文件报成导入失败.
 */
data class LocalDirectoryImportResult(
    val imported: Int,
    val failed: Int,
    val skippedEmpty: Int = 0,
    val skippedEncryptedPdf: Int = 0,
)
