package io.legado.app.ui.book.info

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.request.SuccessResult
import coil3.toBitmap
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.HighlightTagRuleRepository
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.BookTags
import io.legado.app.domain.model.settings.CoverSettings
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.addType
import io.legado.app.help.book.getDisplayTagList
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.parseHighlightedTags
import io.legado.app.help.book.upKind
import io.legado.app.lib.webdav.ObjectNotFoundException
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.main.MainIntent
import io.legado.app.ui.widget.components.image.cover.buildCoverImageRequest
import io.legado.app.utils.ImageSaveUtils
import java.io.ByteArrayOutputStream
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookInfoViewModel(
    application: Application,
    private val readRecordRepository: ReadRecordRepository,
    private val clearBookCacheUseCase: ClearBookCacheUseCase,
    private val bookGroupRepository: BookGroupRepository,
    private val bookRepository: BookRepository,
    private val highlightTagRuleRepository: HighlightTagRuleRepository,
    private val imageLoader: ImageLoader,
    private val themeSettingsGateway: ThemeSettingsGateway,
    private val coverSettingsGateway: CoverSettingsGateway,
) : BaseViewModel(application) {

    val tagNames = bookRepository.flowTagNames()
        .map { tags -> tags.filterNot { it in BookTags.builtIn }.toImmutableList() }

    // 仅保存“每本书/屏幕”状态；外观与其他设置不在此存储，避免整体重置时被抹掉。
    private val screenState = MutableStateFlow(BookInfoUiState())

    // 设置类字段始终从各自 gateway（唯一 SSOT）派生叠加，重置屏幕状态无法影响它们。
    val uiState: StateFlow<BookInfoUiState> = combine(
        screenState,
        themeSettingsGateway.settings,
        coverSettingsGateway.settings,
    ) { screen, theme, cover ->
        screen.withSettings(theme, cover)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BookInfoUiState().withSettings(
            themeSettingsGateway.currentSettings,
            coverSettingsGateway.currentSettings,
        ),
    )

    private val _effects = MutableSharedFlow<BookInfoEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    init {
        collectEventBus()
    }

    private fun collectEventBus() {
        viewModelScope.launch {
            eventFlow<Boolean>(EventBus.REFRESH_BOOK_INFO).collect {
                currentBook?.let { book ->
                    refreshBook(book)
                }
            }
        }
        viewModelScope.launch {
            eventFlow<Boolean>(EventBus.REFRESH_BOOK_TOC).collect {
                currentBook?.let { book ->
                    loadChapter(book)
                }
            }
        }
    }

    private inline fun <reified T> eventFlow(tag: String): Flow<T> = callbackFlow {
        val obs = androidx.lifecycle.Observer<T> { trySend(it) }
        com.jeremyliao.liveeventbus.LiveEventBus.get<T>(tag).observeForever(obs)
        awaitClose {
            com.jeremyliao.liveeventbus.LiveEventBus.get<T>(tag).removeObserver(obs)
        }
    }

    private var currentBook: Book? = null
        set(value) {
            field = value
            observeReadRecordIfNeeded(value)
        }
    private var currentChapterList: List<BookChapter> = emptyList()
    private var tocLoadFailed = false
    private var currentHighlightedTags: List<HighlightedTag> = emptyList()
    private var currentKindLabels: List<String> = emptyList()
    private var currentGroupNames: String? = null
    private var currentHasCustomGroup = false
    private var currentReadRecordTotalTime = 0L
    private var currentReadRecordTimelineDays: List<ReadRecordTimelineDay> = emptyList()
    private var observingReadRecordKey: String? = null
    private var chapterChanged = false

    var inBookshelf = false
        private set

    private var readRecordObserveJob: Job? = null

    fun initData(intent: Intent) {
        initData(
            bookUrl = intent.getStringExtra(MainIntent.EXTRA_BOOK_URL) ?: "",
            name = intent.getStringExtra(MainIntent.EXTRA_BOOK_NAME),
            author = intent.getStringExtra(MainIntent.EXTRA_BOOK_AUTHOR),
            origin = intent.getStringExtra(MainIntent.EXTRA_BOOK_ORIGIN),
            coverPath = intent.getStringExtra(MainIntent.EXTRA_BOOK_COVER),
        )
    }

    fun initData(
        bookUrl: String,
        name: String? = null,
        author: String? = null,
        origin: String? = null,
        coverPath: String? = null,
    ) {
        val current = currentBook
        if (current != null) return
        currentBook = if (!name.isNullOrBlank() && !author.isNullOrBlank()) {
            Book(
                bookUrl = bookUrl,
                name = name,
                author = author,
                origin = origin ?: BookType.localTag,
                coverUrl = coverPath,
            ).apply {
                addType(BookType.notShelf)
            }
        } else {
            null
        }
        clearReadRecordObserve()
        syncUiState()
        execute {
            val dbBook = bookRepository.getBook(bookUrl)
            if (dbBook != null) {
                inBookshelf = !dbBook.isNotShelf
                dbBook
            } else {
                currentBook ?: throw NoStackTraceException("未找到书籍")
            }
        }.onSuccess { book ->
            // 如果从数据库拿到的书没有封面，但我们有传入的封面，则保留传入的封面
            if (book.coverUrl.isNullOrBlank() && !coverPath.isNullOrBlank()) {
                book.coverUrl = coverPath
            }
            upBook(book)
        }.onError {
            showMessage(it.localizedMessage ?: "未找到书籍")
            emitEffect(BookInfoEffect.Finish(afterTransition = true))
        }
    }

    fun onIntent(intent: BookInfoIntent) {
        when (intent) {
            BookInfoIntent.DismissSheet -> dismissSheet()
            BookInfoIntent.DismissDialog -> dismissDialog()
            is BookInfoIntent.MenuAction -> handleMenuAction(intent.action)
            BookInfoIntent.ReadClick -> onReadClick()
            BookInfoIntent.OpenLocalBookExternally ->
                currentBook
                    ?.takeIf { it.isLocal }
                    ?.let { emitEffect(BookInfoEffect.OpenLocalBookExternally(Uri.parse(it.bookUrl))) }
            BookInfoIntent.TocClick -> onTocClick()
            BookInfoIntent.CoverPreviewClick -> currentBook?.getDisplayCover()?.takeIf { it.isNotBlank() }
                ?.let { showDialog(BookInfoDialog.PhotoPreview(it)) }
            BookInfoIntent.GroupClick -> setSheet(BookInfoSheet.GroupPicker)
            BookInfoIntent.ReadRecordClick -> setSheet(BookInfoSheet.ReadRecord)
            BookInfoIntent.RemarkClick -> showDialog(BookInfoDialog.EditRemark(currentBook?.remark))
            is BookInfoIntent.SaveCover -> {
                saveCoverToGallery(intent.path)
            }
            is BookInfoIntent.UpdateRemark -> {
                dismissDialog()
                saveRemark(intent.remark)
            }
            is BookInfoIntent.AddTags -> addTags(intent.tags)
            is BookInfoIntent.IntroImageLongClick -> showDialog(
                BookInfoDialog.PhotoPreview(intent.source),
            )
        }
    }

    fun openEdit() {
        currentBook?.let {
            emitEffect(BookInfoEffect.OpenBookInfoEdit(it.bookUrl))
        }
    }

    fun refreshCurrentBook() {
        currentBook?.let {
            refreshBook(it)
        }
    }

    fun onInfoEdited() {
        currentBook?.bookUrl?.let { bookUrl ->
            execute {
                val book = bookRepository.getBook(bookUrl) ?: return@execute null
                book
            }.onSuccess {
                it?.let { upBook(it) }
            }
        }
    }

    fun onTocResult(result: Triple<Int, Int, Boolean>?) {
        if (result == null) {
            if (!inBookshelf) {
                delBook()
            }
            return
        }
        chapterChanged = result.third
        val book = currentBook ?: return
        execute {
            book.durChapterIndex = result.first
            book.durChapterPos = result.second
            bookRepository.update(book)
            book
        }.onSuccess {
            currentBook = it
            syncUiState(isTocLoading = false)
            openReader(it)
        }
    }

    fun refreshShelfState() {
        val bookUrl = currentBook?.bookUrl ?: return
        execute {
            bookRepository.getBook(bookUrl)
        }.onSuccess { dbBook ->
            val nextInBookshelf = dbBook != null && !dbBook.isNotShelf
            if (nextInBookshelf) {
                currentBook = dbBook
            }
            if (inBookshelf != nextInBookshelf || nextInBookshelf) {
                inBookshelf = nextInBookshelf
                syncUiState()
            }
        }
    }

    fun topBook() {
        currentBook?.let { book ->
            execute {
                val minOrder = bookRepository.getMinOrder()
                book.order = minOrder - 1
                book.durChapterTime = System.currentTimeMillis()
                bookRepository.update(book)
                book
            }.onSuccess {
                currentBook = it
                syncUiState()
            }
        }
    }
    fun clearCache() {
        currentBook?.let { book ->
            execute {
                clearBookCacheUseCase.execute(book.bookUrl)
                if (ReadBook.book?.bookUrl == book.bookUrl) {
                    ReadBook.clearTextChapter()
                }
            }.onSuccess {
                showMessage(R.string.clear_cache_success)
            }.onError {
                showMessage("清理缓存出错\n${it.localizedMessage}")
            }
        }
    }

    private fun saveCoverToGallery(path: String) {
        execute {
            setBusy(true)
            val request = buildCoverImageRequest(
                context = context,
                data = path,
                sourceOrigin = null,
                loadOnlyWifi = coverSettingsGateway.currentSettings.loadOnlyOnWifi,
                crossfade = false,
            )
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = result.image.toBitmap()
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                val byteArray = outputStream.toByteArray()
                ImageSaveUtils.saveImageToGallery(context, byteArray, "Cover_")
            } else {
                false
            }
        }.onSuccess { success ->
            if (success) {
                showMessage("保存成功")
            } else {
                showMessage("保存失败")
            }
        }.onFinally {
            setBusy(false)
        }.onError {
            showMessage("保存出错: ${it.localizedMessage}")
        }
    }

    fun saveRemark(remark: String, success: (() -> Unit)? = null) {
        currentBook?.let { book ->
            execute {
                book.remark = remark
                book.save()
                book
            }.onSuccess {
                currentBook = it
                syncUiState()
                success?.invoke()
            }
        }
    }

    fun saveBook(book: Book?, success: (() -> Unit)? = null) {
        book ?: return
        execute {
            if (book.order == 0) {
                book.order = bookRepository.getMinOrder() - 1
            }
            bookRepository.getBook(book.name, book.author)?.let {
                book.durChapterIndex = it.durChapterIndex
                book.durChapterPos = it.durChapterPos
                book.durChapterTitle = it.durChapterTitle
            }
            book.save()
            if (ReadBook.isCurrentBook(book)) {
                ReadBook.replaceCurrentBook(book)
            }
            book
        }.onSuccess {
            if (currentBook?.bookUrl == it.bookUrl) {
                currentBook = it
                syncUiState()
            }
            success?.invoke()
        }
    }

    fun saveChapterList(success: (() -> Unit)? = null) {
        execute {
            bookRepository.insertChapters(*currentChapterList.toTypedArray())
        }.onSuccess {
            success?.invoke()
        }
    }

    fun delBook(deleteOriginal: Boolean = false, success: (() -> Unit)? = null) {
        val book = currentBook ?: return
        execute {
            inBookshelf = false
            if (book.isLocal) {
                LocalBook.deleteBook(book, deleteOriginal)
            }
            book.delete()
        }.onSuccess {
            success?.invoke()
        }
    }

    fun refreshBook(book: Book) {
        syncUiState(isTocLoading = true)
        execute {
            LocalBook.upBookInfo(book)
            book
        }.onError {
            when (it) {
                is ObjectNotFoundException -> {
                    book.origin = BookType.localTag
                }
                else -> AppLog.put("刷新书籍失败", it)
            }
        }.onFinally {
            loadBookInfo(book)
        }
    }

    fun loadBookInfo(
        book: Book,
        showLoading: Boolean = true,
    ) {
        syncUiState(isTocLoading = showLoading)
        if (book.isLocal) {
            LocalBook.upBookInfo(book)
            currentBook = book
            syncUiState(isTocLoading = showLoading)
            loadChapter(book, showLoading = showLoading)
        } else {
            currentChapterList = emptyList()
            syncUiState(isTocLoading = false)
            showMessage(R.string.error_no_source)
        }
    }
    private fun upBook(book: Book) {
        currentBook = book
        currentChapterList = emptyList()
        tocLoadFailed = false
        currentKindLabels = emptyList()
        currentGroupNames = null
        currentHasCustomGroup = false
        syncUiState(isTocLoading = false)
        refreshMeta(book)
        execute {
            bookRepository.getChapters(book.bookUrl)
        }.onSuccess { chapters ->
            if (chapters.isNotEmpty()) {
                currentChapterList = chapters
                syncUiState(isTocLoading = false)
            } else {
                loadChapter(book, showLoading = false)
            }
        }.onError {
            loadChapter(book, showLoading = false)
        }
    }

    private fun refreshMeta(book: Book) {
        execute {
            book.upKind()
            val userGroupIds = bookGroupRepository.getIdsSum()
            val groupAnd = userGroupIds and book.group
            val hasCustomGroup = book.group > 0L && groupAnd != 0L
            val groupNames = bookGroupRepository.getGroupNames(book.group).joinToString(",")
            val normalizedGroupNames = groupNames.ifBlank { null }
            bookRepository.update(book)
            val finalKinds = book.getDisplayTagList()
            val enabledRules = highlightTagRuleRepository.getEnabled()
            val (highlighted, regular) = parseHighlightedTags(finalKinds, enabledRules)
            HighlightMeta(highlighted, regular, normalizedGroupNames, hasCustomGroup)
        }.onSuccess {
            currentHighlightedTags = it.highlighted
            currentKindLabels = it.regular
            currentGroupNames = it.groupNames
            currentHasCustomGroup = it.hasCustomGroup
            syncUiState()
        }
    }

    private data class HighlightMeta(
        val highlighted: List<HighlightedTag>,
        val regular: List<String>,
        val groupNames: String?,
        val hasCustomGroup: Boolean,
    )

    private fun loadChapter(
        book: Book,
        scope: CoroutineScope = viewModelScope,
        showLoading: Boolean = true,
    ) {
        tocLoadFailed = false
        syncUiState(isTocLoading = showLoading)
        if (book.isLocal) {
            execute(scope) {
                LocalBook.getChapterList(book).also {
                    bookRepository.update(book)
                    bookRepository.deleteChaptersByBook(book.bookUrl)
                    bookRepository.insertChapters(*it.toTypedArray())
                    ReadBook.onChapterListUpdated(book)
                }
            }.onSuccess {
                currentBook = book
                currentChapterList = it
                syncUiState(isTocLoading = false)
            }.onError {
                currentChapterList = emptyList()
                tocLoadFailed = true
                syncUiState(isTocLoading = false)
            }
        } else {
            currentChapterList = emptyList()
            tocLoadFailed = true
            syncUiState(isTocLoading = false)
            showMessage(R.string.error_no_source)
        }
    }

    private fun onReadClick() {
        val book = currentBook ?: return
        if (inBookshelf) {
            readBook(book)
        } else {
            showMessage("书籍尚未通过本地目录导入")
        }
    }

    private fun onTocClick() {
        val book = currentBook ?: return
        if (currentChapterList.isEmpty()) {
            showMessage(R.string.chapter_list_empty)
            return
        }
        if (inBookshelf) {
            emitEffect(BookInfoEffect.OpenToc(book.bookUrl))
        } else {
            showMessage("书籍尚未通过本地目录导入")
        }
    }

    private fun addTags(tags: Set<String>) {
        val book = currentBook ?: return
        execute {
            bookRepository.addTags(setOf(book.bookUrl), tags)
            bookRepository.getBook(book.bookUrl)
        }.onSuccess { updated ->
            updated?.let {
                currentBook = it
                refreshMeta(it)
                syncUiState()
            }
            dismissSheet()
        }.onError {
            showMessage("添加标签失败\n${it.localizedMessage}")
        }
    }

    private fun readBook(book: Book) {
        if (!inBookshelf) return
        saveBook(book) { openReader(book) }
    }

    private fun openReader(book: Book) {
        emitEffect(BookInfoEffect.OpenReader(book.uiCopy(), inBookshelf, chapterChanged))
    }

    private fun handleMenuAction(action: BookInfoMenuAction) {
        if (currentBook == null) return
        when (action) {
            BookInfoMenuAction.Edit -> openEdit()
            BookInfoMenuAction.Refresh -> refreshCurrentBook()
            BookInfoMenuAction.ReadRecord -> setSheet(BookInfoSheet.ReadRecord)
            BookInfoMenuAction.Top -> topBook()
            BookInfoMenuAction.ClearCache -> emitEffect(BookInfoEffect.ClearCache)
        }
    }

    private fun observeReadRecordIfNeeded(book: Book?) {
        if (book == null) {
            clearReadRecordObserve()
            return
        }
        val key = "${book.name}|||${book.author}"
        if (observingReadRecordKey == key && readRecordObserveJob?.isActive == true) return
        observingReadRecordKey = key
        readRecordObserveJob?.cancel()
        readRecordObserveJob = viewModelScope.launch {
            combine(
                readRecordRepository.getBookReadTime(book.name, book.author),
                readRecordRepository.getBookTimelineDays(book.name, book.author),
            ) { totalTime, timelineDays ->
                totalTime to timelineDays
            }.collectLatest { (totalTime, timelineDays) ->
                currentReadRecordTotalTime = totalTime
                currentReadRecordTimelineDays = timelineDays
                screenState.update {
                    it.copy(
                        readRecordTotalTime = currentReadRecordTotalTime,
                        readRecordTimelineDays = currentReadRecordTimelineDays,
                    )
                }
            }
        }
    }

    private fun clearReadRecordObserve() {
        readRecordObserveJob?.cancel()
        readRecordObserveJob = null
        observingReadRecordKey = null
        currentReadRecordTotalTime = 0L
        currentReadRecordTimelineDays = emptyList()
    }

    private fun dismissSheet() {
        setSheet(BookInfoSheet.None)
    }

    private fun setSheet(sheet: BookInfoSheet) {
        screenState.update { it.copy(sheet = sheet) }
    }

    private fun dismissDialog() {
        showDialog(null)
    }

    private fun showDialog(dialog: BookInfoDialog?) {
        screenState.update { it.copy(dialog = dialog) }
    }

    private fun setBusy(isBusy: Boolean) {
        screenState.update { it.copy(isBusy = isBusy) }
    }

    private fun syncUiState(isTocLoading: Boolean = screenState.value.isTocLoading) {
        screenState.update {
            it.copy(
                book = currentBook?.toBookInfoBookUi(),
                hasChapters = currentChapterList.isNotEmpty(),
                tocLoadFailed = tocLoadFailed,
                highlightedTags = currentHighlightedTags,
                kindLabels = currentKindLabels,
                groupNames = currentGroupNames,
                hasCustomGroup = currentHasCustomGroup,
                readRecordTotalTime = currentReadRecordTotalTime,
                readRecordTimelineDays = currentReadRecordTimelineDays,
                inBookshelf = inBookshelf,
                isTocLoading = isTocLoading,
            )
        }
    }

    private fun showMessage(resId: Int) = showMessage(context.getString(resId))

    private fun showMessage(message: String) {
        emitEffect(BookInfoEffect.ShowMessage(message))
    }

    private fun emitEffect(effect: BookInfoEffect) {
        _effects.tryEmit(effect)
    }

    private fun Book.toBookInfoBookUi(): BookInfoBookUi = BookInfoBookUi(
        bookUrl = bookUrl,
        name = name,
        author = author,
        realAuthor = getRealAuthor(),
        coverPath = getDisplayCover(),
        isLocal = isLocal,
        durChapterTitle = durChapterTitle,
        latestChapterTitle = latestChapterTitle,
        totalChapterNum = totalChapterNum,
        durChapterIndex = durChapterIndex,
        durChapterPos = durChapterPos,
        remark = remark,
        intro = getDisplayIntro(),
    )

    private fun Book.uiCopy(): Book = copy().also { snapshot ->
        snapshot.infoHtml = infoHtml
        snapshot.tocHtml = tocHtml
    }
}

/**
 * 把三类设置（各自的 SSOT）叠加到屏幕状态上，得到完整的 UI 状态。
 * 纯函数：设置字段只来自参数，与屏幕状态如何重置无关。
 */
internal fun BookInfoUiState.withSettings(
    theme: ThemeSettings,
    cover: CoverSettings,
): BookInfoUiState = copy(
    bookInfoFollowCoverColor = theme.bookInfoFollowCoverColor,
    bookInfoNetworkCoverBackground = theme.bookInfoNetworkCoverBackground,
    bookInfoDefaultCoverBackground = theme.bookInfoDefaultCoverBackground,
    loadCoverOnlyOnWifi = cover.loadOnlyOnWifi,
    defaultCover = cover.defaultCover,
    defaultCoverDark = cover.defaultCoverDark,
)
