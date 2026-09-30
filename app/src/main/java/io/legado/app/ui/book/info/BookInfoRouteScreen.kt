package io.legado.app.ui.book.info

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.openFileUri
import io.legado.app.utils.toastOnUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest

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
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedCoverKey: String? = null,
) {
    val context = LocalContext.current
    val activity = context as AppCompatActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val tocActivityResult = rememberLauncherForActivityResult(TocActivityResult()) {
        viewModel.onTocResult(it)
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
                is BookInfoEffect.OpenLocalBookExternally -> activity.openFileUri(effect.uri)
                BookInfoEffect.ClearCache -> viewModel.clearCache()
            }
        }
    }

    BookInfoScreen(
        state = uiState,
        tags = viewModel.tagNames
            .collectAsStateWithLifecycle(persistentListOf<String>()).value,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        sharedCoverKey = sharedCoverKey,
    )
}
