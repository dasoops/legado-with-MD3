package io.legado.app.ui.main.bookshelf

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookshelfRepository
import io.legado.app.domain.usecase.ExportBookshelfUseCase
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.exception.NoStackTraceException
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.utils.move
import io.legado.app.utils.GSON
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class BookshelfViewModel(
    application: Application,
    private val bookRepository: BookRepository,
    private val bookGroupRepository: BookGroupRepository,
    private val bookshelfRepository: BookshelfRepository,
    private val exportBookshelfUseCase: ExportBookshelfUseCase,
    private val bookshelfSettingsGateway: BookshelfSettingsGateway,
    private val appShellSettingsGateway: AppShellSettingsGateway,
    private val themeSettingsGateway: ThemeSettingsGateway,
) : BaseViewModel(application) {
    private val initialSettings = bookshelfSettingsGateway.currentSettings
    private val groupIdFlow = MutableStateFlow(initialSettings.saveTabPosition)
    private val searchKeyFlow = MutableStateFlow("")
    private val searchModeFlow = MutableStateFlow(false)
    private val loadingTextFlow = MutableStateFlow<String?>(null)
    private val activeOverlayFlow = MutableStateFlow<BookshelfOverlay?>(null)
    private val isEditModeFlow = MutableStateFlow(false)
    private val selectedBookUrlsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val isInFolderRootFlow = MutableStateFlow(initialSettings.bookGroupStyle == 2)
    private val bookGroupStyleFlow = MutableStateFlow(initialSettings.bookGroupStyle)
    private val draggingBooksFlow = MutableStateFlow<List<BookUiItem>?>(null)
    private val pendingSavedBooksFlow = MutableStateFlow<List<BookUiItem>?>(null)
    private val isInitialLoadingFlow = MutableStateFlow(true)
    private val localBookCoverBackfills = ConcurrentHashMap.newKeySet<String>()

    private data class BookshelfSortConfig(
        val sort: Int,
        val sortOrder: Int
    )

    private val bookshelfSettings = bookshelfSettingsGateway.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initialSettings)
    private val initialAppShellSettings = appShellSettingsGateway.currentSettings
    private val initialThemeSettings = themeSettingsGateway.currentSettings

    private val sortConfigFlow: StateFlow<BookshelfSortConfig> = bookshelfSettings
        .map { BookshelfSortConfig(it.bookshelfSort, it.bookshelfSortOrder) }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            BookshelfSortConfig(initialSettings.bookshelfSort, initialSettings.bookshelfSortOrder)
        )

    private val _scrollTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollTrigger = _scrollTrigger.asSharedFlow()

    private val _effects = MutableSharedFlow<BookshelfEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    val groupsFlow: SharedFlow<List<BookGroup>> = bookGroupRepository.flowShow()
        .onEach {
            if (it.isNotEmpty()) {
                isInitialLoadingFlow.value = false
            }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val allGroupsFlow: StateFlow<List<BookGroup>> = bookGroupRepository.flowAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class GroupPreviewState(
        val previews: ImmutableMap<Long, ImmutableList<BookUiItem>>,
        val counts: ImmutableMap<Long, Int>,
        val allBookCount: Int
    )

    val groupSelectorState: StateFlow<BookshelfGroupSelectorState> = combine(
        groupsFlow,
        groupIdFlow
    ) { groups, selectedGroupId ->
        BookshelfGroupSelectorState(
            groups = groups.map { it.toBookGroupUi() }.toImmutableList(),
            selectedGroupIndex = groups.indexOfFirst { it.groupId == selectedGroupId }
                .coerceAtLeast(0),
            selectedGroupId = selectedGroupId
        )
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookshelfGroupSelectorState())

    private data class SelectedGroupBooksState(
        val groupId: Long,
        val books: List<BookUiItem>,
        val sortConfig: BookshelfSortConfig
    )

    private data class SelectedBooksState(
        val groupId: Long,
        val books: List<BookUiItem>,
        val visibleBooks: List<BookUiItem>,
        val searchKey: String,
        val isSearchMode: Boolean,
        val sortConfig: BookshelfSortConfig
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedGroupBooksFlow: SharedFlow<SelectedGroupBooksState> =
        combine(groupIdFlow, allGroupsFlow) { id, groups ->
            groups.firstOrNull { it.groupId == id } ?: BookGroup(BookGroup.IdAll, "全部")
        }.flatMapLatest { group ->
            combine(bookRepository.flowBookShelfByGroup(group), sortConfigFlow) { list, sortConfig ->
                scheduleMissingLocalBookCoverBackfills(list)
                SelectedGroupBooksState(
                    groupId = group.groupId,
                    books = bookshelfRepository.sortBooks(list, group, sortConfig.sort, sortConfig.sortOrder)
                        .map { it.toUiItem() },
                    sortConfig = sortConfig
                )
            }
        }.distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    val booksFlow: Flow<List<BookUiItem>> = selectedGroupBooksFlow
        .map { it.books }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val allGroupBooksImmutableFlow: Flow<ImmutableMap<Long, ImmutableList<BookUiItem>>> =
        combine(groupsFlow, sortConfigFlow) { groups, sortConfig ->
            groups to sortConfig
        }.flatMapLatest { (groups, sortConfig) ->
            if (groups.isEmpty()) {
                flowOf(persistentMapOf())
            } else {
                val flows = groups.map { group ->
                    bookRepository.flowBookShelfByGroup(group).map { books ->
                        scheduleMissingLocalBookCoverBackfills(books)
                        group.groupId to bookshelfRepository.sortBooks(
                            books,
                            group,
                            sortConfig.sort,
                            sortConfig.sortOrder
                        ).map { it.toUiItem() }.toImmutableList()
                    }
                }
                combine(flows) { results ->
                    results.fold(persistentMapOf<Long, ImmutableList<BookUiItem>>()) { acc, (id, list) ->
                        acc.putting(id, list)
                    }
                }
            }
        }.distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5000), replay = 1)

    private val selectedBooksStateFlow: Flow<SelectedBooksState> = combine(
        selectedGroupBooksFlow,
        searchKeyFlow,
        searchModeFlow
    ) { selectedGroup, searchKey, isSearchMode ->
        SelectedBooksState(
            groupId = selectedGroup.groupId,
            books = selectedGroup.books,
            visibleBooks = filterBooks(selectedGroup.books, searchKey, isSearchMode),
            searchKey = searchKey,
            isSearchMode = isSearchMode,
            sortConfig = selectedGroup.sortConfig
        )
    }.distinctUntilChanged()

    private val visibleBooksFlow: Flow<List<BookUiItem>> = selectedBooksStateFlow
        .map { it.visibleBooks }
        .distinctUntilChanged()

    private val selectedGroupCanReorderFlow = combine(
        isEditModeFlow,
        searchModeFlow,
        groupIdFlow,
        groupsFlow,
        sortConfigFlow
    ) { isEditMode, isSearchMode, groupId, groups, sortConfig ->
        val group = groups.find { it.groupId == groupId }
        val bookSort = group?.bookSort?.takeIf { it >= 0 } ?: sortConfig.sort
        isEditMode && !isSearchMode && bookSort == 3
    }.distinctUntilChanged()

    private val selectedVisibleBookUrlsFlow = combine(
        selectedBookUrlsFlow,
        visibleBooksFlow
    ) { selectedBookUrls, visibleBooks ->
        val visibleBookUrls = visibleBooks.mapTo(hashSetOf()) { it.book.bookUrl }
        selectedBookUrls.intersect(visibleBookUrls)
    }.distinctUntilChanged()

    private val groupPreviewsFlow = combine(
        allGroupBooksImmutableFlow, bookRepository.flowAllBookShelfCount()
    ) { groups, count ->
        GroupPreviewState(
            groups.mapValues { (_, books) -> books.take(9).toImmutableList() }.toImmutableMap(),
            groups.mapValues { (_, books) -> books.size }.toImmutableMap(),
            count
        )
    }.distinctUntilChanged()

    private val internalStateFlow = combine(
        groupIdFlow,
        loadingTextFlow
    ) { groupId, loadingText ->
        InternalState(
            groupId = groupId,
            loadingText = loadingText
        )
    }

    private data class InternalState(
        val groupId: Long,
        val loadingText: String?
    )

    data class BookshelfInteractionState(
        val activeOverlay: BookshelfOverlay?,
        val isEditMode: Boolean,
        val selectedBookUrls: Set<String>,
        val isInFolderRoot: Boolean,
        val bookGroupStyle: Int,
        val draggingBooks: List<BookUiItem>?,
        val pendingSavedBooks: List<BookUiItem>?
    )

    private val interactionStateFlow = combine(
        activeOverlayFlow,
        isEditModeFlow,
        selectedVisibleBookUrlsFlow,
        isInFolderRootFlow
    ) { activeOverlay, isEditMode, selectedBookUrls, isInFolderRoot ->
        BookshelfInteractionState(
            activeOverlay = activeOverlay,
            isEditMode = isEditMode,
            selectedBookUrls = selectedBookUrls,
            isInFolderRoot = isInFolderRoot,
            bookGroupStyle = 0,
            draggingBooks = null,
            pendingSavedBooks = null
        )
    }.combine(
        combine(bookGroupStyleFlow, draggingBooksFlow, pendingSavedBooksFlow) { a, b, c ->
            Triple(a, b, c)
        }
    ) { interaction, (bookGroupStyle, draggingBooks, pendingSavedBooks) ->
        interaction.copy(
            bookGroupStyle = bookGroupStyle,
            draggingBooks = draggingBooks,
            pendingSavedBooks = pendingSavedBooks
        )
    }

    private val groupPreviewsStateFlow = MutableStateFlow(
        GroupPreviewState(persistentMapOf(), persistentMapOf(), 0)
    )

    private val dataStateFlow = combine(
        selectedBooksStateFlow,
        groupsFlow,
        allGroupsFlow,
        groupPreviewsStateFlow,
        internalStateFlow
    ) { selectedBooks, groups, allGroups, previews, internal ->
        BookshelfDataCore(selectedBooks, groups, allGroups, previews, internal)
    }.combine(allGroupBooksImmutableFlow) { core, allGroupBooks ->
        BookshelfDataState(
            selectedBooks = core.selectedBooks,
            groups = core.groups.map { it.toBookGroupUi() },
            allGroups = core.allGroups.map { it.toBookGroupUi() },
            previews = core.previews,
            internal = core.internal,
            allGroupBooks = allGroupBooks
        )
    }

    private data class BookshelfDataCore(
        val selectedBooks: SelectedBooksState,
        val groups: List<BookGroup>,
        val allGroups: List<BookGroup>,
        val previews: GroupPreviewState,
        val internal: InternalState
    )

    private data class BookshelfDataState(
        val selectedBooks: SelectedBooksState,
        val groups: List<BookGroupUi>,
        val allGroups: List<BookGroupUi>,
        val previews: GroupPreviewState,
        val internal: InternalState,
        val allGroupBooks: ImmutableMap<Long, ImmutableList<BookUiItem>>
    )

    private val contentUiState: Flow<BookshelfUiState> = combine(
        dataStateFlow,
        interactionStateFlow,
        isInitialLoadingFlow
    ) { data, interaction, isInitialLoading ->
        val selectedBooks = data.selectedBooks
        val groups = data.groups
        val allGroups = data.allGroups
        val previews = data.previews
        val internal = data.internal
        val visibleGroupBooks =
            if (!selectedBooks.isSearchMode || selectedBooks.searchKey.isBlank()) {
                data.allGroupBooks
            } else {
                data.allGroupBooks.mapValues { (_, books) ->
                    filterBooks(books, selectedBooks.searchKey, true).toImmutableList()
                }.toImmutableMap()
            }
        val books = data.allGroupBooks[internal.groupId]
            ?: selectedBooks.books.takeIf { selectedBooks.groupId == internal.groupId }
            ?: emptyList()
        val filteredBooks = visibleGroupBooks[internal.groupId]
            ?: selectedBooks.visibleBooks.takeIf { selectedBooks.groupId == internal.groupId }
            ?: emptyList()
        val selectedGroupIndex = groups.indexOfFirst { it.groupId == internal.groupId }
            .coerceAtLeast(0)
        val currentGroupName = allGroups.firstOrNull { it.groupId == internal.groupId }?.groupName
            ?: groups.getOrNull(selectedGroupIndex)?.groupName
        val selectedIds = interaction.selectedBookUrls.mapTo(linkedSetOf<Any>()) { it }
        val title = buildTitle(
            bookGroupStyle = interaction.bookGroupStyle,
            isInFolderRoot = interaction.isInFolderRoot,
            isEditMode = interaction.isEditMode,
            isSearchMode = selectedBooks.isSearchMode,
            currentGroupName = currentGroupName
        )

        BookshelfUiState(
            items = filteredBooks.toImmutableList(),
            selectedIds = selectedIds.toImmutableSet(),
            isInitialLoading = isInitialLoading,
            groups = groups.toImmutableList(),
            allGroups = allGroups.toImmutableList(),
            groupPreviews = previews.previews,
            groupBookCounts = previews.counts,
            currentGroupBookCount = books.size,
            allBooksCount = previews.allBookCount,
            selectedGroupIndex = selectedGroupIndex,
            selectedGroupId = internal.groupId,
            searchKey = selectedBooks.searchKey,
            isSearch = selectedBooks.isSearchMode,
            isLoading = internal.loadingText != null,
            loadingText = internal.loadingText,
            activeOverlay = interaction.activeOverlay,
            isEditMode = interaction.isEditMode,
            selectedBookUrls = interaction.selectedBookUrls.toImmutableSet(),
            isInFolderRoot = interaction.isInFolderRoot,
            bookGroupStyle = interaction.bookGroupStyle,
            bookshelfSort = selectedBooks.sortConfig.sort,
            bookshelfSortOrder = selectedBooks.sortConfig.sortOrder,
            title = title,
            subtitle = when {
                interaction.isEditMode -> {
                    context.getString(R.string.bookshelf_total_count, previews.allBookCount)
                }

                selectedBooks.isSearchMode -> {
                    context.getString(R.string.bookshelf_total_count, filteredBooks.size)
                }

                else -> null
            },
            currentGroupName = currentGroupName,
            draggingBooks = interaction.draggingBooks?.toImmutableList(),
            pendingSavedBooks = interaction.pendingSavedBooks?.toImmutableList(),
            visibleGroupBooks = visibleGroupBooks
        )
    }

    val uiState: StateFlow<BookshelfUiState> = combine(
        contentUiState,
        bookshelfSettings,
        appShellSettingsGateway.settings,
        themeSettingsGateway.settings,
    ) { state, settings, appShellSettings, themeSettings ->
        state.copy(
            settings = settings,
            useRaisedBottomInset = appShellSettings.useFloatingBottomBar || themeSettings.enableBlur,
            enableCustomTagColors = themeSettings.enableCustomTagColors,
            customTagColors = parseTagColors(themeSettings.customTagColorsJson),
            themeColor = themeSettings.themeColor,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BookshelfUiState(
            settings = initialSettings,
            selectedGroupId = initialSettings.saveTabPosition,
            isInFolderRoot = initialSettings.bookGroupStyle == 2,
            bookGroupStyle = initialSettings.bookGroupStyle,
            useRaisedBottomInset = initialAppShellSettings.useFloatingBottomBar || initialThemeSettings.enableBlur,
            enableCustomTagColors = initialThemeSettings.enableCustomTagColors,
            customTagColors = parseTagColors(initialThemeSettings.customTagColorsJson),
            themeColor = initialThemeSettings.themeColor,
        ),
    )

    private fun parseTagColors(json: String?): ImmutableList<TagColorPair> = try {
        if (json.isNullOrBlank()) persistentListOf()
        else GSON.fromJson(json, Array<TagColorPair>::class.java).toImmutableList()
    } catch (_: Exception) {
        persistentListOf()
    }

    init {
        viewModelScope.launch {
            groupsFlow.collect { groups ->
                if (groups.isNotEmpty() && groups.none { it.groupId == groupIdFlow.value }) {
                    changeGroup(groups.first().groupId)
                }
            }
        }
        viewModelScope.launch {
            delay(500)
            isInitialLoadingFlow.value = false
        }
        viewModelScope.launch {
            bookshelfSettings.collect { settings ->
                if (groupIdFlow.value != settings.saveTabPosition) {
                    groupIdFlow.value = settings.saveTabPosition
                    clearSelection()
                    clearDragState()
                }
                updateBookGroupStyle(settings.bookGroupStyle)
            }
        }

        viewModelScope.launch {
            groupPreviewsFlow.collect { groupPreviewsStateFlow.value = it }
        }
        viewModelScope.launch {
            combine(booksFlow, selectedGroupCanReorderFlow) { books, canReorderBooks ->
                books to canReorderBooks
            }.collect { (books, canReorderBooks) ->
                syncDragState(books, canReorderBooks)
            }
        }
    }

    fun onIntent(intent: BookshelfIntent) {
        when (intent) {
            is BookshelfIntent.ChangeGroup -> changeGroup(intent.groupId)
            is BookshelfIntent.SetSearchKey -> setSearchKey(intent.value)
            is BookshelfIntent.SetSearchMode -> setSearchMode(intent.active)
            is BookshelfIntent.ShowOverlay -> showOverlay(intent.overlay)
            BookshelfIntent.DismissOverlay -> dismissOverlay()
            BookshelfIntent.ToggleEditMode -> toggleEditMode()
            BookshelfIntent.ExitEditMode -> exitEditMode()
            BookshelfIntent.ClearSelection -> clearSelection()
            BookshelfIntent.SelectAllVisible -> selectAllVisible()
            BookshelfIntent.InvertVisibleSelection -> invertVisibleSelection()
            is BookshelfIntent.ToggleBookSelection -> toggleBookSelection(intent.bookUrl)
            is BookshelfIntent.SetInFolderRoot -> setInFolderRoot(intent.value)
            is BookshelfIntent.AddTags -> addTags(intent.bookUrls, intent.tags)
            is BookshelfIntent.StartDragging -> startDraggingBooks(intent.books)
            is BookshelfIntent.MoveDragging -> moveDraggingBook(intent.from, intent.to, intent.books)
            BookshelfIntent.FinishDragging -> finishDraggingBooks()
            BookshelfIntent.ScrollToTop -> gotoTop()
            is BookshelfIntent.ExportToUri -> exportToUri(intent.uri, intent.books)
            is BookshelfIntent.UpdateSetting -> viewModelScope.launch {
                bookshelfSettingsGateway.update(intent.transform)
            }
            is BookshelfIntent.SetCustomTagColorsEnabled -> viewModelScope.launch {
                themeSettingsGateway.update {
                    it.copy(enableCustomTagColors = intent.enabled)
                }
            }
            is BookshelfIntent.SetCustomTagColors -> viewModelScope.launch {
                themeSettingsGateway.update {
                    it.copy(customTagColorsJson = GSON.toJson(intent.colors))
                }
            }
        }
    }

    private fun filterBooks(
        books: List<BookUiItem>,
        searchKey: String,
        isSearchMode: Boolean
    ): List<BookUiItem> {
        return if (!isSearchMode || searchKey.isBlank()) {
            books
        } else {
            books.filter { it.matches(searchKey) }
        }
    }

    private fun buildTitle(
        bookGroupStyle: Int,
        isInFolderRoot: Boolean,
        isEditMode: Boolean,
        isSearchMode: Boolean,
        currentGroupName: String?
    ): String {
        val bookshelfTitle = context.getString(R.string.bookshelf)
        val baseTitle = when {
            isSearchMode && bookGroupStyle == 0 -> bookshelfTitle
            isSearchMode -> currentGroupName ?: bookshelfTitle
            bookGroupStyle == 1 -> currentGroupName ?: bookshelfTitle
            bookGroupStyle == 2 -> if (isInFolderRoot) {
                bookshelfTitle
            } else {
                currentGroupName ?: bookshelfTitle
            }

            else -> bookshelfTitle
        }
        return if (isEditMode) bookshelfTitle else baseTitle
    }

    fun changeGroup(groupId: Long) {
        if (groupIdFlow.value != groupId) {
            groupIdFlow.value = groupId
            viewModelScope.launch {
                bookshelfSettingsGateway.update { it.copy(saveTabPosition = groupId) }
            }
            clearSelection()
            clearDragState()
        }
    }

    private fun scheduleMissingLocalBookCoverBackfills(books: List<BookShelfItem>) {
        books.asSequence()
            .filter { it.isLocal && it.coverUrl.isNullOrBlank() }
            .forEach { book ->
                if (localBookCoverBackfills.add(book.bookUrl)) {
                    viewModelScope.launch {
                        try {
                            bookRepository.backfillLocalBookCoverIfMissing(book.bookUrl)
                        } finally {
                            localBookCoverBackfills.remove(book.bookUrl)
                        }
                    }
                }
            }
    }

    fun setSearchKey(key: String) {
        searchKeyFlow.value = key
    }

    fun setSearchMode(active: Boolean) {
        searchModeFlow.value = active
        if (!active) {
            searchKeyFlow.value = ""
        }
        clearSelection()
    }

    fun showOverlay(overlay: BookshelfOverlay) {
        activeOverlayFlow.value = overlay
    }

    fun dismissOverlay() {
        activeOverlayFlow.value = null
    }

    fun toggleEditMode() {
        if (isEditModeFlow.value) {
            exitEditMode()
            return
        }
        if (bookGroupStyleFlow.value == 2 && isInFolderRootFlow.value) {
            isInFolderRootFlow.value = false
        }
        isEditModeFlow.value = true
        clearSelection()
    }

    fun exitEditMode() {
        isEditModeFlow.value = false
        clearSelection()
        clearDragState()
    }

    fun clearSelection() {
        selectedBookUrlsFlow.value = emptySet()
    }

    fun selectAllVisible() {
        selectedBookUrlsFlow.value = uiState.value.items.mapTo(hashSetOf()) { it.book.bookUrl }
    }

    fun invertVisibleSelection() {
        val visibleBookUrls = uiState.value.items.mapTo(hashSetOf()) { it.book.bookUrl }
        selectedBookUrlsFlow.value = visibleBookUrls - selectedBookUrlsFlow.value
    }

    fun toggleBookSelection(bookUrl: String) {
        selectedBookUrlsFlow.value = if (selectedBookUrlsFlow.value.contains(bookUrl)) {
            selectedBookUrlsFlow.value - bookUrl
        } else {
            selectedBookUrlsFlow.value + bookUrl
        }
    }

    fun setInFolderRoot(isInFolderRoot: Boolean) {
        if (isInFolderRootFlow.value != isInFolderRoot) {
            isInFolderRootFlow.value = isInFolderRoot
            clearSelection()
            clearDragState()
        }
    }

    private fun updateBookGroupStyle(bookGroupStyle: Int) {
        val previousStyle = bookGroupStyleFlow.value
        if (previousStyle == bookGroupStyle) return
        bookGroupStyleFlow.value = bookGroupStyle
        if (bookGroupStyle == 2 && previousStyle != 2) {
            isInFolderRootFlow.value = true
        } else if (bookGroupStyle != 2) {
            isInFolderRootFlow.value = false
        }
        clearSelection()
        clearDragState()
    }

    fun addTags(bookUrls: Set<String>, tags: Set<String>) {
        execute { bookRepository.addTags(bookUrls, tags) }.onSuccess {
            dismissOverlay()
            clearSelection()
        }.onError {
            showMessage("添加标签失败\n${it.localizedMessage}")
        }
    }

    fun saveBookOrder(reorderedBooks: List<BookUiItem>) {
        if (reorderedBooks.isEmpty()) return
        val isDescending = bookshelfSettings.value.bookshelfSortOrder == 1
        val maxOrder = reorderedBooks.size
        execute {
            val updates = reorderedBooks.mapIndexedNotNull { index, bookUi ->
                bookRepository.getBook(bookUi.book.bookUrl)?.apply {
                    order = if (isDescending) maxOrder - index else index + 1
                }
            }
            if (updates.isNotEmpty()) {
                bookRepository.update(*updates.toTypedArray())
            }
        }.onError {
            showMessage("排序保存失败\n${it.localizedMessage}")
        }
    }

    fun startDraggingBooks(books: List<BookUiItem>) {
        draggingBooksFlow.value = books
    }

    fun moveDraggingBook(fromIndex: Int, toIndex: Int, fallbackBooks: List<BookUiItem>) {
        if (fromIndex == toIndex) return
        val sourceBooks = draggingBooksFlow.value ?: fallbackBooks
        if (fromIndex !in sourceBooks.indices || toIndex !in sourceBooks.indices) return
        draggingBooksFlow.value = sourceBooks.toMutableList().apply {
            move(fromIndex, toIndex)
        }
    }

    fun finishDraggingBooks() {
        val reorderedUiBooks = draggingBooksFlow.value ?: return
        pendingSavedBooksFlow.value = reorderedUiBooks
        draggingBooksFlow.value = null
        saveBookOrder(reorderedUiBooks)
    }

    private fun syncDragState(books: List<BookUiItem>, canReorderBooks: Boolean) {
        if (!canReorderBooks) {
            clearDragState()
            return
        }
        val pending = pendingSavedBooksFlow.value ?: return
        if (books.map { it.book.bookUrl } == pending.map { it.book.bookUrl }) {
            pendingSavedBooksFlow.value = null
        }
    }

    private fun clearDragState() {
        draggingBooksFlow.value = null
        pendingSavedBooksFlow.value = null
    }

    fun gotoTop() {
        _scrollTrigger.tryEmit(Unit)
    }

    fun exportToUri(uri: Uri, items: List<BookUiItem>) {
        execute {
            exportBookshelfUseCase.exportToUri(uri, items).getOrThrow()
        }.onSuccess {
            _effects.tryEmit(BookshelfEffect.ShowSnackbar("导出成功"))
        }.onError {
            _effects.tryEmit(BookshelfEffect.ShowSnackbar("导出失败\n${it.localizedMessage}"))
        }
    }

    fun exportBookshelf(items: List<BookUiItem>?, success: (file: File) -> Unit) {
        execute {
            items ?: throw NoStackTraceException("书籍不能为空")
            exportBookshelfUseCase.exportToFile(items).getOrThrow()
        }.onSuccess {
            success(it)
        }.onError {
            showMessage("导出书籍出错\n${it.localizedMessage}")
        }
    }

    private fun BookShelfItem.matchesSearchKey(searchKey: String): Boolean {
        return name.contains(searchKey, true) ||
                author.contains(searchKey, true) ||
                originName.contains(searchKey, true) ||
                kind?.contains(searchKey, true) == true ||
                customTag?.contains(searchKey, true) == true
    }

    private fun showMessage(resId: Int) = showMessage(context.getString(resId))

    private fun showMessage(message: String) {
        _effects.tryEmit(BookshelfEffect.ShowSnackbar(message))
    }

}
