package io.legado.app.ui.main

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigatorReadAloudTest {

    @Test
    fun `opens cloud TTS manager on top of reader`() {
        val reader = MainRouteReadBook(bookUrl = "book")
        val cloudTts = MainRouteCloudTtsEngines(bookUrl = "book")
        val backStack = mutableListOf<NavKey>(MainRouteBookshelf, reader)

        MainNavigator.navigateToRoute(backStack, MainRouteCloudTtsEngines())

        assertEquals(listOf(MainRouteBookshelf, reader, MainRouteCloudTtsEngines()), backStack)
    }

    @Test
    fun `resets to home before cloud TTS manager from unrelated route`() {
        val cloudTts = MainRouteCloudTtsEngines()
        val backStack = mutableListOf<NavKey>(
            MainRouteBookshelf,
            MainRouteSettings,
        )

        MainNavigator.navigateToRoute(backStack, MainRouteCloudTtsEngines())

        assertEquals(listOf(MainRouteBookshelf, MainRouteCloudTtsEngines()), backStack)
    }
}
