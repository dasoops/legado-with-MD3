package io.legado.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

data class ThemeOverrideState(
    val seedColor: Color,
    val colorScheme: ColorScheme,
    val isDark: Boolean = false,
)

private data class ThemeOverrideKey(
    val seedColor: Color,
    val isDark: Boolean,
    val paletteStyle: PaletteStyle,
    val colorSpec: ThemeColorSpec,
    val usePureBlack: Boolean,
    val contrastLevel: Double,
)

// dynamicColorScheme 是纯函数, 但要跑整套 HCT/CAM16 配色生成, 实测单次可达上百毫秒.
// 阅读器每次打开书籍都会按背景色重建覆盖主题, 用一个小 LRU 复用以避免转场首帧被卡住.
private const val THEME_OVERRIDE_CACHE_SIZE = 8
private val themeOverrideCache =
    object : LinkedHashMap<ThemeOverrideKey, ThemeOverrideState>(THEME_OVERRIDE_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<ThemeOverrideKey, ThemeOverrideState>?,
        ): Boolean = size > THEME_OVERRIDE_CACHE_SIZE
    }

fun buildThemeOverrideState(
    seedColor: Color,
    isDark: Boolean,
    paletteStyle: PaletteStyle,
    colorSpec: ThemeColorSpec,
    usePureBlack: Boolean,
    contrastLevel: Double = ThemeResolver.resolveContrastLevel(),
): ThemeOverrideState {
    val key = ThemeOverrideKey(seedColor, isDark, paletteStyle, colorSpec, usePureBlack, contrastLevel)
    synchronized(themeOverrideCache) {
        themeOverrideCache[key]?.let { return it }
    }
    var colorScheme =
        dynamicColorScheme(
            seedColor = seedColor,
            isDark = isDark,
            isAmoled = false,
            style = paletteStyle,
            contrastLevel = contrastLevel,
            specVersion = ThemeResolver.resolveColorSpecVersion(colorSpec),
        )

    if (isDark && usePureBlack) {
        colorScheme =
            colorScheme.copy(
                surface = Color.Black,
                background = Color.Black,
                surfaceContainerLow = Color(0xFF0A0A0A),
                surfaceContainer = Color(0xFF121212),
            )
    }

    return ThemeOverrideState(
        seedColor = seedColor,
        colorScheme = colorScheme,
        isDark = isDark,
    ).also { state ->
        synchronized(themeOverrideCache) { themeOverrideCache[key] = state }
    }
}

@Composable
fun ProvideThemeOverride(
    theme: ThemeOverrideState?,
    content: @Composable () -> Unit,
) {
    if (theme != null) {
        ProvideColorSchemeOverride(
            colorScheme = theme.colorScheme,
            seedColor = theme.seedColor,
            overrideIsDark = theme.isDark,
            content = content,
        )
    } else {
        content()
    }
}

@Composable
fun rememberThemeOverride(seedColor: Color?): ThemeOverrideState? {
    val isDark = LegadoTheme.isDark
    val paletteStyle = LegadoTheme.paletteStyle
    val themeSettings = LocalAppUiConfiguration.current.theme
    val colorSpec = ThemeResolver.resolveColorSpecFromMaterialVersion(themeSettings.materialVersion)
    val usePureBlack = themeSettings.isPureBlack
    val contrastLevel = ThemeResolver.resolveContrastLevel(themeSettings.customContrast)

    return remember(seedColor, isDark, paletteStyle, colorSpec, usePureBlack, contrastLevel) {
        seedColor?.let { color ->
            buildThemeOverrideState(
                seedColor = color,
                isDark = isDark,
                paletteStyle = paletteStyle,
                colorSpec = colorSpec,
                usePureBlack = usePureBlack,
                contrastLevel = contrastLevel,
            )
        }
    }
}
