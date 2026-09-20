package io.legado.app.feature.localdirectory

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.data.repository.BookImportRepository
import io.legado.app.domain.gateway.LocalDirectoryGateway
import io.legado.app.domain.model.LocalDirectoryEntry
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.AlphanumComparator
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocalDirectoryViewModel(
    application: Application,
    private val gateway: LocalDirectoryGateway,
    private val importRepository: BookImportRepository,
    private val rootUri: String,
) : ViewModel() {

    private data class InternalState(
        val pathNames: List<String> = emptyList(),
        val dirUris: List<String> = emptyList(),
        val entries: List<LocalDirectoryEntry> = emptyList(),
        val searchKey: String = "",
        val isSearch: Boolean = false,
        val isLoading: Boolean = false,
        val sort: Int = 0,
        val isUnavailable: Boolean = false,
    )

    private val appContext = application.applicationContext
    private val _state = MutableStateFlow(InternalState())
    private val _effects = MutableSharedFlow<LocalDirectoryEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    private var initialized = false
    private var watchJob: Job? = null
    private var pollJob: Job? = null
    private var observer: ContentObserver? = null

    val uiState = combine(_state, gateway.flowLocalBookProgress()) { state, progress ->
        val items = state.entries
            .asSequence()
            .filter {
                state.searchKey.isBlank() || it.name.contains(state.searchKey, ignoreCase = true)
            }
            .sortedWith(entryComparator(state.sort))
            .map { entry ->
                LocalDirectoryItem(
                    entry = entry,
                    progress = if (entry.isDir) null else progress[entry.name],
                )
            }
            .toList()
        LocalDirectoryUiState(
            rootUri = rootUri,
            pathNames = state.pathNames.toImmutableList(),
            items = items.toImmutableList(),
            searchKey = state.searchKey,
            isSearch = state.isSearch,
            isLoading = state.isLoading,
            sort = state.sort,
            isUnavailable = state.isUnavailable,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LocalDirectoryUiState(rootUri = rootUri),
        )

    fun onIntent(intent: LocalDirectoryIntent) {
        when (intent) {
            LocalDirectoryIntent.Initialize -> initialize()
            LocalDirectoryIntent.Refresh -> refresh()
            LocalDirectoryIntent.NavigateBack -> navigateBack()
            is LocalDirectoryIntent.NavigateToLevel -> navigateToLevel(intent.index)
            is LocalDirectoryIntent.EnterDir -> enterDir(intent.item)
            is LocalDirectoryIntent.ItemClick -> onItemClick(intent.item)
            is LocalDirectoryIntent.SearchToggle -> setSearchMode(intent.enabled)
            is LocalDirectoryIntent.SearchQueryChange -> _state.update { it.copy(searchKey = intent.query) }
            is LocalDirectoryIntent.SortChange -> _state.update { it.copy(sort = intent.sort) }
        }
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        viewModelScope.launch(Dispatchers.IO) {
            val root = gateway.rootDocument(rootUri)
            if (root == null || !root.isDir) {
                _state.update { it.copy(isLoading = false, isUnavailable = true) }
                return@launch
            }
            _state.update {
                it.copy(pathNames = listOf(root.name), dirUris = listOf(rootUri))
            }
            loadCurrent()
            startWatchingCurrent()
            startPolling()
        }
    }

    private fun refresh() {
        if (_state.value.dirUris.isEmpty()) {
            // 目录曾不可用, 重试解析根, 给授权恢复留一次机会
            initialized = false
            initialize()
            return
        }
        viewModelScope.launch(Dispatchers.IO) { loadCurrent() }
    }

    private suspend fun loadCurrent() {
        val currentUri = _state.value.dirUris.lastOrNull() ?: return
        val listed = gateway.listChildren(currentUri)
        _state.update { state ->
            if (state.dirUris.lastOrNull() != currentUri) return@update state
            // 内容未变时跳过, 避免兜底轮询反复重发列表
            if (state.entries == listed && !state.isLoading) return@update state
            state.copy(entries = listed, isLoading = false)
        }
    }

    private fun navigateBack() {
        val state = _state.value
        if (state.dirUris.size <= 1) return
        goTo(state.dirUris.dropLast(1), state.pathNames.dropLast(1))
    }

    private fun navigateToLevel(index: Int) {
        val state = _state.value
        if (index < 0 || index >= state.pathNames.size) return
        goTo(state.dirUris.take(index + 1), state.pathNames.take(index + 1))
    }

    private fun enterDir(item: LocalDirectoryItem) {
        if (!item.entry.isDir) return
        val state = _state.value
        goTo(
            state.dirUris + item.entry.uri,
            state.pathNames + item.entry.name,
        )
    }

    private fun goTo(dirUris: List<String>, pathNames: List<String>) {
        _state.update {
            it.copy(
                dirUris = dirUris,
                pathNames = pathNames,
                entries = emptyList(),
                isLoading = true,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            loadCurrent()
            startWatchingCurrent()
        }
    }

    private fun onItemClick(item: LocalDirectoryItem) {
        if (item.entry.isDir) {
            enterDir(item)
        } else {
            openFile(item)
        }
    }

    private fun openFile(item: LocalDirectoryItem) {
        val entry = item.entry
        viewModelScope.launch(Dispatchers.IO) {
            val book = runCatching {
                importRepository.findAndRebind(entry.name, entry.uri)
                    ?: LocalBook.importFiles(entry.uri.toUri()).firstOrNull()
            }.getOrNull()
            if (book != null) {
                _effects.emit(LocalDirectoryEffect.OpenBook(book))
            } else {
                _effects.emit(
                    LocalDirectoryEffect.ShowToast(appContext.getString(R.string.error))
                )
            }
        }
    }

    private fun setSearchMode(enabled: Boolean) {
        _state.update {
            it.copy(isSearch = enabled, searchKey = if (enabled) it.searchKey else "")
        }
    }

    private fun entryComparator(sort: Int): Comparator<LocalDirectoryEntry> {
        val base = when (sort) {
            1 -> compareBy<LocalDirectoryEntry> { !it.isDir }.thenByDescending { it.size }
            2 -> compareBy<LocalDirectoryEntry> { !it.isDir }.thenByDescending { it.lastModified }
            else -> compareBy { !it.isDir }
        }
        return base then compareBy(AlphanumComparator) { it.name }
    }

    private fun startWatchingCurrent() {
        watchJob?.cancel()
        watchJob = viewModelScope.launch(Dispatchers.IO) { registerCurrentObserver() }
    }

    private fun registerCurrentObserver() {
        val dirs = _state.value.dirUris
        val currentUri = dirs.lastOrNull() ?: return
        unregisterObserver()
        // 部分 provider 不提供子文档 uri 或拒绝注册, 此处静默降级为仅兜底轮询
        runCatching {
            val uris = buildList {
                add(childDocumentsUri(currentUri))
                dirs.firstOrNull()?.takeIf { it != currentUri }?.let { add(childDocumentsUri(it)) }
            }
            val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    viewModelScope.launch(Dispatchers.IO) { loadCurrent() }
                }
            }
            observer = contentObserver
            uris.forEach {
                appContext.contentResolver.registerContentObserver(it, true, contentObserver)
            }
        }.onFailure { unregisterObserver() }
    }

    private fun childDocumentsUri(uriString: String): Uri {
        val uri = uriString.toUri()
        return DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getDocumentId(uri),
        )
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                loadCurrent()
            }
        }
    }

    private fun unregisterObserver() {
        observer?.let { runCatching { appContext.contentResolver.unregisterContentObserver(it) } }
        observer = null
    }

    override fun onCleared() {
        watchJob?.cancel()
        pollJob?.cancel()
        unregisterObserver()
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
