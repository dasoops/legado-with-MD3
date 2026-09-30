package io.legado.app.ui.book.info

import android.net.Uri
import androidx.compose.runtime.Stable
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay

@Stable
data class HighlightedTag(
    val matchedLabels: List<String>,
    val title: String?,
)

data class BookInfoUiState(
    val book: BookInfoBookUi? = null,
    val hasChapters: Boolean = false,
    val tocLoadFailed: Boolean = false,
    val highlightedTags: List<HighlightedTag> = emptyList(),
    val kindLabels: List<String> = emptyList(),
    val groupNames: String? = null,
    val hasCustomGroup: Boolean = false,
    val readRecordTotalTime: Long = 0L,
    val readRecordTimelineDays: List<ReadRecordTimelineDay> = emptyList(),
    val inBookshelf: Boolean = false,
    val isTocLoading: Boolean = false,
    val isBusy: Boolean = false,
    val sheet: BookInfoSheet = BookInfoSheet.None,
    val dialog: BookInfoDialog? = null,
    val bookInfoFollowCoverColor: Boolean = true,
    val bookInfoNetworkCoverBackground: String = "on",
    val bookInfoDefaultCoverBackground: String = "on",
    val loadCoverOnlyOnWifi: Boolean = false,
    val defaultCover: String = "",
    val defaultCoverDark: String = "",
)

@Stable
data class BookInfoBookUi(
    val bookUrl: String,
    val name: String,
    val author: String,
    val realAuthor: String,
    val coverPath: String?,
    val isLocal: Boolean,
    val durChapterTitle: String?,
    val latestChapterTitle: String?,
    val totalChapterNum: Int,
    val durChapterIndex: Int,
    val durChapterPos: Int,
    val remark: String?,
    val intro: String?,
)

sealed interface BookInfoSheet {
    data object None : BookInfoSheet
    data object GroupPicker : BookInfoSheet
    data object ReadRecord : BookInfoSheet
}

sealed interface BookInfoDialog {
    data class EditRemark(val remark: String?) : BookInfoDialog
    data class PhotoPreview(val path: String) : BookInfoDialog
}

sealed interface BookInfoIntent {
    data object DismissSheet : BookInfoIntent
    data object DismissDialog : BookInfoIntent
    data class MenuAction(val action: BookInfoMenuAction) : BookInfoIntent
    data object ReadClick : BookInfoIntent
    data object OpenLocalBookExternally : BookInfoIntent
    data object TocClick : BookInfoIntent
    data object CoverPreviewClick : BookInfoIntent
    data object GroupClick : BookInfoIntent
    data object ReadRecordClick : BookInfoIntent
    data object RemarkClick : BookInfoIntent
    data class SaveCover(val path: String) : BookInfoIntent
    data class UpdateRemark(val remark: String) : BookInfoIntent
    data class AddTags(val tags: Set<String>) : BookInfoIntent

    /** 简介 HTML 图片长按。 */
    data class IntroImageLongClick(val source: String) : BookInfoIntent
}

sealed interface BookInfoEffect {
    data class ShowMessage(val message: String) : BookInfoEffect

    data class Finish(
        val resultCode: Int? = null,
        val afterTransition: Boolean = false,
    ) : BookInfoEffect

    data class OpenBookInfoEdit(val bookUrl: String) : BookInfoEffect
    data class OpenToc(val bookUrl: String) : BookInfoEffect
    data class OpenReader(
        val book: Book,
        val inBookshelf: Boolean,
        val chapterChanged: Boolean,
    ) : BookInfoEffect
    data class OpenLocalBookExternally(val uri: Uri) : BookInfoEffect
    data object ClearCache : BookInfoEffect
}

enum class BookInfoMenuAction {
    Edit,
    Refresh,
    ReadRecord,
    Top,
    ClearCache,
}
