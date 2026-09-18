package io.legado.app.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import io.legado.app.R
import io.legado.app.constant.BookType
import io.legado.app.constant.Status
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.help.coil.CoverExtras
import io.legado.app.model.Download
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.about.AboutEffect
import io.legado.app.ui.about.AboutScreen
import io.legado.app.ui.about.AboutViewModel
import io.legado.app.ui.book.cache.manage.BookCacheManageRouteScreen
import io.legado.app.ui.book.import.local.ImportBookRouteScreen
import io.legado.app.ui.book.import.remote.RemoteBookRouteScreen
import io.legado.app.ui.book.info.BookInfoRouteScreen
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.manage.BookshelfManageRouteScreen
import io.legado.app.ui.book.manga.MangaReaderRouteScreen
import io.legado.app.ui.book.manga.MangaReaderViewModel
import io.legado.app.ui.book.read.ReadBookController
import io.legado.app.ui.book.read.ReadBookInitRequest
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookRouteScreen
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.read.ReaderSessionViewModel
import io.legado.app.ui.book.readRecord.ReadRecordOverviewRouteScreen
import io.legado.app.ui.book.readRecord.ReadRecordRouteScreen
import io.legado.app.ui.book.searchContent.SearchContentRouteScreen
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.source.debug.BookSourceDebugRoute
import io.legado.app.ui.book.source.debug.BookSourceDebugViewModel
import io.legado.app.ui.book.source.edit.BookSourceEditRoute
import io.legado.app.ui.book.source.edit.BookSourceEditViewModel
import io.legado.app.ui.book.source.manage.BookSourceRouteScreen
import io.legado.app.ui.browser.WebViewModel
import io.legado.app.ui.browser.WebViewRouteScreen
import io.legado.app.ui.config.ConfigNavScreen
import io.legado.app.ui.config.backupConfig.BackupConfigRouteScreen
import io.legado.app.ui.config.coverConfig.CoverAlbumManageRouteScreen
import io.legado.app.ui.config.coverConfig.CoverConfigRouteScreen
import io.legado.app.ui.config.customTheme.CustomThemeRouteScreen
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigRouteScreen
import io.legado.app.ui.config.labConfig.LabConfigRouteScreen
import io.legado.app.ui.config.otherConfig.OtherConfigRouteScreen
import io.legado.app.ui.config.readConfig.ReadConfigRouteScreen
import io.legado.app.ui.config.themeConfig.ThemeConfigRouteScreen
import io.legado.app.ui.config.themeManage.ThemeManageRouteScreen
import io.legado.app.ui.highlightTagRule.HighlightTagRuleRouteScreen
import io.legado.app.ui.login.SourceLoginIntent
import io.legado.app.ui.login.SourceLoginRoute
import io.legado.app.ui.login.SourceLoginType
import io.legado.app.ui.login.SourceLoginViewModel
import io.legado.app.ui.theme.ProvideThemeOverride
import io.legado.app.ui.theme.rememberImageSeedColor
import io.legado.app.ui.theme.rememberThemeOverride
import io.legado.app.ui.widget.components.changeSource.ChangeSourceSheet
import io.legado.app.utils.openUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * WebView 类页面（内置浏览器）只做位移转场。
 *
 * WebView 是 AndroidView interop view：所在子树一旦被加上 graphicsLayer（fade 的 alpha、
 * scaleOut 的缩放），Compose 会把网页一并画进离屏 RenderNode
 * （`AndroidViewHolder.draw` → `AndroidComposeView.drawAndroidView`），Chromium 在这条绘制路径上
 * 不稳定，部分设备会表现为网页闪烁。`slideIntoContainer` / `slideOutOfContainer` 只改 layout
 * offset、不产生图层，所以这里保留默认的位移与时长，去掉 fade 与 scale。
 */
