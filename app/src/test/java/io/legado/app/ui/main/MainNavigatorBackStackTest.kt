package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigatorBackStackTest {
    @Test
    fun `reader with reset to home replaces stale route with home parent`() {
        val bookInfo = MainRouteBookInfo("Book", "Author", "book-url")
        val reader = MainRouteReadBook(bookUrl = "book-url")
        val backStack = mutableListOf<NavKey>(MainRouteBookshelf, bookInfo)

        MainNavigator.navigateToRoute(backStack, reader, resetToHome = true)

        assertEquals(listOf(MainRouteBookshelf, reader), backStack)
    }

    @Test
    fun `regular reader keeps book info as parent`() {
        val bookInfo = MainRouteBookInfo("Book", "Author", "book-url")
        val reader = MainRouteReadBook(bookUrl = "book-url", chapterChanged = true)
        val backStack = mutableListOf<NavKey>(MainRouteBookshelf, bookInfo)

        MainNavigator.navigateToRoute(backStack, reader)

        assertEquals(listOf(MainRouteBookshelf, bookInfo, reader), backStack)
    }

    @Test
    fun `reader opened from main screen keeps bookshelf as parent`() {
        // 首页/阅读记录入口改为应用内导航后, 返回应回到主界面而不是退出应用.
        val reader = MainRouteReadBook(bookUrl = "book-url", sharedCoverKey = "home_recent_book-url")
        val backStack = mutableListOf<NavKey>(MainRouteBookshelf)

        MainNavigator.navigateToRoute(backStack, reader)

        assertEquals(listOf(MainRouteBookshelf, reader), backStack)
    }
}
