package io.legado.app.feature.localdirectory

import androidx.compose.runtime.Stable
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.LocalBookProgress
import io.legado.app.domain.model.LocalDirectoryEntry
import io.legado.app.ui.widget.components.list.ListUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Stable
data class LocalDirectoryItem(
    val entry: LocalDirectoryEntry,
    val progress: LocalBookProgress?,
)

@Stable
data class LocalDirectoryUiState(
    val rootUri: String = "",
    val pathNames: ImmutableList<String> = persistentListOf(),
    override val items: ImmutableList<LocalDirectoryItem> = persistentListOf(),
    // 目录视图不提供多选, 恒空以满足 ListUiState 契约
    override val selectedIds: ImmutableSet<Any> = persistentSetOf(),
    override val searchKey: String = "",
    override val isSearch: Boolean = false,
    override val isLoading: Boolean = false,
    val sort: Int = 0,
    val isUnavailable: Boolean = false,
) : ListUiState<LocalDirectoryItem>

sealed interface LocalDirectoryIntent {
    data object Initialize : LocalDirectoryIntent
    data object Refresh : LocalDirectoryIntent
    data object NavigateBack : LocalDirectoryIntent
    data class NavigateToLevel(val index: Int) : LocalDirectoryIntent
    data class EnterDir(val item: LocalDirectoryItem) : LocalDirectoryIntent
    data class ItemClick(val item: LocalDirectoryItem) : LocalDirectoryIntent
    data class SearchToggle(val enabled: Boolean) : LocalDirectoryIntent
    data class SearchQueryChange(val query: String) : LocalDirectoryIntent
    data class SortChange(val sort: Int) : LocalDirectoryIntent
}

sealed interface LocalDirectoryEffect {
    data class OpenBook(val book: Book) : LocalDirectoryEffect
    data class ShowToast(val message: String) : LocalDirectoryEffect
}