private fun webViewEntryMetadata(predictiveBackEnabled: Boolean) = metadata {
    put(NavDisplay.TransitionKey) {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            initialOffset = { fullWidth -> fullWidth }
        ) togetherWith slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            targetOffset = { fullWidth -> fullWidth / 4 }
        )
    }
    put(NavDisplay.PopTransitionKey) {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            initialOffset = { fullWidth -> -fullWidth / 4 }
        ) togetherWith slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            targetOffset = { fullWidth -> fullWidth }
        )
    }
    if (predictiveBackEnabled) {
        put(NavDisplay.PredictivePopTransitionKey) { _ ->
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> -fullWidth / 4 }
            ) togetherWith slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(easing = FastOutSlowInEasing),
                targetOffset = { fullWidth -> fullWidth }
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
fun MainActivity.mainEntryProvider(
    backStack: MutableList<NavKey>,
    configuration: AppUiConfiguration,
    showMangaUi: Boolean,
    useRail: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    onNavigateToRoute: (NavKey) -> Unit,
    onNavigateBack: () -> Unit,
) = entryProvider<NavKey> {
    entry<MainRouteWebView>(
        metadata = webViewEntryMetadata(configuration.appShell.predictiveBackEnabled)
    ) { route ->
        val viewModel = koinViewModel<WebViewModel>(
            key = "WebView:${route.url}:${route.sourceOrigin}:${route.sourceVerificationEnable}",
        )
        val browserIntent = remember(route) {
            Intent().apply {
                putExtra("title", route.title)
                putExtra("url", route.url)
                putExtra("sourceOrigin", route.sourceOrigin)
                putExtra("sourceName", route.sourceName)
                route.sourceType?.let { putExtra("sourceType", it) }
                putExtra("sourceVerificationEnable", route.sourceVerificationEnable)
                putExtra("refetchAfterSuccess", route.refetchAfterSuccess)
                putExtra("html", route.html)
            }
        }
        WebViewRouteScreen(
            intent = browserIntent,
            viewModel = viewModel,
            onFinish = onNavigateBack,
            onImportBookSource = { importUrl ->
                onNavigateToRoute(MainRouteBookSourceManage(importUrl))
            },
        )
    }
    entry<MainRouteSourceLogin>(
        metadata = ModalOverlaySceneStrategy.modalOverlay(),
    ) { route ->
        DisposableEffect(route) {
            MainActivity.hasActiveSourceLoginRoute = true
            onDispose {
                MainActivity.hasActiveSourceLoginRoute = false
            }
        }
        val viewModel = koinViewModel<SourceLoginViewModel>(
            key = "SourceLogin:${route.type}:${route.sourceKey}:${route.bookUrl}",
        )
        SourceLoginRoute(
            request = SourceLoginIntent.Initialize(route.type, route.sourceKey, route.bookUrl),
            viewModel = viewModel,
            host = this@mainEntryProvider,
            onBack = onNavigateBack,
        )
    }
    entry<MainRouteBookSourceManage> { route ->
        BookSourceRouteScreen(
            initialImportUrl = route.importUrl,
            closeAfterImport = route.importUrl != null,
            onImportClosed = onNavigateBack,
            onBackClick = onNavigateBack,
            onAddSource = { onNavigateToRoute(MainRouteBookSourceEdit()) },
            onEditSource = { onNavigateToRoute(MainRouteBookSourceEdit(it)) },
            onLoginSource = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, it))
            },
            onDebugSource = { sourceUrl ->
                onNavigateToRoute(MainRouteBookSourceDebug(sourceUrl))
            },
        )
    }
    entry<MainRouteBookSourceEdit> { route ->
        val viewModel = koinViewModel<BookSourceEditViewModel>(
            key = "BookSourceEdit:${route.sourceUrl.orEmpty()}",
        )
        BookSourceEditRoute(
            sourceUrl = route.sourceUrl,
            viewModel = viewModel,
            onBack = { savedSourceUrl ->
                if (backStack.size == 1) {
                    savedSourceUrl?.let {
                        this@mainEntryProvider.setResult(
                            android.app.Activity.RESULT_OK,
                            Intent().putExtra("origin", it),
                        )
                    }
                    this@mainEntryProvider.finish()
                } else onNavigateBack()
            },
            onLogin = {
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, it))
            },
            onDebug = { onNavigateToRoute(MainRouteBookSourceDebug(it)) },
        )
    }
    entry<MainRouteBookSourceDebug> { route ->
        val viewModel = koinViewModel<BookSourceDebugViewModel>(
            key = "BookSourceDebug:${route.sourceUrl.orEmpty()}",
        )
        BookSourceDebugRoute(route.sourceUrl, viewModel, onNavigateBack)
    }
    entry<MainRouteBookshelf> {
        val mainViewModel = koinViewModel<MainViewModel>()
        val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
        MainScreen(
            mainUiState = mainUiState,
            onIntent = mainViewModel::onIntent,
            effects = mainViewModel.effects,
            useRail = useRail,
            onOpenSettings = {
                onNavigateToRoute(MainRouteSettings)
            },
            onNavigateToRemoteImport = {
                onNavigateToRoute(MainRouteImportRemote)
            },
            onNavigateToLocalImport = {
                onNavigateToRoute(MainRouteImportLocal)
            },
            onNavigateToCache = { groupId ->
                onNavigateToRoute(MainRouteCache(groupId))
            },
            onNavigateToBookCacheManage = {
                onNavigateToRoute(MainRouteBookCacheManage)
            },
            onOpenBookshelfBook = { book, sharedCoverKey ->
                if (book.isAudio) {
                    this@mainEntryProvider.startActivityForBook(book)
                } else if (!book.isLocal && book.isImage && showMangaUi) {
                    onNavigateToRoute(MainRouteReadManga(bookUrl = book.bookUrl))
                } else {
                    onNavigateToRoute(
                        MainRouteReadBook(
                            bookUrl = book.bookUrl,
                            sharedCoverKey = sharedCoverKey,
                        )
                    )
                }
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath, sharedCoverKey ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = name,
                        author = author,
                        bookUrl = bookUrl,
                        origin = origin,
                        coverPath = coverPath,
                        sharedCoverKey = sharedCoverKey
                    )
                )
            },
            onNavigateToBookSourceManage = {
                onNavigateToRoute(MainRouteBookSourceManage())
            },
            onNavigateToReadRecord = {
                onNavigateToRoute(MainRouteReadRecord)
            },
            onNavigateToHighlightTagRule = {
                onNavigateToRoute(MainRouteHighlightTagRule)
            },
            onNavigateToAbout = {
                onNavigateToRoute(MainRouteAbout)
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
        )
    }

    entry<MainRouteSettings> {
        ConfigNavScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToOther = { backStack.add(MainRouteSettingsOther) },
            onNavigateToRead = { backStack.add(MainRouteSettingsRead) },
            onNavigateToCover = { backStack.add(MainRouteSettingsCover) },
            onNavigateToTheme = { backStack.add(MainRouteSettingsTheme) },
            onNavigateToBackup = { backStack.add(MainRouteSettingsBackup) },
            onNavigateToDownloadCache = { backStack.add(MainRouteSettingsDownloadCache) },
            onNavigateToLab = { backStack.add(MainRouteSettingsLabConfig) }
        )
    }

    entry<MainRouteSettingsOther> {
        OtherConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsRead> {
        ReadConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsCover> {
        CoverConfigRouteScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToCoverAlbums = {
                backStack.add(MainRouteSettingsCoverAlbums)
            },
        )
    }

    entry<MainRouteSettingsCoverAlbums> {
        CoverAlbumManageRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsTheme> {
        ThemeConfigRouteScreen(
            onBackClick = { onNavigateBack() },
            onNavigateToCustomTheme = { backStack.add(MainRouteSettingsCustomTheme) },
            onNavigateToThemeManage = { backStack.add(MainRouteSettingsThemeManage) }
        )
    }

    entry<MainRouteSettingsBackup> {
        BackupConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsDownloadCache> {
        DownloadCacheConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsLabConfig> {
        LabConfigRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteSettingsCustomTheme> {
        CustomThemeRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteSettingsThemeManage> {
        ThemeManageRouteScreen(onBackClick = { onNavigateBack() })
    }

    entry<MainRouteImportLocal> {
        ImportBookRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteImportRemote> {
        RemoteBookRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteCache> { route ->
        BookshelfManageRouteScreen(
            groupId = route.groupId,
            onBackClick = { onNavigateBack() },
            onOpenBookInfo = { name, author, bookUrl ->
                onNavigateToRoute(
                    MainRouteBookInfo(
                        name = name,
                        author = author,
                        bookUrl = bookUrl
                    )
                )
            }
        )
    }

    entry<MainRouteBookCacheManage> {
        BookCacheManageRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteReadBook>(
        metadata = metadata {
            put(NavDisplay.TransitionKey) {
                fadeIn(animationSpec = tween(600)) togetherWith
                        fadeOut(animationSpec = tween(600))
            }
            put(NavDisplay.PopTransitionKey) {
                fadeIn(animationSpec = tween(600)) togetherWith
                        fadeOut(animationSpec = tween(600))
            }
            if (configuration.appShell.predictiveBackEnabled) {
                put(NavDisplay.PredictivePopTransitionKey) { _ ->
                    fadeIn(animationSpec = tween(600)) togetherWith
                            fadeOut(animationSpec = tween(600))
                }
            }
        }
    ) { route ->
        val readBookViewModel = koinViewModel<ReadBookViewModel>(
            key = "ReadBook:${route.bookUrl ?: "last-read"}"
        )
        val readerSessionViewModel = koinViewModel<ReaderSessionViewModel>(
            key = "ReaderSession:${route.bookUrl ?: "last-read"}"
        )
        val controller = remember(readBookViewModel, readerSessionViewModel) {
            ReadBookController(
                this@mainEntryProvider,
                readBookViewModel,
                readerSessionViewModel,
            )
        }
        // Canvas 阅读面在首次组合时就会请求分页，必须先告诉 ViewModel 本路由要打开哪本书。
        // 刻意用 remember 而非 LaunchedEffect：后者在组合之后才跑，赶不上首帧。
        @Suppress("RememberReturnType")
        remember(readBookViewModel, route) {
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        val initRequest = remember(route) {
            ReadBookInitRequest(
                bookUrl = route.bookUrl,
                inBookshelf = route.inBookshelf,
                chapterChanged = route.chapterChanged,
            )
        }
        val effectsReady = remember(readBookViewModel) { CompletableDeferred<Unit>() }
        val readerResumeState = remember(controller, lifecycleOwner) { booleanArrayOf(false) }
        val collectorReady = remember(readBookViewModel) { booleanArrayOf(false) }
        fun resumeReader() {
            if (readerResumeState[0]) return
            readerResumeState[0] = true
            controller.onResume()
            readBookViewModel.onIntent(ReadBookIntent.OnResume)
        }

        fun pauseReader() {
            if (!readerResumeState[0]) return
            readerResumeState[0] = false
            controller.onPause()
            readBookViewModel.onIntent(ReadBookIntent.OnPause)
        }

        ReadBookRouteScreen(
            viewModel = readBookViewModel,
            readerSessionViewModel = readerSessionViewModel,
            host = controller,
            controller = controller,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            sharedCoverKey = route.sharedCoverKey,
            onEffectsReady = { effectsReady.complete(Unit) },
            onOpenSearch = { word, bookUrl, autoFocus ->
                onNavigateToRoute(
                    MainRouteSearchContent(
                        bookUrl = bookUrl,
                        searchWord = word,
                        searchResultIndex = readBookViewModel.uiState.value.searchResultIndex,
                        autoFocus = autoFocus,
                    )
                )
            },
        )

        DisposableEffect(controller, lifecycleOwner) {
            activeReadBookInputHandler = controller
            activeReadBookRoute = route
            MainActivity.hasActiveReadBookRoute = true
            controller.onClose = { onNavigateBack() }

            val lifecycleObserver = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (collectorReady[0]) resumeReader()
                    }
                    Lifecycle.Event.ON_PAUSE -> pauseReader()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
            onDispose {
                pauseReader()
                readBookViewModel.onIntent(ReadBookIntent.OnDispose)
                lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                if (activeReadBookInputHandler === controller) {
                    activeReadBookInputHandler = null
                }
                if (activeReadBookRoute == route) {
                    activeReadBookRoute = null
                }
                MainActivity.hasActiveReadBookRoute = false
                controller.releaseReader()
                this@mainEntryProvider.toggleSystemBar(configuration.appShell.showStatusBar)
            }
        }

        LaunchedEffect(route, readBookViewModel, lifecycleOwner) {
            // Resolving the book and applying its read style do not depend on launcher effects.
            // Start that I/O immediately; initData still waits below because it can emit effects.
            val initialBook = readBookViewModel.initReadBookConfig(initRequest)
            effectsReady.await()
            collectorReady[0] = true
            readBookViewModel.initData(initRequest, initialBook) {
                readBookViewModel.markJustInitData()
                controller.onRouteInitialized()
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    resumeReader()
                }
            }
        }
    }

    entry<MainRouteReadManga> { route ->
        val mangaViewModel = koinViewModel<MangaReaderViewModel>(
            key = "ReadManga:${route.bookUrl ?: "last-read"}",
        )
        MangaReaderRouteScreen(
            bookUrl = route.bookUrl,
            inBookshelf = route.inBookshelf,
            chapterChanged = route.chapterChanged,
            openRequestId = route.openRequestId,
            viewModel = mangaViewModel,
            restoreSystemBarsVisible = configuration.appShell.showStatusBar,
            onFinish = { onNavigateBack() },
            onOpenBookInfo = { name, author, bookUrl ->
                onNavigateToRoute(MainRouteBookInfo(name, author, bookUrl))
            },
            onOpenSourceLogin = { sourceUrl ->
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, sourceUrl))
            },
            onOpenSourceEdit = { sourceUrl ->
                onNavigateToRoute(MainRouteBookSourceEdit(sourceUrl))
            },
            onOpenWebView = { title, url, sourceOrigin, sourceName, sourceType ->
                onNavigateToRoute(
                    MainRouteWebView(title, url, sourceOrigin, sourceName, sourceType)
                )
            },
        )
    }

    entry<MainRouteSearchContent> { route ->
        val viewModel = koinViewModel<SearchContentViewModel>(
            key = "SearchContent:${route.bookUrl}",
            parameters = { parametersOf(route) }
        )
        SearchContentRouteScreen(
            viewModel = viewModel,
            autoFocus = route.autoFocus,
            onBack = { onNavigateBack() },
        )
    }

    entry<MainRouteReadRecord> {
        ReadRecordRouteScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    if (book != null) this@mainEntryProvider.startActivityForBook(book)
                }
            },
            onSummaryClick = {
                onNavigateToRoute(MainRouteReadRecordOverview)
            }
        )
    }

    entry<MainRouteReadRecordOverview> {
        ReadRecordOverviewRouteScreen(
            onBackClick = { onNavigateBack() },
            onBookClick = { name, author ->
                lifecycleScope.launch {
                    val book = withContext(IO) {
                        io.legado.app.data.appDb.bookDao.getBook(name, author)
                    }
                    if (book != null) this@mainEntryProvider.startActivityForBook(book)
                }
            }
        )
    }

    entry<MainRouteBookInfo>(
        metadata = metadata {
            put(NavDisplay.TransitionKey) {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            }
            put(NavDisplay.PopTransitionKey) {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            }
            if (configuration.appShell.predictiveBackEnabled) {
                put(NavDisplay.PredictivePopTransitionKey) { _ ->
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                }
            }
        }
    ) { route ->
        val bookInfoViewModel = koinViewModel<BookInfoViewModel>(key = "BookInfo:${route.bookUrl}")
        BookInfoRouteScreen(
            bookUrl = route.bookUrl,
            name = route.name,
            author = route.author,
            origin = route.origin,
            coverPath = route.coverPath,
            viewModel = bookInfoViewModel,
            onBack = { onNavigateBack() },
            onFinish = { _, _ -> onNavigateBack() },
            onOpenBookSourceEdit = { sourceUrl ->
                onNavigateToRoute(MainRouteBookSourceEdit(sourceUrl))
            },
            onOpenSourceLogin = { sourceUrl ->
                onNavigateToRoute(MainRouteSourceLogin(SourceLoginType.BookSource, sourceUrl))
            },
            onOpenReader = { bookUrl, inBookshelf, chapterChanged ->
                onNavigateToRoute(
                    MainRouteReadBook(
                        bookUrl = bookUrl,
                        inBookshelf = inBookshelf,
                        chapterChanged = chapterChanged,
                        sharedCoverKey = route.sharedCoverKey ?: bookCoverSharedElementKey(route.bookUrl),
                    )
                )
            },
            onOpenMangaReader = { bookUrl, inBookshelf, chapterChanged ->
                onNavigateToRoute(
                    MainRouteReadManga(
                        bookUrl = bookUrl,
                        inBookshelf = inBookshelf,
                        chapterChanged = chapterChanged,
                        openRequestId = System.nanoTime(),
                    )
                )
            },
            onNavigateToBookInfo = { name, author, bookUrl, origin, coverPath ->
                onNavigateToRoute(MainRouteBookInfo(name, author, bookUrl, origin, coverPath))
            },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            sharedCoverKey = route.sharedCoverKey ?: bookCoverSharedElementKey(route.bookUrl),
        )
    }

    entry<MainRouteHighlightTagRule> {
        HighlightTagRuleRouteScreen(
            onBackClick = { onNavigateBack() }
        )
    }

    entry<MainRouteAbout> {
        val viewModel = koinViewModel<AboutViewModel>()
        val context = LocalContext.current
        LaunchedEffect(viewModel) {
            viewModel.effects.collectLatest { effect ->
                when (effect) {
                    is AboutEffect.OpenUrl -> context.openUrl(effect.url)
                    is AboutEffect.ShowToast -> context.toastOnUi(effect.message)
                    is AboutEffect.StartDownload -> Download.start(
                        context,
                        effect.url,
                        effect.fileName
                    )
                }
            }
        }
        AboutScreen(
            state = viewModel.uiState.collectAsStateWithLifecycle().value,
            onIntent = viewModel::onIntent,
            onBack = { onNavigateBack() },
        )
    }
}
