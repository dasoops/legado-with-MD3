package io.legado.app.feature.localdirectory

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.Book
import io.legado.app.domain.model.LocalBookProgress
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.EmptyMessage
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun LocalDirectoryRouteScreen(
    rootUri: String,
    modifier: Modifier = Modifier,
    onOpenBook: (Book) -> Unit,
    viewModel: LocalDirectoryViewModel = koinViewModel(
        key = "localDir:$rootUri",
        parameters = { parametersOf(rootUri) },
    ),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 在子目录时优先退出一级, 到根目录则不拦截, 交还宿主返回
    BackHandler(enabled = state.pathNames.size > 1) {
        viewModel.onIntent(LocalDirectoryIntent.NavigateBack)
    }

    LaunchedEffect(viewModel) {
        viewModel.onIntent(LocalDirectoryIntent.Initialize)
        viewModel.effects.collect { effect ->
            when (effect) {
                is LocalDirectoryEffect.OpenBook -> onOpenBook(effect.book)
                is LocalDirectoryEffect.ShowToast -> context.toastOnUi(effect.message)
            }
        }
    }

    LocalDirectoryScreen(
        state = state,
        modifier = modifier,
        onIntent = viewModel::onIntent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalDirectoryScreen(
    state: LocalDirectoryUiState,
    onIntent: (LocalDirectoryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    ListScaffold(
        title = state.pathNames.lastOrNull() ?: stringResource(R.string.local_book),
        state = state,
        scrollBehavior = scrollBehavior,
        onBackClick = { onIntent(LocalDirectoryIntent.NavigateBack) },
        showSearchAction = !state.isUnavailable,
        onSearchToggle = { onIntent(LocalDirectoryIntent.SearchToggle(it)) },
        onSearchQueryChange = { onIntent(LocalDirectoryIntent.SearchQueryChange(it)) },
        searchPlaceholder = stringResource(R.string.screen),
        dropDownMenuContent = { dismiss ->
            val sorts = listOf(
                R.string.sort_by_name to 0,
                R.string.sort_by_size to 1,
                R.string.sort_by_time to 2,
            )
            sorts.forEach { (textRes, sort) ->
                RoundDropdownMenuItem(
                    text = stringResource(textRes),
                    onClick = {
                        onIntent(LocalDirectoryIntent.SortChange(sort))
                        dismiss()
                    },
                    trailingIcon = {
                        if (state.sort == sort) {
                            Icon(Icons.Default.Check, null)
                        }
                    }
                )
            }
        },
        bottomContent = {
            if (!state.isUnavailable && state.pathNames.isNotEmpty()) {
                LocalPathNavigationBar(
                    pathNames = state.pathNames,
                    canGoBack = state.pathNames.size > 1,
                    onNavigateBack = { onIntent(LocalDirectoryIntent.NavigateBack) },
                    onNavigateToLevel = { onIntent(LocalDirectoryIntent.NavigateToLevel(it)) },
                )
            }
        },
    ) { paddingValues ->
        when {
            state.isUnavailable -> {
                EmptyMessage(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    message = stringResource(R.string.directory_unavailable),
                )
            }

            state.items.isEmpty() && state.isLoading -> {
                AppCircularProgressIndicator(
                    modifier = modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                )
            }

            state.items.isEmpty() -> {
                EmptyMessage(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    message = stringResource(R.string.empty),
                )
            }

            else -> {
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    items(state.items, key = { it.entry.uri }) { item ->
                        LocalDirectoryItemRow(
                            modifier = Modifier.animateItem(),
                            item = item,
                            onClick = { onIntent(LocalDirectoryIntent.ItemClick(item)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPathNavigationBar(
    pathNames: List<String>,
    canGoBack: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToLevel: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            .animateContentSize(),
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

        if (canGoBack) {
            SmallTonalButton(
                onClick = onNavigateBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back)
            )
        }
    }
}

@Composable
private fun LocalDirectoryItemRow(
    modifier: Modifier,
    item: LocalDirectoryItem,
    onClick: () -> Unit,
) {
    val entry = item.entry
    val progress = item.progress
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(
                imageVector = when {
                    entry.isDir -> Icons.Default.Folder
                    progress != null -> Icons.Outlined.Book
                    else -> Icons.Outlined.Description
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (progress != null) {
                    LegadoTheme.colorScheme.primary
                } else {
                    LegadoTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = entry.name,
                    style = LegadoTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!entry.isDir) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextCard(
                            text = entry.name.substringAfterLast('.', "").uppercase(),
                            textStyle = LegadoTheme.typography.labelSmall,
                            horizontalPadding = 4.dp,
                            verticalPadding = 2.dp,
                            cornerRadius = 4.dp,
                            icon = null,
                            backgroundColor = LegadoTheme.colorScheme.surfaceContainerHighest
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AppText(
                            text = "${ConvertUtils.formatFileSize(entry.size)} - " +
                                    AppConst.dateFormat.format(entry.lastModified),
                            style = LegadoTheme.typography.labelMedium,
                            color = LegadoTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    progress?.let { readingProgress ->
                        AppText(
                            text = buildProgressText(readingProgress),
                            style = LegadoTheme.typography.labelMedium,
                            color = LegadoTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun buildProgressText(progress: LocalBookProgress): String {
    if (progress.totalChapterNum <= 0) return ""
    val position = "${progress.durChapterIndex + 1}/${progress.totalChapterNum}"
    val title = progress.durChapterTitle
    return if (title.isNullOrBlank()) position else "$title  $position"
}
