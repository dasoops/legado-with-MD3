package io.legado.app.ui.main

import android.app.Activity
import android.content.Intent
import androidx.navigation3.runtime.NavKey
import io.legado.app.model.ReadBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object MainNavigator {

    var backNavigationInProgress = false
        private set
    private val navigationScope by lazy(LazyThreadSafetyMode.NONE) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    private var backNavigationResetJob: Job? = null

    fun navigateToRoute(
        backStack: MutableList<NavKey>,
        route: NavKey,
        resetToHome: Boolean = false,
    ) {
        if (resetToHome) {
            backStack.clear()
            backStack.add(MainRouteBookshelf)
        }
        val currentRoute = backStack.lastOrNull()
        if (currentRoute == route) return

        if (route is MainRouteReadManga) {
            val existingReaderIndex = backStack.indexOfLast { it is MainRouteReadManga }
            if (existingReaderIndex >= 0) {
                while (backStack.lastIndex > existingReaderIndex) {
                    backStack.removeAt(backStack.lastIndex)
                }
                backStack[existingReaderIndex] = route
                return
            }
        }

        // 导航动画和阅读页组合要花几百毫秒, 这段时间足够把正文读出来并排版好
        if (route is MainRouteReadBook && !route.chapterChanged) {
            route.bookUrl?.let { ReadBook.prefetchForOpen(it) }
        }

        when (route) {
            is MainRouteBookSourceManage,
            is MainRouteBookSourceEdit,
            is MainRouteBookSourceDebug -> backStack.add(route)

            MainRouteBookshelf -> {
                backStack.clear()
                backStack.add(MainRouteBookshelf)
            }

            MainRouteSettings -> {
                if (currentRoute == MainRouteBookshelf) {
                    backStack.add(MainRouteSettings)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteBookshelf)
                    backStack.add(MainRouteSettings)
                }
            }

            MainRouteSettingsOther,
            MainRouteSettingsRead,
            MainRouteSettingsCover,
            MainRouteSettingsTheme,
            MainRouteSettingsBackup,
            MainRouteSettingsCustomTheme,
            MainRouteSettingsThemeManage,
            MainRouteSettingsDownloadCache -> {
                backStack.clear()
                backStack.add(MainRouteBookshelf)
                backStack.add(MainRouteSettings)
                backStack.add(route)
            }

            MainRouteImportLocal,
            MainRouteImportRemote,
            is MainRouteCache,
            MainRouteBookCacheManage,
            is MainRouteReadBook,
            is MainRouteReadManga -> {
                if (
                    currentRoute == MainRouteBookshelf ||
                    currentRoute is MainRouteBookInfo
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteBookshelf)
                    backStack.add(route)
                }
            }

            is MainRouteSearchContent -> {
                backStack.add(route)
            }

            is MainRouteBookInfo -> {
                if (
                    currentRoute == MainRouteBookshelf ||
                    currentRoute is MainRouteBookInfo ||
                    currentRoute is MainRouteCache ||
                    currentRoute is MainRouteReadManga
                ) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteBookshelf)
                    backStack.add(route)
                }
            }

            MainRouteHighlightTagRule,
            MainRouteReadRecord -> {
                if (currentRoute == MainRouteBookshelf) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteBookshelf)
                    backStack.add(route)
                }
            }

            MainRouteAbout -> {
                if (currentRoute == MainRouteBookshelf) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteBookshelf)
                    backStack.add(route)
                }
            }

            MainRouteReadRecordOverview -> {
                if (currentRoute == MainRouteBookshelf || currentRoute == MainRouteReadRecord) {
                    backStack.add(route)
                } else {
                    backStack.clear()
                    backStack.add(MainRouteBookshelf)
                    backStack.add(route)
                }
            }
        }
    }

    fun navigateBack(activity: Activity, backStack: MutableList<NavKey>) {
        if (backNavigationInProgress) {
            return
        }
        if (backStack.size > 1) {
            backNavigationInProgress = true
            backStack.removeLastOrNull()
        } else {
            activity.finish()
        }
    }

    fun onBackStackChanged() {
        backNavigationResetJob?.cancel()
        backNavigationResetJob = navigationScope.launch {
            delay(500)
            backNavigationInProgress = false
        }
    }

    fun resolveStartRoute(intent: Intent?): NavKey {
        val route = intent?.getStringExtra(MainIntent.EXTRA_START_ROUTE)
        return resolveStartRoute(route, intent)
    }

    private fun resolveStartRoute(route: String?, intent: Intent?): MainRoute {
        return when (route) {
            MainRouteConst.ROUTE_MAIN -> MainRouteBookshelf
            MainRouteConst.ROUTE_BOOK_SOURCE_MANAGE -> MainRouteBookSourceManage(
                intent?.getStringExtra(MainIntent.EXTRA_BOOK_SOURCE_IMPORT)
            )
            MainRouteConst.ROUTE_BOOK_SOURCE_EDIT -> MainRouteBookSourceEdit(
                intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
            )

            MainRouteConst.ROUTE_BOOK_SOURCE_DEBUG -> MainRouteBookSourceDebug(
                intent?.getStringExtra(MainIntent.EXTRA_SOURCE_URL)
            )

            MainRouteConst.ROUTE_SETTINGS -> MainRouteSettings
            MainRouteConst.ROUTE_SETTINGS_OTHER -> MainRouteSettingsOther
            MainRouteConst.ROUTE_SETTINGS_READ -> MainRouteSettingsRead
            MainRouteConst.ROUTE_SETTINGS_COVER -> MainRouteSettingsCover
            MainRouteConst.ROUTE_SETTINGS_THEME -> MainRouteSettingsTheme
            MainRouteConst.ROUTE_SETTINGS_BACKUP -> MainRouteSettingsBackup
            MainRouteConst.ROUTE_SETTINGS_CUSTOM_THEME -> MainRouteSettingsCustomTheme
            MainRouteConst.ROUTE_SETTINGS_LAB_CONFIG -> MainRouteSettingsLabConfig
            MainRouteConst.ROUTE_SETTINGS_DOWNLOAD_CACHE -> MainRouteSettingsDownloadCache
            MainRouteConst.ROUTE_IMPORT_LOCAL -> MainRouteImportLocal
            MainRouteConst.ROUTE_IMPORT_REMOTE -> MainRouteImportRemote
            MainRouteConst.ROUTE_CACHE -> MainRouteCache(
                intent?.getLongExtra(
                    MainIntent.EXTRA_CACHE_GROUP_ID,
                    -1L
                ) ?: -1L
            )

            MainRouteConst.ROUTE_BOOK_CACHE_MANAGE -> MainRouteBookCacheManage
            MainRouteConst.ROUTE_READ_BOOK -> MainRouteReadBook(
                bookUrl = intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL),
                inBookshelf = intent?.getBooleanExtra(MainIntent.EXTRA_IN_BOOKSHELF, true) != false,
                chapterChanged = intent?.getBooleanExtra(
                    MainIntent.EXTRA_CHAPTER_CHANGED,
                    false
                ) == true,
            )
            MainRouteConst.ROUTE_READ_MANGA -> MainRouteReadManga(
                bookUrl = intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL),
                inBookshelf = intent?.getBooleanExtra(MainIntent.EXTRA_IN_BOOKSHELF, true) != false,
                chapterChanged = intent?.getBooleanExtra(
                    MainIntent.EXTRA_CHAPTER_CHANGED,
                    false,
                ) == true,
                openRequestId = System.nanoTime(),
            )
            MainRouteConst.ROUTE_BOOK_INFO -> intent?.getStringExtra(MainIntent.EXTRA_BOOK_URL)
                ?.takeIf { it.isNotBlank() }
                ?.let { bookUrl ->
                    MainRouteBookInfo(
                        name = intent.getStringExtra(MainIntent.EXTRA_BOOK_NAME),
                        author = intent.getStringExtra(MainIntent.EXTRA_BOOK_AUTHOR),
                        bookUrl = bookUrl,
                        origin = intent.getStringExtra(MainIntent.EXTRA_BOOK_ORIGIN),
                        coverPath = intent.getStringExtra(MainIntent.EXTRA_BOOK_COVER)
                    )
                } ?: MainRouteBookshelf

            MainRouteConst.ROUTE_ABOUT -> MainRouteAbout

            else -> MainRouteBookshelf
        }
    }
}
