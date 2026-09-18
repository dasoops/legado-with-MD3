package io.legado.app.ui.book.manage

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.SearchRepository
import io.legado.app.domain.gateway.BookExportSettingsGateway
import io.legado.app.domain.model.settings.BookExportSettings
import io.legado.app.domain.usecase.BatchChangeSourceCandidate
import io.legado.app.domain.usecase.BatchChangeSourcePreviewItem
import io.legado.app.domain.usecase.BatchChangeSourcePreviewStatus
import io.legado.app.domain.usecase.ChangeBookSourceUseCase
import io.legado.app.domain.usecase.ChangeSourceMigrationOptions
import io.legado.app.domain.usecase.DeleteBooksUseCase
import io.legado.app.domain.usecase.UpdateBooksGroupUseCase
import io.legado.app.help.book.removeType
import io.legado.app.help.config.LocalConfig
import io.legado.app.service.ExportBookService
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import org.koin.core.context.GlobalContext
import io.legado.app.ui.config.bookshelfConfig.BookshelfManageScreenConfig
import io.legado.app.ui.main.bookshelf.toLightBook
import io.legado.app.utils.cnCompare
import io.legado.app.utils.move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlin.math.max

private val bookshelfSettingsGateway get() = GlobalContext.get().get<BookshelfSettingsGateway>()

data class BookshelfManageScreenExportConfig(
    val exportUseReplace: Boolean = true,
    val enableCustomExport: Boolean = false,
    val exportNoChapterName: Boolean = false,
    val exportToWebDav: Boolean = false,
    val exportPictureFile: Boolean = false,
    val parallelExportBook: Boolean = false,
    val exportType: Int = 0,
    val exportCharset: String = "UTF-8",
    val bookExportFileName: String? = null,
    val episodeExportFileName: String = ""
) {
    val isCustomEpubExportEnabled: Boolean
        get() = enableCustomExport && exportType == 1
}

data class BookshelfManageScreenUiState(
    val groupId: Long = -1,
    val groupName: String? = null,
    val groupList: List<BookGroup> = emptyList(),
    val books: List<Book> = emptyList(),
    val bookSort: Int = bookshelfSettingsGateway.currentSettings.bookshelfSort,
    val bookSortOrder: Int = bookshelfSettingsGateway.currentSettings.bookshelfSortOrder,
    val isChangingSource: Boolean = false,
    val changeSourceProgress: String? = null,
    val changeSourceMessage: String? = null,
    val changeSourceError: String? = null,
    val batchChangePreviewItems: List<BatchChangeSourcePreviewItem> = emptyList(),
    val batchChangeOptions: ChangeSourceMigrationOptions = ChangeSourceMigrationOptions(),
    val cacheVersion: Long = 0,
    val deleteBookOriginal: Boolean = LocalConfig.deleteBookOriginal,
    val exportConfig: BookshelfManageScreenExportConfig = BookshelfManageScreenExportConfig(),
    val bookSources: ImmutableList<BookSourcePart> = persistentListOf(),
)

