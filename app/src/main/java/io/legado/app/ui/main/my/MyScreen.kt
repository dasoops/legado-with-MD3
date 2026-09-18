package io.legado.app.ui.main.my

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyRouteScreen(
    onOpenSettings: () -> Unit,
    onNavigate: (PrefClickEvent) -> Unit
) {
    MyScreen(
        onOpenSettings = onOpenSettings,
        onNavigate = onNavigate,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyScreen(
    onOpenSettings: () -> Unit,
    onNavigate: (PrefClickEvent) -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.my),
                actions = {},
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    adaptiveContentPadding(
                        top = padding.calculateTopPadding(),
                        bottom = 120.dp
                    )
                )
        ) {
            SplicedColumnGroup(
                title = stringResource(R.string.rule_segment),
            ) {
                ClickableSettingItem(
                    title = stringResource(R.string.book_source_manage),
                    description = stringResource(R.string.book_source_manage_desc),
                    imageVector = Icons.Default.Source,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.OpenBookSourceManage
                        )
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.replace_purify),
                    imageVector = Icons.Default.FindReplace,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(ReplaceRuleActivity::class.java)
                        )
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.txt_toc_rule),
                    imageVector = Icons.AutoMirrored.Filled.Rule,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(TxtTocRuleActivity::class.java)
                        )
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.highlight_tag_config),
                    imageVector = Icons.Default.Sell,
                    onClick = { onNavigate(PrefClickEvent.OpenHighlightTagRule) }
                )
            }

            SplicedColumnGroup(
                title = stringResource(R.string.other)
            ) {
                ClickableSettingItem(
                    title = stringResource(R.string.setting),
                    imageVector = Icons.Default.Settings,
                    onClick = {
                        onOpenSettings()
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.bookmark),
                    imageVector = Icons.Default.Bookmark,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(AllBookmarkActivity::class.java))
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.read_record),
                    imageVector = Icons.Default.History,
                    onClick = {
                        onNavigate(PrefClickEvent.OpenReadRecord)
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.cache_management),
                    imageVector = Icons.Default.Download,
                    onClick = {
                        onNavigate(PrefClickEvent.OpenBookCacheManage)
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.file_manage),
                    imageVector = Icons.Default.Folder,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(FileManageActivity::class.java))
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.about),
                    imageVector = Icons.Default.Info,
                    onClick = {
                        onNavigate(PrefClickEvent.OpenAbout)
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.exit),
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    onClick = {
                        onNavigate(PrefClickEvent.ExitApp)
                    }
                )
            }
        }
    }
}
