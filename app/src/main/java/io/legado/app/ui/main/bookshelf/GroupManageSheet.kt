package io.legado.app.ui.main.bookshelf

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.book.group.GroupDeleteAction
import io.legado.app.ui.book.group.GroupEditContent
import io.legado.app.ui.book.group.GroupResetCoverAction
import io.legado.app.ui.book.group.GroupViewModel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.move
import io.legado.app.utils.takePersistablePermissionSafely
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupManageSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    viewModel: GroupViewModel = koinViewModel(),
    bookshelfViewModel: BookshelfViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val defaultLocalDirectoryName = stringResource(R.string.local_directory)
    val allGroups by bookshelfViewModel.allGroupsFlow.collectAsStateWithLifecycle()

    var editingGroup by remember { mutableStateOf<BookGroup?>(null) }
    var isEditing by remember { mutableStateOf(false) }
    var isCreatingTag by remember { mutableStateOf(false) }
    var coverPath by remember(editingGroup) { mutableStateOf(editingGroup?.cover) }

    var listData by remember { mutableStateOf(allGroups) }
    var hasPendingOrder by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 全部/本地目录/标签三类分组的显示与排序统一持久化; 标签分组首次调整时在此落库.
    val persistGroups: (Map<Long, Boolean>) -> Unit = { showOverrides ->
        val updated = listData.mapIndexed { index, group ->
            group.copy(order = index, show = showOverrides[group.groupId] ?: group.show)
        }
        listData = updated
        viewModel.upGroup(*updated.toTypedArray())
    }

    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        listData = listData.toMutableList().apply {
            move(from.index, to.index)
        }
        hasPendingOrder = true
    }

    LaunchedEffect(allGroups) {
        if (!reorderableState.isAnyItemDragging) {
            listData = allGroups
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && hasPendingOrder) {
            hasPendingOrder = false
            persistGroups(emptyMap())
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = if (!isEditing) stringResource(R.string.group_manage) else stringResource(R.string.group_edit),
        startAction = editingGroup?.takeIf {
            isEditing && (it.groupId > 0 || it.groupId == Long.MIN_VALUE)
        }?.let { group ->
            {
                GroupDeleteAction(
                    group = group,
                    onDismissRequest = {
                        editingGroup = null
                        isEditing = false
                    },
                    viewModel = viewModel
                )
            }
        },
        endAction = {
            if (!isEditing) {
                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    val directoryPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocumentTree()
                    ) { uri ->
                        if (uri != null) {
                            uri.takePersistablePermissionSafely(context)
                            // 部分 provider 不返回显示名, 退化到 tree document id 的末段
                            val pickedName = DocumentFile.fromTreeUri(context, uri)
                                ?.name
                                ?.takeIf { it.isNotBlank() }
                                ?: DocumentsContract.getTreeDocumentId(uri)
                                    .substringAfterLast('/')
                                    .substringAfter(':')
                                    .takeIf { it.isNotBlank() }
                                ?: defaultLocalDirectoryName
                            viewModel.addGroup(
                                groupName = pickedName,
                                bookSort = -1,
                                enableRefresh = false,
                                isPrivate = false,
                                cover = null,
                                pattern = null,
                                localDirectoryUri = uri.toString(),
                                onSuccess = { showMenu = false }
                            )
                        }
                    }
                    MediumTonalButton(
                        onClick = { showMenu = true },
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add),
                    )
                    RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.add_local_directory_group),
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = {
                                showMenu = false
                                directoryPicker.launch(null)
                            }
                        )
                        RoundDropdownMenuItem(
                            text = "新增标签分组",
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = {
                                showMenu = false
                                editingGroup = null
                                isCreatingTag = true
                                isEditing = true
                            }
                        )

                    }
                }
            } else {
                GroupResetCoverAction(
                    group = editingGroup,
                    onCoverPathChange = { coverPath = it },
                    viewModel = viewModel
                )
            }
        }
    ) {
        AnimatedContent(
            targetState = isEditing,
            transitionSpec = {
                fadeIn() togetherWith fadeOut() using SizeTransform(clip = false)
            },
            label = "GroupManageState"
        ) { editing ->
            if (editing) {
                GroupEditContent(
                    group = editingGroup,
                    isTag = isCreatingTag,
                    onDismissRequest = {
                        editingGroup = null
                        isEditing = false
                    },
                    coverPath = coverPath,
                    onCoverPathChange = { coverPath = it },
                    viewModel = viewModel
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listData, key = { it.groupId }) { group ->
                        val manageNameInfo = remember(group) { group.getManageName(context) }
                        ReorderableSelectionItem(
                            state = reorderableState,
                            key = group.groupId,
                            reorderIndex = listData.indexOf(group),
                            reorderItemCount = listData.size,
                            onMoveItem = { from, to ->
                                listData = listData.toMutableList().apply { move(from, to) }
                                persistGroups(emptyMap())
                            },
                            title = group.groupName.ifBlank { manageNameInfo.suffix.orEmpty() },
                            subtitle = manageNameInfo.suffix?.takeIf { it != group.groupName },
                            isEnabled = group.show,
                            containerColor = LegadoTheme.colorScheme.onSheetContent,
                            onEnabledChange = { isChecked ->
                                persistGroups(mapOf(group.groupId to isChecked))
                            },
                            onClickEdit = if (group.isLocalDirectory || group.groupId > 0) {
                                {
                                    editingGroup = group
                                    coverPath = group.cover
                                    isEditing = true
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }

    }
}
