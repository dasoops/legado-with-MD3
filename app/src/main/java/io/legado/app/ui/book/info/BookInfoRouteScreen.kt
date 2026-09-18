package io.legado.app.ui.book.info

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.openFileUri
import io.legado.app.utils.sendToClip
import io.legado.app.utils.takePersistablePermissionSafely
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import splitties.init.appCtx

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BookInfoRouteScreen(
    bookUrl: String,
    name: String? = null,
    author: String? = null,
    origin: String? = null,
    coverPath: String? = null,
    viewModel: BookInfoViewModel,
    onBack: () -> Unit,
    onFinish: (resultCode: Int?, afterTransition: Boolean) -> Unit,
    onOpenReader: (bookUrl: String, inBookshelf: Boolean, chapterChanged: Boolean) -> Unit = { _, _, _ -> },
    onNavigateToBookInfo: (name: String?, author: String?, bookUrl: String, origin: String?, coverPath: String?) -> Unit = { _, _, _, _, _ -> },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSelectBooksDirSheet by remember { mutableStateOf(false) }

    val tocActivityResult = rememberLauncherForActivityResult(TocActivityResult()) {
        viewModel.onTocResult(it)
    }
    val localBookTreeSelect =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            if (RealPathUtil.getTreePath(uri)?.startsWith(appCtx.externalFiles.parent!!) == true) {
                return@rememberLauncherForActivityResult
            }
            if (uri.isContentScheme()) {
                uri.takePersistablePermissionSafely(activity)
            }
            viewModel.onIntent(BookInfoIntent.SetDefaultBookTreeUri(uri.toString()))
        }
    val infoEditResult = rememberLauncherForActivityResult(
        StartActivityContract(BookInfoEditActivity::class.java)
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            viewModel.onInfoEdited()
        }
    }

    LaunchedEffect(bookUrl, name, author, origin, coverPath, viewModel) {
        viewModel.initData(
            bookUrl = bookUrl,
            name = name,
            author = author,
            origin = origin,
            coverPath = coverPath
        )
    }


    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshShelfState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(viewModel, activity) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is BookInfoEffect.ShowMessage -> context.toastOnUi(effect.message)
                is BookInfoEffect.Finish -> {
                    onFinish(effect.resultCode, effect.afterTransition)
                }

                is BookInfoEffect.OpenBookInfoEdit -> {
                    infoEditResult.launch {
                        putExtra("bookUrl", effect.bookUrl)
                    }
                }

                is BookInfoEffect.OpenReader -> {
                    onOpenReader(
                        effect.book.bookUrl,
                        effect.inBookshelf,
                        effect.chapterChanged,
                    )
                }

                is BookInfoEffect.OpenToc -> tocActivityResult.launch(effect.bookUrl)

                BookInfoEffect.OpenSelectBooksDir -> showSelectBooksDirSheet = true

                is BookInfoEffect.OpenFile -> activity.openFileUri(effect.uri, effect.mimeType)
                is BookInfoEffect.RunSourceCallback -> {
                    runSourceCallback(activity, effect, viewModel)
                }

                is BookInfoEffect.NavigateToBookInfo -> {
                    onNavigateToBookInfo(effect.name, effect.author, effect.bookUrl, effect.origin, effect.coverPath)
                }
            }
        }
    }

    FilePickerSheet(
        show = showSelectBooksDirSheet,
        onDismissRequest = { showSelectBooksDirSheet = false },
        title = stringResource(R.string.select_book_folder),
        onSelectSysDir = {
            showSelectBooksDirSheet = false
            localBookTreeSelect.launch(null)
        },
    )
    BookInfoScreen(
        state = uiState,
        groups = viewModel.allGroups
            .collectAsStateWithLifecycle(persistentListOf<BookGroup>()).value,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedCoverKey = sharedCoverKey,
    )
}

private fun runSourceCallback(
    activity: AppCompatActivity,
    effect: BookInfoEffect.RunSourceCallback,
    viewModel: BookInfoViewModel,
) {
    when (val action = effect.action) {
        is BookInfoCallbackAction.ShareText -> {
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, action.text)
                type = "text/plain"
            }
            activity.startActivity(Intent.createChooser(intent, action.chooserTitle))
        }

        is BookInfoCallbackAction.CopyText -> {
            activity.sendToClip(action.text)
        }

        BookInfoCallbackAction.ClearCache -> {
            viewModel.clearCache()
        }

        BookInfoCallbackAction.None -> Unit
    }
}
