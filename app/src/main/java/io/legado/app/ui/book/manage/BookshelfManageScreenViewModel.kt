package io.legado.app.ui.book.manage

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.SearchRepository
import io.legado.app.domain.gateway.BookExportSettingsGateway
import io.legado.app.domain.model.settings.BookExportSettings
import io.legado.app.domain.usecase.DeleteBooksUseCase
import io.legado.app.help.config.LocalConfig
import io.legado.app.service.ExportBookService
import io.legado.app.domain.gateway.BookshelfSettingsGateway
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
    val cacheVersion: Long = 0,
    val deleteBookOriginal: Boolean = LocalConfig.deleteBookOriginal,
    val exportConfig: BookshelfManageScreenExportConfig = BookshelfManageScreenExportConfig(),
)

sealed interface BookshelfManageScreenIntent {
    data class Initialize(val groupId: Long) : BookshelfManageScreenIntent
    data class ChangeGroup(val groupId: Long) : BookshelfManageScreenIntent
    data class AddTags(val bookUrls: Set<String>, val tags: Set<String>) : BookshelfManageScreenIntent
    data class DeleteBooks(val bookUrls: Set<String>, val deleteOriginal: Boolean) : BookshelfManageScreenIntent
    data class MoveBookOrder(val fromIndex: Int, val toIndex: Int) : BookshelfManageScreenIntent
    data class OpenBookInfoPreview(val book: Book, val inBookshelf: Boolean) : BookshelfManageScreenIntent
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
    private val bookGroupRepository: BookGroupRepository,
    private val searchRepository: SearchRepository,
    val bookshelfManageScreenConfig: BookshelfManageScreenConfig,
    private val bookExportSettingsGateway: BookExportSettingsGateway,
    private val deleteBooksUseCase: DeleteBooksUseCase,
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
    }

    fun dispatch(intent: BookshelfManageScreenIntent) {
        when (intent) {
            is BookshelfManageScreenIntent.Initialize -> initialize(intent.groupId)
            is BookshelfManageScreenIntent.ChangeGroup -> changeGroup(intent.groupId)
            is BookshelfManageScreenIntent.AddTags -> addTags(intent.bookUrls, intent.tags)
            is BookshelfManageScreenIntent.DeleteBooks -> deleteBooks(intent.bookUrls, intent.deleteOriginal)
            is BookshelfManageScreenIntent.MoveBookOrder -> moveBookOrder(intent.fromIndex, intent.toIndex)
            is BookshelfManageScreenIntent.OpenBookInfoPreview -> openBookInfoPreview(
                intent.book,
                intent.inBookshelf
            )

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

    private fun addTags(bookUrls: Set<String>, tags: Set<String>) {
        if (bookUrls.isEmpty()) return
        execute {
            bookRepository.addTags(bookUrls, tags)
        }.onError {
            _effects.tryEmit(BookshelfManageScreenEffect.ShowMessage("添加标签失败\n${it.localizedMessage}"))
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

    private fun emitBookChanged(bookUrl: String) {
        _uiState.update { it.copy(cacheVersion = it.cacheVersion + 1) }
        _effects.tryEmit(BookshelfManageScreenEffect.NotifyBookChanged(bookUrl))
    }

}
