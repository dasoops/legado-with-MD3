package io.legado.app.feature.localdirectory

import androidx.compose.runtime.Stable
import io.legado.app.ui.main.bookshelf.BookUiItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class DirectoryBook(
    val ui: BookUiItem,
    /** 相对目录组的目录层级 (不含文件名) */
    val dir: ImmutableList<String>,
) {
    val book get() = ui.book
}

@Stable
sealed interface LocalDirectoryNode {
    @Stable
    data class Folder(val name: String) : LocalDirectoryNode

    @Stable
    data class Book(val item: DirectoryBook) : LocalDirectoryNode
}

@Stable
data class LocalDirectoryUiState(
    val path: ImmutableList<String> = persistentListOf(),
    /** 面包屑: 根目录名 + path */
    val pathNames: ImmutableList<String> = persistentListOf(),
    val nodes: ImmutableList<LocalDirectoryNode> = persistentListOf(),
    val isLoading: Boolean = false,
    val isUnavailable: Boolean = false,
)

sealed interface LocalDirectoryIntent {
    data object Initialize : LocalDirectoryIntent
    data object Refresh : LocalDirectoryIntent
    data class EnterFolder(val name: String) : LocalDirectoryIntent
    data class NavigateToLevel(val index: Int) : LocalDirectoryIntent
    data object NavigateBack : LocalDirectoryIntent
    data class SearchChange(val key: String, val isSearch: Boolean) : LocalDirectoryIntent
}

sealed interface LocalDirectoryEffect {
    data class ShowToast(val message: String) : LocalDirectoryEffect
}
