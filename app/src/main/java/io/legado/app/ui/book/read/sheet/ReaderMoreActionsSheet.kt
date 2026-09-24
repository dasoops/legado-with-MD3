package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleanHands
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.MoreActionIds
import io.legado.app.ui.book.read.ReadBookButtonConfigItem
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookMenuRoute
import io.legado.app.ui.book.read.ReadBookSheet
import io.legado.app.ui.book.read.ReadBookUiState
import io.legado.app.ui.widget.components.ConfigListEntry
import io.legado.app.ui.widget.components.ReorderableConfigList
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.reader.ReaderMenuActionSquare

private data class MoreActionSpec(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val applicable: Boolean = true,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun ReaderMoreActionsSheet(
    show: Boolean,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var editing by remember(show) { mutableStateOf(false) }
    fun dispatch(intent: ReadBookIntent) {
        onDismissRequest()
        onIntent(intent)
    }

    val specs = moreActionSpecs(state, onIntent, ::dispatch)
    val config = state.menuConfig.moreActionItems.ifEmpty {
        MoreActionIds.map { ReadBookButtonConfigItem(it, true) }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        contentPaddingEnabled = false,
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            ReaderBookHeader(book = state.book)
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (editing) {
            MoreActionsConfigContent(
                items = config,
                specs = specs,
                onCancel = { editing = false },
                onSave = {
                    onIntent(ReadBookIntent.SaveMoreActionsConfig(it))
                    editing = false
                },
            )
        } else {
            MoreActionsPager(
                actions = orderedVisibleActions(config, specs) + MoreActionSpec(
                    id = "configure_more_actions",
                    label = stringResource(R.string.more_actions_config),
                    icon = Icons.Default.Settings,
                    onClick = { editing = true },
                ),
                state = state,
                onIntent = onIntent,
                dispatch = ::dispatch,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun orderedVisibleActions(
    config: List<ReadBookButtonConfigItem>,
    specs: List<MoreActionSpec>,
): List<MoreActionSpec> {
    val specMap = specs.associateBy { it.id }
    return config.mapNotNull { item ->
        specMap[item.id]?.takeIf { item.enabled && it.applicable }
    }
}

@Composable
private fun MoreActionsPager(
    actions: List<MoreActionSpec>,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    dispatch: (ReadBookIntent) -> Unit,
) {
    val pages = actions.chunked(8).ifEmpty { listOf(emptyList()) }
    val pagerState = rememberPagerState { pages.size }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val itemSize = (maxWidth - 56.dp) / 4
        val rowCount = if (actions.size > 4) 2 else 1
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(
                    itemSize * rowCount + if (rowCount == 2) 24.dp else 16.dp
                ),
        ) { page ->
            val pageItems = pages[page]
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pageItems.chunked(4).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { action ->
                            ActionSquareHost(
                                action = action,
                                state = state,
                                onIntent = onIntent,
                                dispatch = dispatch,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionSquareHost(
    action: MoreActionSpec,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    dispatch: (ReadBookIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(action.id) { mutableStateOf(false) }
    val hasMore = false
    Box(modifier = modifier) {
        ReaderMenuActionSquare(
            icon = action.icon,
            text = action.label,
            selected = action.selected,
            hasMore = hasMore,
            onClick = if (action.id == "image_style") {
                { expanded = true }
            } else {
                action.onClick
            },
            onMoreClick = { expanded = true },
        )
        when (action.id) {
            "image_style" -> ImageStyleDropdown(
                expanded = expanded,
                currentStyle = state.book?.getImageStyle() ?: Book.imgStyleDefault,
                onDismissRequest = { expanded = false },
                onIntent = onIntent,
            )

        }
    }
}

@Composable
private fun MoreActionsConfigContent(
    items: List<ReadBookButtonConfigItem>,
    specs: List<MoreActionSpec>,
    onCancel: () -> Unit,
    onSave: (List<ReadBookButtonConfigItem>) -> Unit,
) {
    val meta = specs.associateBy { it.id }
    val entries = items.mapNotNull { item ->
        meta[item.id]?.let { spec ->
            ConfigListEntry(item.id, item.enabled, spec.icon, spec.label)
        }
    }

    ReorderableConfigList(
        initialEntries = entries,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        onDismiss = onCancel,
        onConfirm = { confirmed ->
            onSave(confirmed.map { ReadBookButtonConfigItem(it.id, it.enabled) })
        },
    )
}

@Composable
private fun moreActionSpecs(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    dispatch: (ReadBookIntent) -> Unit,
): List<MoreActionSpec> = listOf(
    MoreActionSpec(
        "toc_rule", stringResource(R.string.txt_toc_rule), Icons.AutoMirrored.Filled.Toc,
        applicable = state.isLocalTxt, onClick = { dispatch(ReadBookIntent.MenuTocRegex) }),
    MoreActionSpec(
        "charset", stringResource(R.string.set_charset), Icons.Default.Translate,
        applicable = state.isLocalBook,
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset)) }),
    MoreActionSpec(
        "add_bookmark", stringResource(R.string.bookmark_add), Icons.Default.Bookmark,
        onClick = { dispatch(ReadBookIntent.AddBookmark) }),
    MoreActionSpec(
        "read_style", stringResource(R.string.read_config), Icons.Default.DisplaySettings,
        onClick = { dispatch(ReadBookIntent.OpenReadMenuRoute(ReadBookMenuRoute.ReadStyle)) }),
    MoreActionSpec(
        "re_segment", stringResource(R.string.re_segment), Icons.AutoMirrored.Filled.Toc,
        selected = state.reSegment, onClick = { onIntent(ReadBookIntent.MenuReSegment) }),
    MoreActionSpec(
        "del_ruby", stringResource(R.string.del_ruby_tag), Icons.Default.CleanHands,
        applicable = state.isEpub, selected = state.delRubyTag,
        onClick = { onIntent(ReadBookIntent.MenuDelRubyTag) }),
    MoreActionSpec(
        "image_style", stringResource(R.string.image_style), Icons.Default.Image,
        onClick = {}),
    MoreActionSpec(
        "get_progress", stringResource(R.string.get_book_progress), Icons.Default.Sync,
        applicable = state.isReadingProgressSyncConfigured,
        onClick = { dispatch(ReadBookIntent.MenuGetProgress) }),
    MoreActionSpec(
        "cover_progress", stringResource(R.string.cover_book_progress), Icons.Default.Sync,
        applicable = state.isReadingProgressSyncConfigured,
        onClick = { dispatch(ReadBookIntent.MenuCoverProgress) }),
    MoreActionSpec(
        "bottom_button_config", stringResource(R.string.config_btn), Icons.Default.Settings,
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ToolButtonConfig)) }),
    MoreActionSpec(
        "log", stringResource(R.string.log), Icons.Default.BugReport,
        onClick = { onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.AppLog)) }),
)

@Composable
private fun ImageStyleDropdown(
    expanded: Boolean,
    currentStyle: String,
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    RoundDropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) { dismiss ->
        listOf(
            R.string.btn_default_s to Book.imgStyleDefault,
            R.string.image_style_full to Book.imgStyleFull,
            R.string.image_style_text to Book.imgStyleText,
            R.string.image_style_single to Book.imgStyleSingle,
        ).forEach { (label, style) ->
            RoundDropdownMenuItem(
                text = stringResource(label),
                isSelected = currentStyle == style,
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuImageStyle(style)) },
            )
        }
    }
}
