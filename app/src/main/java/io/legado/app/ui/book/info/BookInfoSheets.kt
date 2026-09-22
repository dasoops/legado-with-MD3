package io.legado.app.ui.book.info

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.flowOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.book.changecover.ChangeCoverViewModel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppLinearProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import org.koin.androidx.compose.koinViewModel

@Composable
fun WebFileSheet(
    show: Boolean,
    files: List<BookInfoWebFile>,
    title: String,
    onDismissRequest: () -> Unit,
    onSelect: (BookInfoWebFile) -> Unit,
) {
    AppModalBottomSheet(show = show, onDismissRequest = onDismissRequest, title = title) {
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp), contentAlignment = Alignment.Center
            ) {
                Text(text = stringResource(R.string.empty))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(files, key = { it.name }) { file ->
                    GlassCard(onClick = { onSelect(file) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (file.name.endsWith("zip") || file.name.endsWith("rar") || file.name.endsWith("7z")) Icons.Outlined.FolderZip else Icons.Outlined.Image, null)
                            Text(text = file.name, modifier = Modifier.weight(1f), style = LegadoTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChangeCoverSheet(
    show: Boolean,
    name: String,
    author: String,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
    viewModel: ChangeCoverViewModel = koinViewModel(key = "cover-$name-$author"),
) {
    val items by remember(show) {
        // dataFlow 若无条件 collectAsStateWithLifecycle 收集会带来副作用：
        // ChangeCoverSheet 在编辑页（BookInfoEditContent）里是 show=false 也照样组合的，
        // callbackFlow 收集体立即执行：查库后若结果 ≤ 1 条就 startSearch() → 用全部启用
        // 书源并发搜书（每源执行搜索规则/登录检测脚本），这就是“每次点开编辑页都弹某书源
        // 未登录提示”的根源。
        // 这里只有用户真正打开换封面面板（show=true）才开始收集/自动搜索；
        // 隐藏时收集一个空的已完成 Flow，不触发任何网络与脚本。
        if (show) viewModel.dataFlow else flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList<SearchBook>())
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    LaunchedEffect(name, author) {
        viewModel.initData(name, author)
    }
    DisposableEffect(show) {
        onDispose {
            viewModel.stopSearch()
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.change_cover_source),
        endAction = {
            MediumTonalButton(
                onClick = { viewModel.startOrStopSearch() },
                icon = if (isSearching) Icons.Default.MoreVert else Icons.Default.Refresh,
                contentDescription = stringResource(
                    if (isSearching) R.string.more_menu else R.string.refresh
                )
            )
        }
    ) {
        if (isSearching) {
            AppLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.bookUrl + it.originName }) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            onSelect(item.coverUrl.orEmpty())
                        }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CoilBookCover(
                        name = item.name,
                        author = item.author,
                        path = item.coverUrl,
                        sourceOrigin = item.origin,
                        modifier = Modifier
                            .width(112.dp)
                            .aspectRatio(5f / 7f),
                    )
                    AppText(
                        text = item.originName,
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        maxLines = 2
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
