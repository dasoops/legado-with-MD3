package io.legado.app.feature.localdirectory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.gateway.LocalDirectoryGateway
import io.legado.app.utils.AlphanumComparator
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalDirectoryViewModel(
    application: Application,
    private val gateway: LocalDirectoryGateway,
    private val bookRepository: BookRepository,
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

    private val appContext = application.applicationContext
    private val _state = MutableStateFlow(InternalState())
    private val _effects = MutableSharedFlow<LocalDirectoryEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var initialized = false

    val uiState = _state
        .map { state ->
            val searching = state.isSearch && state.searchKey.isNotBlank()
            val nodes = if (searching) {
                state.books
                    .asSequence()
                    .filter { it.matches(state.searchKey) }
                    .sortedWith(compareBy(AlphanumComparator) { it.book.name })
                    .map { LocalDirectoryNode.Book(it) }
                    .toList()
            } else {
                buildNodes(state.books, state.path)
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
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LocalDirectoryUiState(),
        )

    init {
        viewModelScope.launch {
            bookRepository.flowBookShelfByGroup(groupId)
                .map { list ->
                    list.map { book ->
                        DirectoryBook(
                            ui = book.toUiItem(),
                            dir = gateway.relativeDirectory(rootUri, book.bookUrl).toImmutableList(),
                        )
                    }
                }
                .collect { books ->
                    _state.update { it.copy(books = books) }
                }
        }
    }

    fun onIntent(intent: LocalDirectoryIntent) {
        when (intent) {
            LocalDirectoryIntent.Initialize -> initialize()
            LocalDirectoryIntent.Refresh -> rescan()
            is LocalDirectoryIntent.EnterFolder ->
                _state.update { it.copy(path = it.path + intent.name) }
            is LocalDirectoryIntent.NavigateToLevel -> _state.update { state ->
                state.copy(
                    path = if (intent.index <= 0) emptyList() else state.path.take(intent.index)
                )
            }
            LocalDirectoryIntent.NavigateBack -> _state.update { state ->
                state.copy(path = state.path.dropLast(1))
            }
            is LocalDirectoryIntent.SearchChange -> _state.update {
                it.copy(searchKey = intent.key, isSearch = intent.isSearch)
            }
        }
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch(Dispatchers.IO) {
            val name = gateway.directoryName(rootUri)
            _state.update {
                it.copy(rootName = name.orEmpty(), isUnavailable = name.isNullOrBlank())
            }
            rescan()
        }
    }

    private fun rescan() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            runCatching { gateway.importDirectoryToGroup(groupId, rootUri) }
                .onFailure {
                    AppLog.put("扫描本地目录失败\n${it.localizedMessage}", it)
                    val message = it.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: appContext.getString(R.string.error)
                    _effects.tryEmit(LocalDirectoryEffect.ShowToast(message))
                }
            withContext(Dispatchers.Main) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun buildNodes(
        books: List<DirectoryBook>,
        path: List<String>,
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
            folders.sortedWith(AlphanumComparator).forEach { add(LocalDirectoryNode.Folder(it)) }
            currentBooks
                .sortedWith(compareBy(AlphanumComparator) { it.book.name })
                .forEach { add(LocalDirectoryNode.Book(it)) }
        }
    }

    private fun DirectoryBook.matches(key: String): Boolean =
        book.name.contains(key, true) ||
                book.author.contains(key, true) ||
                book.originName.contains(key, true)
}
