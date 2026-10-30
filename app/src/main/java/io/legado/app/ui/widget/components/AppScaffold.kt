package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.legado.app.domain.model.settings.hasBackgroundImage
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHazeState
import io.legado.app.ui.theme.LocalTopBarBackdrop
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.theme.responsiveHazeSource
import top.yukonga.miuix.kmp.basic.FabPosition as MiuixFabPosition
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable (HazeState) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    contentColor: Color = contentColorFor(MiuixTheme.colorScheme.surface),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    alwaysDrawBehindBars: Boolean = false,
    /**
     * 本页不参与模糊 / 液态玻璃：不录制内容图层，也按未开启模糊的样式渲染顶栏。
     *
     * haze 的 `hazeSource` 与液态玻璃的 `layerBackdrop` 都会把整棵内容录进 GraphicsLayer，
     * 内容每帧变化就要重录一次。书架这类长列表滚动时每帧多花约 7ms（60Hz 下直接超预算），
     * 因此这类页面整体退化为无模糊样式；采样源与模糊效果必须一起关，否则顶栏只剩半透明色。
     */
    disableContentSampling: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val isDark = LegadoTheme.isDark
    val configuration = LocalAppUiConfiguration.current
    val themeSettings = configuration.theme
    val hasImageBg = themeSettings.hasBackgroundImage(isDark)
    val hazeState = remember { HazeState() }
    val liquidGlassEnabled = configuration.theme.topBarButtonStyle == "liquid"
    val composeEngine = LegadoTheme.composeEngine
    val contentDrawsBehindBars =
        alwaysDrawBehindBars || themeSettings.enableBlur || themeSettings.enableProgressiveBlur

    val containerColor =
        if (hasImageBg) {
            Color.Transparent
        } else {
            LegadoTheme.colorScheme.background
        }

    val miuixContainerColor =
        if (hasImageBg) {
            Color.Transparent
        } else {
            MiuixTheme.colorScheme.surface
        }
    val topBarBackdropBaseColor = LegadoTheme.colorScheme.background
    val topBarBackgroundBackdrop =
        rememberLayerBackdrop {
            drawRect(topBarBackdropBaseColor)
            drawContent()
        }
    val topBarContentBackdrop = rememberLayerBackdrop { drawContent() }
    val topBarBackdrop =
        rememberCombinedBackdrop(
            topBarBackgroundBackdrop,
            topBarContentBackdrop,
        )

    val blurEnabled = themeSettings.enableBlur && !disableContentSampling
    val effectiveConfiguration =
        remember(configuration, disableContentSampling) {
            if (configuration.theme.enableBlur && disableContentSampling) {
                configuration.copy(theme = configuration.theme.copy(enableBlur = false))
            } else {
                configuration
            }
        }

    CompositionLocalProvider(
        LocalAppUiConfiguration provides effectiveConfiguration,
        LocalHazeState provides if (blurEnabled) hazeState else null,
        LocalTopBarBackdrop provides if (liquidGlassEnabled && !disableContentSampling) topBarBackdrop else null,
    ) {
        when {
            ThemeResolver.isMiuixEngine(composeEngine) -> {
                val miuixFabPosition =
                    when (floatingActionButtonPosition) {
                        FabPosition.End -> MiuixFabPosition.End
                        FabPosition.Center -> MiuixFabPosition.Center
                        else -> MiuixFabPosition.End
                    }
                Box(modifier = modifier.fillMaxSize()) {
                    Box(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (liquidGlassEnabled) {
                                    Modifier.layerBackdrop(topBarBackgroundBackdrop)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        BackgroundImageContent(isDark = isDark, hazeState = hazeState)
                    }
                    MiuixScaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            topBar(hazeState)
                        },
                        bottomBar = bottomBar,
                        snackbarHost = snackbarHost,
                        floatingActionButton = floatingActionButton,
                        floatingActionButtonPosition = miuixFabPosition,
                        containerColor = miuixContainerColor,
                        contentWindowInsets = contentWindowInsets,
                    ) { paddingValues ->
                        val scaffoldPadding =
                            if (configuration.appShell.useFloatingBottomBar) {
                                PaddingValues(top = paddingValues.calculateTopPadding())
                            } else {
                                paddingValues
                            }
                        Box(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (liquidGlassEnabled && !disableContentSampling) {
                                        Modifier.layerBackdrop(topBarContentBackdrop)
                                    } else {
                                        Modifier
                                    },
                                ).then(
                                    if (!disableContentSampling) {
                                        Modifier.responsiveHazeSource(
                                            hazeState,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).then(
                                    if (contentDrawsBehindBars) {
                                        Modifier
                                    } else {
                                        Modifier
                                            .padding(scaffoldPadding)
                                            .consumeWindowInsets(scaffoldPadding)
                                    },
                                ),
                        ) {
                            content(
                                if (contentDrawsBehindBars) {
                                    scaffoldPadding
                                } else {
                                    PaddingValues(0.dp)
                                },
                            )
                        }
                    }
                }
            }
            else -> {
                Box(modifier = modifier.fillMaxSize()) {
                    Box(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (liquidGlassEnabled) {
                                    Modifier.layerBackdrop(topBarBackgroundBackdrop)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        BackgroundImageContent(isDark = isDark, hazeState = hazeState)
                    }
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            topBar(hazeState)
                        },
                        bottomBar = bottomBar,
                        snackbarHost = snackbarHost,
                        floatingActionButton = floatingActionButton,
                        floatingActionButtonPosition = floatingActionButtonPosition,
                        containerColor = containerColor,
                        contentColor = contentColor,
                        contentWindowInsets = contentWindowInsets,
                    ) { paddingValues ->
                        val scaffoldPadding =
                            if (configuration.appShell.useFloatingBottomBar) {
                                PaddingValues(top = paddingValues.calculateTopPadding())
                            } else {
                                paddingValues
                            }
                        Box(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (liquidGlassEnabled && !disableContentSampling) {
                                        Modifier.layerBackdrop(topBarContentBackdrop)
                                    } else {
                                        Modifier
                                    },
                                ).then(
                                    if (!disableContentSampling) {
                                        Modifier.responsiveHazeSource(
                                            hazeState,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).then(
                                    if (contentDrawsBehindBars) {
                                        Modifier
                                    } else {
                                        Modifier
                                            .padding(scaffoldPadding)
                                            .consumeWindowInsets(scaffoldPadding)
                                    },
                                ),
                        ) {
                            content(
                                if (contentDrawsBehindBars) {
                                    scaffoldPadding
                                } else {
                                    PaddingValues(0.dp)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundImageContent(
    isDark: Boolean,
    hazeState: HazeState,
) {
    val themeSettings = LocalAppUiConfiguration.current.theme
    val hasImageBg = themeSettings.hasBackgroundImage(isDark)
    val bgImagePath =
        if (isDark) {
            themeSettings.backgroundImageDark
        } else {
            themeSettings.backgroundImageLight
        }
    val blur =
        if (isDark) {
            themeSettings.backgroundImageDarkBlurring
        } else {
            themeSettings.backgroundImageBlurring
        }

    if (hasImageBg && !bgImagePath.isNullOrBlank()) {
        if (themeSettings.enableBlur) {
            AsyncImage(
                model = bgImagePath,
                contentDescription = null,
                imageLoader = org.koin.compose.koinInject(),
                modifier =
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
                contentScale = ContentScale.Crop,
            )
        } else {
            AsyncImage(
                model = bgImagePath,
                contentDescription = null,
                imageLoader = org.koin.compose.koinInject(),
                modifier =
                Modifier
                    .fillMaxSize()
                    .blur(blur.dp),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
