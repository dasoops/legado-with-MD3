package io.legado.app.domain.model

object AdvancedGroup {
    // 序列化文本的字段顺序和分隔符是已落库正则的隐式契约, 调整会使旧正则失效.
    private fun sanitize(value: String) = value
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace('\t', ' ')
        .trim()

    fun serialize(
        name: String,
        author: String,
        tags: List<String>,
    ): String = "name=${sanitize(name)}\tauthor=${sanitize(author)}\ttags=${
        tags.joinToString(",") { sanitize(it) }
    }"
}
