package io.legado.app.feature.localdirectory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookshelfRepository
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.gateway.LocalDirectoryGateway
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.utils.AlphanumComparator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalDirectoryViewModel(
    application: Application,
    private val gateway: LocalDirectoryGateway,
    private val bookRepository: BookRepository,
    private val bookshelfRepository: BookshelfRepository,
    bookshelfSettingsGateway: BookshelfSettingsGateway,
    private val groupId: Long,
    private val rootUri: String,
) : ViewModel() {

    private data class InternalState(
        val path: List<String> = emptyList(),
        val rootName: String = "",
        val books: List<DirectoryBook> = emptyList(),
        val searchKey: String = "",
        val isSearch: Boolean = false,
        val isLoading: Boolean = false,
        val isUnavailable: Boolean = false,
    )

    private data class SortConfig(val sort: Int, val sortOrder: Int)

    private val appContext = application.applicationContext
    private val state = MutableStateFlow(InternalState())
    private val _effects = MutableSharedFlow<LocalDirectoryEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    // 本地目录页复用书架的排序设置, 否则与「书架-布局设置」的排序口径不一致.
    private val sortConfigFlow = bookshelfSettingsGateway.settings
        .map { SortConfig(it.bookshelfSort, it.bookshelfSortOrder) }
        .distinctUntilChanged()

    private var initialized = false
    private var scanJob: Job? = null

    // 仅在 toDirectoryBooks 内读写 (单收集器串行执行), 用于复用未变化的书籍派生数据.
    private var directoryBookCache: Map<String, DirectoryBook> = emptyMap()

    val uiState = combine(state, sortConfigFlow) { state, sortConfig ->
        val bookComparator = bookshelfRepository.bookComparator(sortConfig.sort, sortConfig.sortOrder)
        val searching = state.isSearch && state.searchKey.isNotBlank()
        val nodes = if (searching) {
            state.books
                .asSequence()
                .filter { it.matches(state.searchKey) }
                .sortedWith(compareBy(bookComparator) { it.book })
                .map { LocalDirectoryNode.Book(it) }
                .toList()
        } else {
            buildNodes(state.books, state.path, bookComparator)
        }
        LocalDirectoryUiState(
            path = state.path.toImmutableList(),
            pathNames = (listOf(state.rootName) + state.path)
                .filter { it.isNotBlank() }
                .toImmutableList(),
            nodes = nodes.toImmutableList(),
            isLoading = state.isLoading,
            isUnavailable = state.isUnavailable,
        )
    }
        // 节点重建是 O(书数) 的遍历 + 排序, 书架大时放主线程会造成上百毫秒卡顿.
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LocalDirectoryUiState(),
        )

    init {
        viewModelScope.launch {
            bookRepository.flowBookShelfByGroup(groupId)
                .map(::toDirectoryBooks)
                // 每本书都要做标签解析和路径解析, 书多时单次发射在主线程可达 200ms 以上.
                .flowOn(Dispatchers.Default)
                .collect { books ->
                    state.update { it.copy(books = books) }
                }
        }
    }

    /**
     * 只为内容变化的书重建 UI 项: 一次导入/刷新常只改动个别书, 全量重算会把大书架的
     * 派生成本线性叠加到每次发射上. 复用旧项同时保持对象同一性, 减少下游重组.
     */
    private fun toDirectoryBooks(books: List<BookShelfItem>): List<DirectoryBook> {
        val previous = directoryBookCache
        val next = HashMap<String, DirectoryBook>(books.size * 2)
        val result = ArrayList<DirectoryBook>(books.size)
        books.forEach { book ->
            val cached = previous[book.bookUrl]
            val item =
                if (cached != null && cached.ui.book == book) {
                    cached
                } else {
                    DirectoryBook(
                        ui = book.toUiItem(),
                        dir = gateway.relativeDirectory(rootUri, book.bookUrl).toImmutableList(),
                    )
                }
            next[book.bookUrl] = item
            result.add(item)
        }
        directoryBookCache = next
        return result
    }

    fun onIntent(intent: LocalDirectoryIntent) {
        when (intent) {
            LocalDirectoryIntent.Initialize -> initialize()
            LocalDirectoryIntent.Refresh -> rescan()
            is LocalDirectoryIntent.EnterFolder ->
                state.update { it.copy(path = it.path + intent.name) }
            is LocalDirectoryIntent.NavigateToLevel -> state.update { state ->
                state.copy(
                    path = if (intent.index <= 0) emptyList() else state.path.take(intent.index),
                )
            }
            LocalDirectoryIntent.NavigateBack -> state.update { state ->
                state.copy(path = state.path.dropLast(1))
            }
            is LocalDirectoryIntent.SearchChange -> state.update {
                it.copy(searchKey = intent.key, isSearch = intent.isSearch)
            }
        }
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch(Dispatchers.IO) {
            val name = gateway.directoryName(rootUri)
            state.update {
                it.copy(rootName = name.orEmpty(), isUnavailable = name.isNullOrBlank())
            }
            rescan()
        }
    }

    private fun rescan() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            state.update { it.copy(isLoading = true) }
            runCatching { gateway.importDirectoryToGroup(groupId, rootUri) }
                .onSuccess { result ->
                    AppLog.putDebug(
                        "本地目录扫描完成: group=$groupId imported=${result.imported} " +
                            "failed=${result.failed} durationMs=${System.currentTimeMillis() - startedAt}",
                    )
                    if (result.failed > 0) {
                        _effects.tryEmit(
                            LocalDirectoryEffect.ShowToast(
                                appContext.getString(R.string.local_directory_import_partial, result.failed),
                            ),
                        )
                    }
                }
                .onFailure {
                    AppLog.put("扫描本地目录失败\n${it.localizedMessage}", it)
                    val message = it.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: appContext.getString(R.string.error)
                    _effects.tryEmit(LocalDirectoryEffect.ShowToast(message))
                }
            withContext(Dispatchers.Main) {
                state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun buildNodes(
        books: List<DirectoryBook>,
        path: List<String>,
        bookComparator: Comparator<BookShelfItem>,
    ): List<LocalDirectoryNode> {
        val folders = linkedSetOf<String>()
        val currentBooks = arrayListOf<DirectoryBook>()
        books.forEach { item ->
            val dir = item.dir
            if (dir.size <= path.size || dir.take(path.size) != path) return@forEach
            if (dir.size == path.size + 1) {
                folders.add(dir.last())
            } else {
                folders.add(dir[path.size])
            }
        }
        books.forEach { item ->
            if (item.dir == path) currentBooks.add(item)
        }
        return buildList {
            // 文件夹作为导航层级始终按名称排序, 仅同一层级内的书籍跟随书架排序设置.
            folders.sortedWith(AlphanumComparator).forEach { add(LocalDirectoryNode.Folder(it)) }
            currentBooks
                .sortedWith(compareBy(bookComparator) { it.book })
                .forEach { add(LocalDirectoryNode.Book(it)) }
        }
    }

    private fun DirectoryBook.matches(key: String): Boolean = book.name.contains(key, true) ||
        book.author.contains(key, true) ||
        book.originName.contains(key, true)
}
