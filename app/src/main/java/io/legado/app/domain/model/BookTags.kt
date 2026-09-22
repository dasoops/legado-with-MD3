package io.legado.app.domain.model

object BookTags {
    const val READ = "已读"
    const val UNREAD = "未读"
    val builtIn = setOf(READ, UNREAD)
    // 格式标签需要始终提供对应的内置分组, 但仍属于书籍可见标签.
    val builtInGroupTags = builtIn + setOf("txt", "epub", "umd", "pdf", "mobi")

    fun parse(value: String?): List<String> = value.orEmpty().split(',', '\n')
        .map(String::trim).filter(String::isNotEmpty).distinct()

    fun editable(tags: Iterable<String>): List<String> = tags.flatMap(::parse)
        .filterNot { it in builtIn }.distinct()

    fun display(custom: String?, source: String?, index: Int, position: Int, total: Int): List<String> =
        (editable(parse(custom) + parse(source)) + buildList {
            if (index == 0 && position == 0) add(UNREAD)
            if (total > 0 && index >= total - 1) add(READ)
        }).distinct()

    fun grouping(custom: String?, source: String?, index: Int, position: Int, total: Int): List<String> {
        val metadata = Regex("""^\d[\d,.]*\s*(b|kb|mb|gb|tb|m|g|t)$""", RegexOption.IGNORE_CASE)
        val wordCount = Regex(""".*?\d.*字$""")
        val sourceTags = parse(source).filterNot {
            metadata.matches(it) || wordCount.matches(it)
        }
        return display(custom, sourceTags.joinToString(","), index, position, total)
    }

    fun directoryNames(path: String): List<String> = path.replace('\\', '/')
        .split('/').dropLast(1).map(String::trim)
        .filter { it.isNotEmpty() && !it.endsWith(':') }.distinct()

    // 使用完整标签名的稳定散列, 避开系统 ID 和目录分组位的取值空间.
    fun groupId(tag: String): Long {
        var hash = -3750763034362895579L
        tag.forEach { hash = (hash xor it.code.toLong()) * 1099511628211L }
        return -1024L - (hash and 0x3fffffffffffffffL)
    }
}