sealed interface BookshelfManageScreenIntent {
    data class Initialize(val groupId: Long) : BookshelfManageScreenIntent
    data class ChangeGroup(val groupId: Long) : BookshelfManageScreenIntent
    data class MoveBooksToGroup(val bookUrls: Set<String>, val groupId: Long) : BookshelfManageScreenIntent
    data class DeleteBooks(val bookUrls: Set<String>, val deleteOriginal: Boolean) : BookshelfManageScreenIntent
    data class MoveBookOrder(val fromIndex: Int, val toIndex: Int) : BookshelfManageScreenIntent
    data class ChangeBookSource(
        val oldBookUrl: String,
        val source: BookSource,
        val book: Book,
        val chapters: List<BookChapter>,
        val options: ChangeSourceMigrationOptions,
    ) : BookshelfManageScreenIntent
    data class BatchChangeBookSource(
        val bookUrls: Set<String>,
        val sources: List<BookSource>,
        val options: ChangeSourceMigrationOptions,
    ) : BookshelfManageScreenIntent
    data class MigratePreviewItem(val oldBookUrl: String) : BookshelfManageScreenIntent
    data class SkipPreviewItem(val oldBookUrl: String) : BookshelfManageScreenIntent
    data class SelectPreviewCandidate(val oldBookUrl: String, val candidateIndex: Int) : BookshelfManageScreenIntent
    data class UpdatePreviewItem(
        val oldBookUrl: String,
        val source: BookSource,
        val book: Book,
        val chapterCount: Int,
    ) : BookshelfManageScreenIntent
    data class AddPreviewItemToShelf(val oldBookUrl: String) : BookshelfManageScreenIntent
    data class OpenBookInfoPreview(val book: Book, val inBookshelf: Boolean) : BookshelfManageScreenIntent
    data object MigrateAllPreviewItems : BookshelfManageScreenIntent
    data object AddAllPreviewItemsToShelf : BookshelfManageScreenIntent
    data object DismissChangeSourceStatus : BookshelfManageScreenIntent
    data object DismissBatchChangePreview : BookshelfManageScreenIntent
    data class SetExportUseReplace(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetEnableCustomExport(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetExportNoChapterName(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetExportToWebDav(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetExportPictureFile(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetParallelExportBook(val enabled: Boolean) : BookshelfManageScreenIntent
    data class SetExportType(val type: Int) : BookshelfManageScreenIntent
    data class SetExportCharset(val charset: String) : BookshelfManageScreenIntent
    data class SetBookExportFileName(val fileName: String?) : BookshelfManageScreenIntent
    data class SetEpisodeExportFileName(val fileName: String) : BookshelfManageScreenIntent
}

sealed interface BookshelfManageScreenEffect {
    data class NotifyBookChanged(val bookUrl: String) : BookshelfManageScreenEffect
    data class ShowMessage(val message: String) : BookshelfManageScreenEffect
    data class OpenBookInfo(val bookUrl: String, val name: String, val author: String) : BookshelfManageScreenEffect
}

class BookshelfManageScreenViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val bookSourceRepository: BookSourceRepository,
    private val bookGroupRepository: BookGroupRepository,
    private val searchRepository: SearchRepository,
    val bookshelfManageScreenConfig: BookshelfManageScreenConfig,
    private val bookExportSettingsGateway: BookExportSettingsGateway,
    private val changeBookSourceUseCase: ChangeBookSourceUseCase,
    private val deleteBooksUseCase: DeleteBooksUseCase,
    private val updateBooksGroupUseCase: UpdateBooksGroupUseCase,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(BookshelfManageScreenUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BookshelfManageScreenEffect>(extraBufferCapacity = 32)
    val effects = _effects.asSharedFlow()

    private var booksJob: Job? = null
    private var groupsJob: Job? = null

    init {
        viewModelScope.launch {
            bookExportSettingsGateway.settings.collect(::syncExportConfig)
        }
        viewModelScope.launch {
            bookSourceRepository.flowEnabled().collect { sources ->
                _uiState.update { it.copy(bookSources = sources.toImmutableList()) }
            }
        }
    }

    fun dispatch(intent: BookshelfManageScreenIntent) {
        when (intent) {
            is BookshelfManageScreenIntent.Initialize -> initialize(intent.groupId)
            is BookshelfManageScreenIntent.ChangeGroup -> changeGroup(intent.groupId)
            is BookshelfManageScreenIntent.MoveBooksToGroup -> moveBooksToGroup(intent.bookUrls, intent.groupId)
            is BookshelfManageScreenIntent.DeleteBooks -> deleteBooks(intent.bookUrls, intent.deleteOriginal)
            is BookshelfManageScreenIntent.MoveBookOrder -> moveBookOrder(intent.fromIndex, intent.toIndex)
            is BookshelfManageScreenIntent.ChangeBookSource -> changeBookSource(
                intent.oldBookUrl,
                intent.source,
                intent.book,
                intent.chapters,
                intent.options
            )

            is BookshelfManageScreenIntent.BatchChangeBookSource -> batchChangeBookSource(
                intent.bookUrls,
                intent.sources,
                intent.options
            )

            is BookshelfManageScreenIntent.MigratePreviewItem -> migratePreviewItem(intent.oldBookUrl)
            is BookshelfManageScreenIntent.SkipPreviewItem -> skipPreviewItem(intent.oldBookUrl)
            is BookshelfManageScreenIntent.SelectPreviewCandidate -> selectPreviewCandidate(
                intent.oldBookUrl,
                intent.candidateIndex
            )

            is BookshelfManageScreenIntent.UpdatePreviewItem -> updatePreviewItem(
                intent.oldBookUrl,
                intent.source,
                intent.book,
                intent.chapterCount
            )

            is BookshelfManageScreenIntent.AddPreviewItemToShelf -> addPreviewItemToShelf(intent.oldBookUrl)
            is BookshelfManageScreenIntent.OpenBookInfoPreview -> openBookInfoPreview(
                intent.book,
                intent.inBookshelf
            )

            BookshelfManageScreenIntent.MigrateAllPreviewItems -> migrateAllPreviewItems()

            BookshelfManageScreenIntent.AddAllPreviewItemsToShelf -> addAllPreviewItemsToShelf()

            BookshelfManageScreenIntent.DismissChangeSourceStatus -> {
                _uiState.update {
                    it.copy(
                        changeSourceProgress = null,
                        changeSourceMessage = null,
                        changeSourceError = null,
                    )
                }
            }

            BookshelfManageScreenIntent.DismissBatchChangePreview -> {
                _uiState.update { it.copy(batchChangePreviewItems = emptyList()) }
            }

            is BookshelfManageScreenIntent.SetExportUseReplace -> {
                updateExportSetting { it.copy(exportUseReplace = intent.enabled) }
                val msg = if (intent.enabled) "替换净化功能已开启" else "替换净化功能已关闭"
                _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage(msg))
            }

            is BookshelfManageScreenIntent.SetEnableCustomExport -> {
                updateExportSetting { it.copy(enableCustomExport = intent.enabled) }
            }

            is BookshelfManageScreenIntent.SetExportNoChapterName -> {
                updateExportSetting { it.copy(exportNoChapterName = intent.enabled) }
            }

            is BookshelfManageScreenIntent.SetExportToWebDav -> {
                updateExportSetting { it.copy(exportToWebDav = intent.enabled) }
            }

            is BookshelfManageScreenIntent.SetExportPictureFile -> {
                updateExportSetting { it.copy(exportPictureFile = intent.enabled) }
            }

            is BookshelfManageScreenIntent.SetParallelExportBook -> {
                updateExportSetting { it.copy(parallelExportBook = intent.enabled) }
            }

            is BookshelfManageScreenIntent.SetExportType -> {
                updateExportSetting { it.copy(exportType = intent.type) }
            }

            is BookshelfManageScreenIntent.SetExportCharset -> {
                updateExportSetting { it.copy(exportCharset = intent.charset) }
            }

            is BookshelfManageScreenIntent.SetBookExportFileName -> {
                updateExportSetting { it.copy(bookExportFileName = intent.fileName) }
            }

            is BookshelfManageScreenIntent.SetEpisodeExportFileName -> {
                updateExportSetting { it.copy(episodeExportFileName = intent.fileName) }
            }
        }
    }

    private fun initialize(groupId: Long) {
        _uiState.update { it.copy(groupId = groupId) }
        syncExportConfig(bookExportSettingsGateway.currentSettings)
        observeGroups()
        observeBooks(groupId)
        observeExportChanges()
        refreshGroupName(groupId)
    }

    private fun changeGroup(groupId: Long) {
        _uiState.update { it.copy(groupId = groupId) }
        observeBooks(groupId)
        refreshGroupName(groupId)
    }

    private fun observeGroups() {
        groupsJob?.cancel()
        groupsJob = viewModelScope.launch {
            bookGroupRepository.flowAll().collect { groups ->
                _uiState.update { it.copy(groupList = groups) }
            }
        }
    }

    private fun observeBooks(groupId: Long) {
        booksJob?.cancel()
        booksJob = viewModelScope.launch {
            bookRepository.flowBookShelfByGroup(groupId).map { books ->
                val booksDownload = books.map { it.toLightBook() }
                val bookSort = bookshelfManageScreenConfig.getBookSortByGroupId(groupId)
                val isDescending = bookshelfManageScreenConfig.bookshelfSortOrder == 1
                bookSort to when (bookSort) {
                    1 -> if (isDescending) booksDownload.sortedByDescending { it.latestChapterTime }
                    else booksDownload.sortedBy { it.latestChapterTime }

                    2 -> if (isDescending) {
                        booksDownload.sortedWith { o1, o2 -> o2.name.cnCompare(o1.name) }
                    } else {
                        booksDownload.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }
                    }

                    3 -> if (isDescending) booksDownload.sortedByDescending { it.order }
                    else booksDownload.sortedBy { it.order }

                    4 -> if (isDescending) booksDownload.sortedByDescending {
                        max(it.latestChapterTime, it.durChapterTime)
                    } else booksDownload.sortedBy {
                        max(it.latestChapterTime, it.durChapterTime)
                    }

                    else -> if (isDescending) booksDownload.sortedByDescending { it.durChapterTime }
                    else booksDownload.sortedBy { it.durChapterTime }
                }
            }.collect { (bookSort, books) ->
                _uiState.update {
                    it.copy(
                        books = books,
                        bookSort = bookSort,
                        bookSortOrder = bookshelfManageScreenConfig.bookshelfSortOrder
                    )
                }
            }
        }
    }

    private fun observeExportChanges() {
        viewModelScope.launch {
            ExportBookService.exportBookUpdateFlow.collect { bookUrl ->
                emitBookChanged(bookUrl)
            }
        }
    }

    private fun syncExportConfig(settings: BookExportSettings) {
        _uiState.update {
            it.copy(
                exportConfig = BookshelfManageScreenExportConfig(
                    exportUseReplace = settings.exportUseReplace,
                    enableCustomExport = settings.enableCustomExport,
                    exportNoChapterName = settings.exportNoChapterName,
                    exportToWebDav = settings.exportToWebDav,
                    exportPictureFile = settings.exportPictureFile,
                    parallelExportBook = settings.parallelExportBook,
                    exportType = settings.exportType,
                    exportCharset = settings.exportCharset,
                    bookExportFileName = settings.bookExportFileName,
                    episodeExportFileName = settings.episodeExportFileName,
                )
            )
        }
    }

    private fun updateExportSetting(transform: (BookExportSettings) -> BookExportSettings) {
        viewModelScope.launch {
            bookExportSettingsGateway.update(transform)
        }
    }

    private fun refreshGroupName(groupId: Long) {
        execute {
            val title = bookGroupRepository.getByID(groupId)?.groupName
            title ?: context.getString(io.legado.app.R.string.no_group)
        }.onSuccess { groupName ->
            _uiState.update { it.copy(groupName = groupName) }
        }
    }

    private fun moveBooksToGroup(bookUrls: Set<String>, groupId: Long) {
        if (bookUrls.isEmpty()) return
        val safeGroupId = groupId.coerceAtLeast(0L)
        execute {
            updateBooksGroupUseCase.replaceGroup(bookUrls, safeGroupId)
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("移动分组失败\n${it.localizedMessage}"))
        }
    }

    private fun deleteBooks(bookUrls: Set<String>, deleteOriginal: Boolean) {
        if (bookUrls.isEmpty()) return
        execute {
            LocalConfig.deleteBookOriginal = deleteOriginal
            deleteBooksUseCase.execute(bookUrls, deleteOriginal)
        }.onSuccess { deletedBookUrls ->
            _uiState.update { it.copy(deleteBookOriginal = deleteOriginal) }
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("删除成功"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("删除失败\n${it.localizedMessage}"))
        }
    }

    private fun moveBookOrder(fromIndex: Int, toIndex: Int) {
        val books = uiState.value.books
        if (fromIndex !in books.indices || toIndex !in books.indices || fromIndex == toIndex) {
            return
        }
        val reorderedBooks = books.toMutableList().apply { move(fromIndex, toIndex) }
        val isDescending = uiState.value.bookSortOrder == 1
        val maxOrder = reorderedBooks.size
        reorderedBooks.forEachIndexed { index, book ->
            book.order = if (isDescending) maxOrder - index else index + 1
        }
        _uiState.update {
            it.copy(
                books = reorderedBooks,
                cacheVersion = it.cacheVersion + 1
            )
        }
        execute {
            bookRepository.update(*reorderedBooks.toTypedArray())
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("排序保存失败\n${it.localizedMessage}"))
        }
    }

    private fun changeBookSource(
        oldBookUrl: String,
        source: BookSource,
        book: Book,
        chapters: List<BookChapter>,
        options: ChangeSourceMigrationOptions,
    ) {
        execute {
            val oldBook = bookRepository.getBook(oldBookUrl) ?: return@execute null
            changeBookSourceUseCase.changeTo(oldBook, book, chapters, options)
        }.onSuccess { result ->
            result ?: return@onSuccess
            emitBookChanged(result.book.bookUrl)
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("换源完成"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("换源失败\n${it.localizedMessage}"))
        }
    }

    private fun batchChangeBookSource(
        bookUrls: Set<String>,
        sources: List<BookSource>,
        options: ChangeSourceMigrationOptions,
    ) {
        if (bookUrls.isEmpty()) {
            _uiState.update { it.copy(changeSourceError = "未选择书籍") }
            return
        }
        if (sources.isEmpty()) {
            _uiState.update { it.copy(changeSourceError = "未选择书源") }
            return
        }
        execute {
            val concurrency = downloadCacheSettingsGateway.currentSettings.threadCount.coerceAtLeast(1)
            _uiState.update {
                it.copy(
                    isChangingSource = true,
                    changeSourceProgress = "0 / ${bookUrls.size}",
                    changeSourceMessage = "开始查找：${bookUrls.size} 本，${sources.size} 个书源，并发 $concurrency",
                    changeSourceError = null,
                    batchChangeOptions = options,
                    batchChangePreviewItems = emptyList()
                )
            }
            val books = bookUrls.mapNotNull { bookRepository.getBook(it) }
            changeBookSourceUseCase.prepareBatchChange(
                books = books,
                sources = sources,
                concurrency = concurrency,
            ) { current, total, bookName ->
                _uiState.update {
                    it.copy(changeSourceProgress = "$current / $total  $bookName")
                }
            }
        }.onSuccess { previewItems ->
            _uiState.update {
                it.copy(
                    batchChangePreviewItems = previewItems,
                    isChangingSource = false,
                    changeSourceProgress = null
                )
            }
            val matchedCount = previewItems.count { it.canMigrate }
            val skippedCount = previewItems.count {
                it.status == BatchChangeSourcePreviewStatus.Skipped
            }
            val notFoundCount = previewItems.size - matchedCount - skippedCount
            _uiState.update {
                it.copy(
                    changeSourceMessage = "查找完成：可迁移 $matchedCount 本，未找到 $notFoundCount 本，跳过 $skippedCount 本",
                    changeSourceError = null
                )
            }
        }.onError {
            val progress = uiState.value.changeSourceProgress.orEmpty()
            _uiState.update { state ->
                state.copy(
                    changeSourceError = "批量换源查找失败${if (progress.isBlank()) "" else "\n进度：$progress"}\n${it.localizedMessage}"
                )
            }
        }.onFinally {
            _uiState.update {
                it.copy(
                    isChangingSource = false,
                    changeSourceProgress = null
                )
            }
        }
    }

    private fun migratePreviewItem(oldBookUrl: String) {
        val item = uiState.value.batchChangePreviewItems.firstOrNull {
            it.oldBook.bookUrl == oldBookUrl
        } ?: return
        val candidate = item.selectedCandidate ?: return
        execute {
            val oldBook = bookRepository.getBook(oldBookUrl) ?: item.oldBook
            val chapters = changeBookSourceUseCase.loadCandidateChapters(
                candidate.source,
                candidate.book
            ) ?: error("获取目录失败")
            changeBookSourceUseCase.changeTo(
                oldBook = oldBook,
                newBook = candidate.book,
                chapters = chapters,
                options = uiState.value.batchChangeOptions,
            )
        }.onSuccess { result ->
            removePreviewItem(oldBookUrl)
            emitBookChanged(result.book.bookUrl)
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("迁移完成"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("迁移失败\n${it.localizedMessage}"))
        }
    }

    private fun skipPreviewItem(oldBookUrl: String) {
        _uiState.update { state ->
            state.copy(
                batchChangePreviewItems = state.batchChangePreviewItems.map { item ->
                    if (item.oldBook.bookUrl == oldBookUrl) {
                        item.copy(status = BatchChangeSourcePreviewStatus.Skipped)
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun selectPreviewCandidate(oldBookUrl: String, candidateIndex: Int) {
        _uiState.update { state ->
            state.copy(
                batchChangePreviewItems = state.batchChangePreviewItems.map { item ->
                    if (item.oldBook.bookUrl == oldBookUrl) {
                        item.copy(
                            selectedCandidateIndex = candidateIndex.coerceIn(
                                0,
                                (item.candidates.size - 1).coerceAtLeast(0)
                            ),
                            status = BatchChangeSourcePreviewStatus.Matched
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun updatePreviewItem(
        oldBookUrl: String,
        source: BookSource,
        book: Book,
        chapterCount: Int,
    ) {
        _uiState.update { state ->
            state.copy(
                batchChangePreviewItems = state.batchChangePreviewItems.map { item ->
                    if (item.oldBook.bookUrl == oldBookUrl) {
                        book.totalChapterNum = chapterCount
                        item.copy(
                            candidates = listOf(
                                BatchChangeSourceCandidate(
                                    source = source,
                                    book = book,
                                    chapterCount = chapterCount
                                )
                            ) +
                                    item.candidates,
                            selectedCandidateIndex = 0,
                            status = BatchChangeSourcePreviewStatus.Matched
                        )
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun addPreviewItemToShelf(oldBookUrl: String) {
        val item = uiState.value.batchChangePreviewItems.firstOrNull {
            it.oldBook.bookUrl == oldBookUrl
        } ?: return
        val candidate = item.selectedCandidate ?: return
        execute {
            val chapters = changeBookSourceUseCase.loadCandidateChapters(
                candidate.source,
                candidate.book
            ) ?: error("获取目录失败")
            candidate.book.removeType(BookType.notShelf)
            if (candidate.book.order == 0) {
                candidate.book.order = bookRepository.getMinOrder() - 1
            }
            bookRepository.insert(candidate.book)
            bookRepository.insertChapters(*chapters.toTypedArray())
            candidate.book
        }.onSuccess {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("已添加到书架"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("添加书籍失败\n${it.localizedMessage}"))
        }
    }

    private fun openBookInfoPreview(book: Book, inBookshelf: Boolean) {
        execute {
            if (!inBookshelf) {
                searchRepository.saveSearchBooks(listOf(book.toSearchBook()))
            }
            book
        }.onSuccess {
            _effects.tryEmit(BookshelfManageScreenEffect.OpenBookInfo(it.bookUrl, it.name, it.author))
        }
    }

    private fun removePreviewItem(oldBookUrl: String) {
        _uiState.update {
            it.copy(
                batchChangePreviewItems = it.batchChangePreviewItems.filterNot { item ->
                    item.oldBook.bookUrl == oldBookUrl
                },
                cacheVersion = it.cacheVersion + 1
            )
        }
    }

    private fun migrateAllPreviewItems() {
        val items = uiState.value.batchChangePreviewItems.filter { it.canMigrate }
        if (items.isEmpty()) return
        execute {
            _uiState.update {
                it.copy(isChangingSource = true, changeSourceProgress = "0 / ${items.size}")
            }
            items.forEachIndexed { index, item ->
                _uiState.update {
                    it.copy(changeSourceProgress = "${index + 1} / ${items.size}  ${item.oldBook.name}")
                }
                val candidate = item.selectedCandidate ?: return@forEachIndexed
                val oldBook = bookRepository.getBook(item.oldBook.bookUrl) ?: item.oldBook
                val chapters = changeBookSourceUseCase.loadCandidateChapters(
                    candidate.source,
                    candidate.book
                ) ?: return@forEachIndexed
                changeBookSourceUseCase.changeTo(
                    oldBook = oldBook,
                    newBook = candidate.book,
                    chapters = chapters,
                    options = uiState.value.batchChangeOptions,
                )
            }
        }.onSuccess {
            _uiState.update { it.copy(batchChangePreviewItems = emptyList()) }
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("批量迁移完成"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("批量迁移失败\n${it.localizedMessage}"))
        }.onFinally {
            _uiState.update {
                it.copy(
                    isChangingSource = false,
                    changeSourceProgress = null,
                    cacheVersion = it.cacheVersion + 1
                )
            }
        }
    }

    private fun addAllPreviewItemsToShelf() {
        val items = uiState.value.batchChangePreviewItems.filter { it.canMigrate }
        if (items.isEmpty()) return
        execute {
            _uiState.update {
                it.copy(isChangingSource = true, changeSourceProgress = "0 / ${items.size}")
            }
            items.forEachIndexed { index, item ->
                _uiState.update {
                    it.copy(changeSourceProgress = "${index + 1} / ${items.size}  ${item.oldBook.name}")
                }
                val candidate = item.selectedCandidate ?: return@forEachIndexed
                val chapters = changeBookSourceUseCase.loadCandidateChapters(
                    candidate.source,
                    candidate.book
                ) ?: return@forEachIndexed
                candidate.book.removeType(BookType.notShelf)
                if (candidate.book.order == 0) {
                    candidate.book.order = bookRepository.getMinOrder() - 1
                }
                bookRepository.insert(candidate.book)
                bookRepository.insertChapters(*chapters.toTypedArray())
            }
        }.onSuccess {
            _uiState.update { it.copy(batchChangePreviewItems = emptyList()) }
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("批量添加完成"))
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("批量添加失败\n${it.localizedMessage}"))
        }.onFinally {
            _uiState.update {
                it.copy(
                    isChangingSource = false,
                    changeSourceProgress = null,
                    cacheVersion = it.cacheVersion + 1
                )
            }
        }
    }

    private fun emitBookChanged(bookUrl: String) {
        _uiState.update { it.copy(cacheVersion = it.cacheVersion + 1) }
        _effects.tryEmit(BookshelfManageScreenEffect.NotifyBookChanged(bookUrl))
    }

}
