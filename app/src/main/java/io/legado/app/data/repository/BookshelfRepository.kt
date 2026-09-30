package io.legado.app.data.repository

import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.utils.cnCompare
import kotlin.math.max

class BookshelfRepository {
    fun sortBooks(
        list: List<BookShelfItem>,
        sort: Int,
        sortOrder: Int,
    ): List<BookShelfItem> = list.sortedWith(bookComparator(sort, sortOrder))

    /**
     * 书架排序比较器. 本地目录等自持列表的视图复用同一规则, 保证各页排序口径一致.
     */
    fun bookComparator(
        sort: Int,
        sortOrder: Int,
    ): Comparator<BookShelfItem> {
        val isDescending = sortOrder == 1
        return when (sort) {
            1 -> {
                if (isDescending) {
                    compareByDescending { it.latestChapterTime }
                } else {
                    compareBy { it.latestChapterTime }
                }
            }
            2 -> {
                if (isDescending) {
                    Comparator { o1, o2 -> o2.name.cnCompare(o1.name) }
                } else {
                    Comparator { o1, o2 -> o1.name.cnCompare(o2.name) }
                }
            }
            3 -> {
                if (isDescending) {
                    compareByDescending { it.order }
                } else {
                    compareBy { it.order }
                }
            }
            4 -> {
                if (isDescending) {
                    compareByDescending {
                        max(it.latestChapterTime, it.durChapterTime)
                    }
                } else {
                    compareBy { max(it.latestChapterTime, it.durChapterTime) }
                }
            }
            5 -> {
                if (isDescending) {
                    Comparator { o1, o2 -> o2.author.cnCompare(o1.author) }
                } else {
                    Comparator { o1, o2 -> o1.author.cnCompare(o2.author) }
                }
            }
            else -> {
                if (isDescending) {
                    compareByDescending { it.durChapterTime }
                } else {
                    compareBy { it.durChapterTime }
                }
            }
        }
    }
}
