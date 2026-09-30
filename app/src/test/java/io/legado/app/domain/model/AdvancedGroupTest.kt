package io.legado.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AdvancedGroupTest {
    private data class SampleBook(
        val name: String,
        val author: String,
        val tags: List<String>,
    )

    private val shelf =
        listOf(
            SampleBook("三体", "刘慈欣", listOf("科幻", "宇宙", "已读")),
            SampleBook("诡秘之主", "爱潜水的乌贼", listOf("奇幻", "冒险", "未读")),
            SampleBook("庆余年", "猫腻", listOf("历史", "权谋", "已读")),
            SampleBook("凡人修仙传", "忘语", listOf("仙侠", "修真", "未读")),
            SampleBook("明朝那些事儿", "当年明月", listOf("历史", "纪实", "已读")),
        )

    private fun matchedNames(pattern: String): List<String> {
        val regex = Regex(pattern)
        return shelf
            .filter { regex.containsMatchIn(AdvancedGroup.serialize(it.name, it.author, it.tags)) }
            .map { it.name }
    }

    @Test
    fun `序列化字段顺序与分隔符固定`() {
        assertEquals(
            "name=三体\tauthor=刘慈欣\ttags=科幻,宇宙,已读",
            AdvancedGroup.serialize("三体", "刘慈欣", listOf("科幻", "宇宙", "已读")),
        )
    }

    @Test
    fun `换行与制表符折叠为空格并 trim`() {
        assertEquals(
            "name=三 体\tauthor=刘 慈 欣\ttags=科 幻,宇宙",
            AdvancedGroup.serialize(" 三\n体 ", "刘\t慈\r欣", listOf("科\n幻", " 宇宙 ")),
        )
    }

    @Test
    fun `按作者精确匹配`() {
        assertEquals(listOf("三体"), matchedNames("author=刘慈欣"))
    }

    @Test
    fun `按标签匹配`() {
        assertEquals(listOf("庆余年", "明朝那些事儿"), matchedNames("tags=.*历史.*"))
    }

    @Test
    fun `作者接标签组合匹配`() {
        assertEquals(listOf("诡秘之主"), matchedNames("author=爱潜水的乌贼.*tags=.*未读"))
    }

    @Test
    fun `按书名分组匹配`() {
        assertEquals(listOf("三体", "凡人修仙传"), matchedNames("name=(三体|凡人修仙传)"))
    }

    @Test
    fun `作者分组接已读标签匹配`() {
        assertEquals(listOf("三体", "庆余年"), matchedNames("author=(刘慈欣|猫腻).*tags=.*已读"))
    }

    @Test
    fun `前瞻同时约束历史与已读标签`() {
        assertEquals(
            listOf("庆余年", "明朝那些事儿"),
            matchedNames("(?=.*tags=.*历史)(?=.*tags=.*已读)"),
        )
    }
}
