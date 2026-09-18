package io.legado.app.ui.main

import android.content.Context
import android.content.Intent
import io.legado.app.ui.config.ConfigTag

object MainIntent {
    const val EXTRA_START_ROUTE = "startRoute"
    internal const val EXTRA_ROUTE_HOME_AS_PARENT = "routeHomeAsParent"
    const val EXTRA_CACHE_GROUP_ID = "extra_cache_group_id"
    const val EXTRA_BOOK_NAME = "name"
    const val EXTRA_BOOK_AUTHOR = "author"
    const val EXTRA_BOOK_URL = "bookUrl"
    const val EXTRA_BOOK_ORIGIN = "origin"
    const val EXTRA_BOOK_COVER = "coverPath"
    const val EXTRA_IN_BOOKSHELF = "inBookshelf"
    const val EXTRA_CHAPTER_CHANGED = "chapterChanged"
    const val EXTRA_SOURCE_URL = "sourceUrl"
    const val EXTRA_BOOK_SOURCE_IMPORT = "bookSourceImport"

    fun createLauncherIntent(context: Context): Intent {
        val launcherComponent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        return if (launcherComponent != null) {
            Intent().setComponent(launcherComponent)
        } else {
            Intent(context, MainActivity::class.java)
        }
    }

    fun createHomeIntent(context: Context): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_MAIN)
        }
    }

    fun createBookSourceManageIntent(
        context: Context,
        importSource: String? = null,
    ): Intent =
        createLauncherIntent(context).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_SOURCE_MANAGE)
            putExtra(EXTRA_BOOK_SOURCE_IMPORT, importSource)
        }

    fun createBookSourceEditIntent(context: Context, sourceUrl: String? = null): Intent =
        createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_SOURCE_EDIT)
            putExtra(EXTRA_SOURCE_URL, sourceUrl)
        }

    fun createBookSourceDebugIntent(context: Context, sourceUrl: String?): Intent =
        createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_SOURCE_DEBUG)
            putExtra(EXTRA_SOURCE_URL, sourceUrl)
        }

    fun createIntent(context: Context, configTag: String? = null): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, routeForConfigTag(configTag))
        }
    }

    fun createBookshelfManageScreenIntent(
        context: Context,
        groupId: Long = -1L
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_CACHE)
            putExtra(EXTRA_CACHE_GROUP_ID, groupId)
        }
    }

    fun createCacheIntent(
        context: Context,
        groupId: Long = -1L
    ): Intent = createBookshelfManageScreenIntent(context, groupId)

    fun createBookCacheManageIntent(context: Context): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_CACHE_MANAGE)
        }
    }

    fun createReadBookIntent(
        context: Context,
        bookUrl: String? = null,
        inBookshelf: Boolean = true,
        chapterChanged: Boolean = false,
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_READ_BOOK)
            bookUrl?.let { putExtra(EXTRA_BOOK_URL, it) }
            putExtra(EXTRA_IN_BOOKSHELF, inBookshelf)
            putExtra(EXTRA_CHAPTER_CHANGED, chapterChanged)
        }
    }

    internal fun shouldOpenRouteWithHomeParent(intent: Intent?): Boolean =
        intent?.getBooleanExtra(EXTRA_ROUTE_HOME_AS_PARENT, false) == true

    fun createBookInfoIntent(
        context: Context,
        name: String? = null,
        author: String? = null,
        bookUrl: String,
        origin: String? = null,
        coverPath: String? = null
    ): Intent {
        return createLauncherIntent(context).apply {
            putExtra(EXTRA_START_ROUTE, MainRouteConst.ROUTE_BOOK_INFO)
            putExtra(EXTRA_BOOK_NAME, name)
            putExtra(EXTRA_BOOK_AUTHOR, author)
            putExtra(EXTRA_BOOK_URL, bookUrl)
            putExtra(EXTRA_BOOK_ORIGIN, origin)
            putExtra(EXTRA_BOOK_COVER, coverPath)
        }
    }

    private fun routeForConfigTag(configTag: String?): String {
        return when (configTag) {
            ConfigTag.OTHER_CONFIG -> MainRouteConst.ROUTE_SETTINGS_OTHER
            ConfigTag.READ_CONFIG -> MainRouteConst.ROUTE_SETTINGS_READ
            ConfigTag.COVER_CONFIG -> MainRouteConst.ROUTE_SETTINGS_COVER
            ConfigTag.THEME_CONFIG -> MainRouteConst.ROUTE_SETTINGS_THEME
            ConfigTag.BACKUP_CONFIG -> MainRouteConst.ROUTE_SETTINGS_BACKUP
            ConfigTag.DOWNLOAD_CACHE_CONFIG -> MainRouteConst.ROUTE_SETTINGS_DOWNLOAD_CACHE
            else -> MainRouteConst.ROUTE_SETTINGS
        }
    }
}
