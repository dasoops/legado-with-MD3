package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.repository.BookRepository
import io.legado.app.domain.model.ReadingProgress
import io.legado.app.feature.reader.platform.ReaderPerfTrace
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.LocalBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import kotlin.coroutines.coroutineContext

/**
 * 开书 / 目录加载 / 进度同步域（R2.2 续批）。
 *
 * 从导航请求解析出书，装载目录与正文，处理本地文件缺失，
 * 以及与云端阅读进度的双向同步。
 *
 * **无自持状态**：唯一的状态是 `isInitFinish`（Compose 阅读路由用它表达开书初始化完成），
 * 必须留在 [ReadBookUiState]。
 * 故与 [ReadConfigUpdateDelegate] 同形，读写经 [Host]。
 */
class ReadBookLoadDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: Host,
    private val bookRepository: BookRepository,
) {

    interface Host {
        /** 刚 initData 完的一次性标记：此时不再弹进度冲突确认。 */
        var justInitData: Boolean

        fun setInitFinish()

        fun emitEffect(effect: ReadBookEffect)

        /** 云端进度比本地新，弹确认框。 */
        fun sureNewProgress(progress: BookProgress)

        /** 本地书文件读不到，去要书籍目录权限。 */
        fun requestBooksDirPicker(reloadChapterList: Boolean)

        /** 把 ReadPreferences 快照刷成最新，开书流程依赖它。 */
        suspend fun syncReadPreferencesSnapshot()

        fun openChapter(index: Int, durChapterPos: Int)

        suspend fun checkReadRecordAlias(book: Book)
    }

    suspend fun initReadBookConfig(request: ReadBookInitRequest): Book? = withContext(Dispatchers.IO) {
        val bookUrl = request.bookUrl
        val book = when {
            bookUrl.isNullOrEmpty() -> bookRepository.getLastReadBook()
            else -> bookRepository.getBook(bookUrl)
        } ?: return@withContext null
        ReadBook.upReadBookConfig(book)
        book
    }

    fun initData(
        request: ReadBookInitRequest,
        initialBook: Book? = null,
        success: (() -> Unit)? = null,
    ) {
        Coroutine.async(scope, Dispatchers.IO) {
            ReaderPerfTrace.suspendSection("open.init") {
                host.syncReadPreferencesSnapshot()
                ReadBook.inBookshelf = request.inBookshelf
                ReadBook.chapterChanged = request.chapterChanged
                val bookUrl = request.bookUrl
                val book = initialBook ?: when {
                    bookUrl.isNullOrEmpty() -> bookRepository.getLastReadBook()
                    else -> bookRepository.getBook(bookUrl)
                } ?: ReadBook.book
                when {
                    book != null -> initBook(book)
                    else -> {
                        ReadBook.upMsg(context.getString(R.string.no_book))
                        AppLog.put("未找到书籍\nbookUrl:$bookUrl")
                    }
                }
                val index = request.chapterIndex
                val chapterPos = request.chapterPos
                if (index >= 0 && chapterPos >= 0) {
                    ReadBook.saveCurrentBookProgress()
                    host.openChapter(index, chapterPos)
                }
            }
        }.onSuccess {
            success?.invoke()
        }.onError {
            val msg = "初始化数据失败\n${it.localizedMessage}"
            ReadBook.upMsg(msg)
            AppLog.put(msg, it)
        }.onFinally {
            ReadBook.saveRead()
        }
    }

    /** 换书/重装目录后重走开书流程。VM 的「模拟阅读切换」和目录权限回来后也调它。 */
    suspend fun initBook(book: Book) = ReaderPerfTrace.suspendSection("open.book") {
        val isSameBook = ReadBook.book?.bookUrl == book.bookUrl
        if (isSameBook) {
            ReadBook.upData(book)
        } else {
            ReadBook.resetData(book)
        }
        host.setInitFinish()
        if (book.isLocal && !checkLocalBookFileExist(book)) {
            return@suspendSection
        }
        if ((ReadBook.chapterSize == 0 || book.isLocalModified()) && !loadChapterListAwait(book)) {
            return@suspendSection
        }
        ReadBook.upMsg(null)
        host.checkReadRecordAlias(book)

        if (!isSameBook) {
            ReadBook.loadInitialContent(resetPageOffset = true)
        } else {
            ReadBook.loadOrUpContent()
        }
        if (ReadBook.chapterChanged) {
            ReadBook.chapterChanged = false
        }
    }

    private fun checkLocalBookFileExist(book: Book): Boolean {
        try {
            LocalBook.getBookInputStream(book)
            return true
        } catch (e: Throwable) {
            ReadBook.upMsg("打开本地书籍出错: ${e.localizedMessage}")
            if (e is SecurityException || e is FileNotFoundException) {
                host.requestBooksDirPicker(reloadChapterList = false)
            }
            return false
        }
    }

    /** 重新拉目录。会话侧 `loadChapterList` 回调和 TOC 正则改动都走这里。 */
    fun doLoadChapterList(book: Book) {
        Coroutine.async(scope, Dispatchers.IO) {
            if (loadChapterListAwait(book)) {
                ReadBook.upMsg(null)
            }
        }
    }

    private suspend fun loadChapterListAwait(book: Book): Boolean {
        kotlin.runCatching {
            LocalBook.getChapterList(book).let {
                bookRepository.replaceChaptersAndUpdateBook(book, it)
                ReadBook.onChapterListUpdated(book)
            }
            return true
        }.onFailure {
            when (it) {
                is SecurityException, is FileNotFoundException -> {
                    host.requestBooksDirPicker(reloadChapterList = true)
                }
                else -> {
                    AppLog.put("LoadTocError:${it.localizedMessage}", it)
                    ReadBook.upMsg("LoadTocError:${it.localizedMessage}")
                }
            }
            return false
        }
        return true
    }

    private fun Book.toReadingProgress() = ReadingProgress(
        name = name,
        author = author,
        durChapterIndex = durChapterIndex,
        durChapterPos = durChapterPos,
        durChapterTime = durChapterTime,
        durChapterTitle = durChapterTitle
    )

    private fun ReadingProgress.toBookProgress() = BookProgress(
        name = name,
        author = author,
        durChapterIndex = durChapterIndex,
        durChapterPos = durChapterPos,
        durChapterTime = durChapterTime,
        durChapterTitle = durChapterTitle
    )

}
