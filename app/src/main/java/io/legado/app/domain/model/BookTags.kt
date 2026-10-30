package io.legado.app.domain.model

object BookTags {
    const val READ = "已读"
    const val UNREAD = "未读"
    val builtIn = setOf(READ, UNREAD)

    fun parse(value: String?): List<String> = value
        .orEmpty()
        .split(',', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    fun editable(tags: Iterable<String>): List<String> = tags
        .flatMap(::parse)
        .filterNot { it in builtIn }
        .distinct()

    fun display(
        custom: String?,
        source: String?,
        index: Int,
        position: Int,
        total: Int,
    ): List<String> = (
        editable(parse(custom) + parse(source)) +
            buildList {
                if (index == 0 && position == 0) add(UNREAD)
                if (total > 0 && index >= total - 1) add(READ)
            }
        ).distinct()

    // grouping 在书架流程里按书调用, 正则必须提前编译: 每本现编译两个 Pattern,
    // 千本书的一次数据发射在主线程要几百毫秒.
    private val sizeTagRegex = Regex("""^\d[\d,.]*\s*(b|kb|mb|gb|tb|m|g|t)$""", RegexOption.IGNORE_CASE)
    private val wordCountTagRegex = Regex(""".*?\d.*字$""")

    fun grouping(
        custom: String?,
        source: String?,
        index: Int,
        position: Int,
        total: Int,
    ): List<String> {
        val sourceTags =
            parse(source).filterNot {
                sizeTagRegex.matches(it) || wordCountTagRegex.matches(it)
            }
        return display(custom, sourceTags.joinToString(","), index, position, total)
    }

    fun directoryNames(path: String): List<String> = path
        .replace('\\', '/')
        .split('/')
        .dropLast(1)
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.endsWith(':') }
        .distinct()

    // 使用完整标签名的稳定散列, 避开系统 ID 和目录分组位的取值空间.
    fun groupId(tag: String): Long {
        var hash = -3750763034362895579L
        tag.forEach { hash = (hash xor it.code.toLong()) * 1099511628211L }
        return -1024L - (hash and 0x3fffffffffffffffL)
    }
}
