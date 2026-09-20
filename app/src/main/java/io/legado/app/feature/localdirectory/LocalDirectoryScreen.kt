package io.legado.app.feature.localdirectory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.ui.main.bookCoverSharedElementKey
import io.legado.app.ui.main.bookshelf.BookItem
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.ui.main.bookshelf.BookshelfGridItem
import io.legado.app.ui.main.bookshelf.BookshelfListItem
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.ImmutableList
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LocalDirectoryRouteScreen(
    groupId: Long,
    rootUri: String,
    settings: BookshelfSettings,
    customTagColors: ImmutableList<TagColorPair>,
    searchKey: String,
    isSearch: Boolean,
    contentPadding: PaddingValues,
    onOpenBook: (BookShelfItem, String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalDirectoryViewModel = koinViewModel(
        key = "localDir:$groupId",
        parameters = { parametersOf(groupId, rootUri) },
    ),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.onIntent(LocalDirectoryIntent.Initialize)
        viewModel.effects.collect { effect ->
            when (effect) {
                is LocalDirectoryEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }
    LaunchedEffect(searchKey, isSearch) {
        viewModel.onIntent(LocalDirectoryIntent.SearchChange(searchKey, isSearch))
    }
    BackHandler(enabled = state.path.isNotEmpty()) {
        viewModel.onIntent(LocalDirectoryIntent.NavigateBack)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
    ) {
        DirectoryBreadcrumb(
            pathNames = state.pathNames,
            onNavigateToLevel = { viewModel.onIntent(LocalDirectoryIntent.NavigateToLevel(it)) },
            onRefresh = { viewModel.onIntent(LocalDirectoryIntent.Refresh) },
        )
        DirectoryContent(
            state = state,
            settings = settings,
            customTagColors = customTagColors,
            groupId = groupId,
            onIntent = viewModel::onIntent,
            onOpenBook = onOpenBook,
        )
    }
}

@Composable
private fun DirectoryBreadcrumb(
    pathNames: ImmutableList<String>,
    onNavigateToLevel: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassCard(
            modifier = Modifier.weight(1f),
            containerColor = LegadoTheme.colorScheme.surfaceContainer
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(pathNames) { index, name ->
                    val isLast = index == pathNames.lastIndex
                    AppText(
                        text = name,
                        style = LegadoTheme.typography.labelSmall,
                        fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isLast) {
                            LegadoTheme.colorScheme.primary
                        } else {
                            LegadoTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .then(
                                if (!isLast) {
                                    Modifier.clickable { onNavigateToLevel(index) }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    if (!isLast) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = LegadoTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
        SmallTonalButton(
            onClick = onRefresh,
            icon = Icons.Default.Refresh,
            contentDescription = stringResource(R.string.refresh),
        )
    }
}

@Composable
private fun DirectoryContent(
    state: LocalDirectoryUiState,
    settings: BookshelfSettings,
    customTagColors: ImmutableList<TagColorPair>,
    groupId: Long,
    onIntent: (LocalDirectoryIntent) -> Unit,
    onOpenBook: (BookShelfItem, String?) -> Unit,
) {
    if (state.isUnavailable) {
        EmptyMessage(
            modifier = Modifier.fillMaxSize(),
            message = stringResource(R.string.directory_unavailable),
        )
        return
    }
    if (state.isLoading && state.nodes.isEmpty()) {
        AppCircularProgressIndicator(
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    if (state.nodes.isEmpty()) {
        EmptyMessage(
            modifier = Modifier.fillMaxSize(),
            message = stringResource(R.string.empty),
        )
        return
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val layoutMode = if (isLandscape) {
        settings.bookshelfLayoutModeLandscape
    } else {
        settings.bookshelfLayoutModePortrait
    }
    val layoutGrid = if (isLandscape) {
        settings.bookshelfLayoutGridLandscape
    } else {
        settings.bookshelfLayoutGridPortrait
    }
    val layoutList = if (isLandscape) {
        settings.bookshelfLayoutListLandscape
    } else {
        settings.bookshelfLayoutListPortrait
    }
    val columns = if (layoutMode == 0) layoutList else layoutGrid
    val isGridMode = layoutMode != 0
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isGridMode) 8.dp else 0.dp),
    ) {
        items(
            items = state.nodes,
            key = { node ->
                when (node) {
                    is LocalDirectoryNode.Folder -> "f:${node.name}"
                    is LocalDirectoryNode.Book -> "b:${node.item.book.bookUrl}"
                }
            }
        ) { node ->
            when (node) {
                is LocalDirectoryNode.Folder -> DirectoryFolderItem(
                    name = node.name,
                    settings = settings,
                    isGridMode = isGridMode,
                    onClick = { onIntent(LocalDirectoryIntent.EnterFolder(node.name)) },
                )

                is LocalDirectoryNode.Book -> {
                    val bookUi = node.item.ui
                    BookItem(
                        settings = settings,
                        customTagColors = customTagColors,
                        bookUi = bookUi,
                        layoutMode = layoutMode,
                        gridStyle = settings.bookshelfGridLayout,
                        isCompact = settings.bookshelfLayoutCompact,
                        titleSmallFont = settings.bookshelfTitleSmallFont,
                        titleCenter = settings.bookshelfTitleCenter,
                        titleMaxLines = settings.bookshelfTitleMaxLines,
                        coverShadow = settings.bookshelfCoverShadow,
                        onClick = {
                            onOpenBook(
                                bookUi.book,
                                bookCoverSharedElementKey(bookUi.book.bookUrl, "localdir:$groupId")
                            )
                        },
                        onLongClick = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectoryFolderItem(
    name: String,
    settings: BookshelfSettings,
    isGridMode: Boolean,
    onClick: () -> Unit,
) {
    val cover: @Composable (Modifier) -> Unit = { coverModifier ->
        Box(
            modifier = coverModifier.background(LegadoTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = LegadoTheme.colorScheme.primary,
            )
        }
    }
    if (isGridMode) {
        BookshelfGridItem(
            cover = cover,
            title = name,
            gridStyle = settings.bookshelfGridLayout,
            titleSmallFont = settings.bookshelfTitleSmallFont,
            titleCenter = settings.bookshelfTitleCenter,
            titleMaxLines = settings.bookshelfTitleMaxLines,
            coverShadow = settings.bookshelfCoverShadow,
            coverWidth = settings.bookshelfGridCoverWidth,
            onClick = onClick,
            onLongClick = null,
        )
    } else {
        BookshelfListItem(
            settings = settings,
            isCompact = settings.bookshelfLayoutCompact,
            cover = cover,
            title = name,
            coverWidth = settings.bookshelfListCoverWidth,
            coverShadow = settings.bookshelfCoverShadow,
            onClick = onClick,
            onLongClick = null,
        )
    }
}
