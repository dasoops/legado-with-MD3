package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 锁定 [ConfigUpdate.actions] 与旧 View `EventBus.UP_CONFIG` 事件码的对应关系。
 *
 * 旧 View 证据：`git show 4c088448c^:app/src/main/java/io/legado/app/ui/book/read/ReadBookActivity.kt`
 * 的 `observeLiveBus()` 翻译表，以及各弹窗（FontConfigDialog / TipConfigDialog /
 * UnderlineConfigDialog / InfoConfigDialog / PaddingConfigDialog / MoreConfigDialog /
 * BgTextConfigDialog / ReadStyleDialog / ShadowSetDialog / ReadConfigViewModel）里的
 * `postEvent(EventBus.UP_CONFIG, arrayListOf(...))`。
 *
 * 事件码含义：0=UpdateSystemUi 1=UpdateBackground 2=UpdateStyle 3=UpdateBackgroundAlpha
 * 4=UpdatePageSlopSquare 5=ReloadContent 6=UpdateContent 8=UpdateChapterStyle
 * 9=InvalidateTextPage 10=UpdateLayout 11=SubmitRenderTask。
 *
 * 允许的 Compose 追加项（旧 View 无对应码）：
 * `RebuildWholeBookPageIndex`（整书页码估算）、`UpdateWholeBookPageDemand`（{FullPageIndex} 类
 * 页眉页脚）、`RefreshInlineImages`（样式方案/预设切换）、`UpdateSystemUi`（样式方案切换后刷新
 * 状态栏图标）。
 */
class LegacyConfigUpdateActionsTest {

    private val updateStyle = ConfigUpdateAction.UpdateStyle
    private val updateContent = ConfigUpdateAction.UpdateContent
    private val updateBackgroundAlpha = ConfigUpdateAction.UpdateBackgroundAlpha
    private val updateBackground = ConfigUpdateAction.UpdateBackground
    private val updateSystemUi = ConfigUpdateAction.UpdateSystemUi
    private val reloadContent = ConfigUpdateAction.ReloadContent
    private val updateChapterStyle = ConfigUpdateAction.UpdateChapterStyle
    private val invalidateTextPage = ConfigUpdateAction.InvalidateTextPage
    private val updateLayout = ConfigUpdateAction.UpdateLayout
    private val submitRenderTask = ConfigUpdateAction.SubmitRenderTask
    private val updatePageAnim = ConfigUpdateAction.UpdatePageAnim
    private val rebuildWholeBookPageIndex = ConfigUpdateAction.RebuildWholeBookPageIndex
    private val updateWholeBookPageDemand = ConfigUpdateAction.UpdateWholeBookPageDemand
    private val refreshInlineImages = ConfigUpdateAction.RefreshInlineImages

    private data class Case(
        val legacy: String,
        val update: ConfigUpdate,
        val expected: Set<ConfigUpdateAction>,
    )

    private val cases = listOf(
        // ── 正文样式 ──
        Case("FontConfigDialog 缩进 8,5", ConfigUpdate.ParagraphIndent("　"), setOf(updateChapterStyle, reloadContent)),
        Case("FontConfigDialog 字重 8,9,6", ConfigUpdate.TextBold(600), setOf(updateChapterStyle, invalidateTextPage, updateContent)),
        Case("FontConfigDialog 字距 8,5", ConfigUpdate.LetterSpacing(0.1f), setOf(updateChapterStyle, reloadContent)),
        Case("FontConfigDialog 行距 8,5", ConfigUpdate.LineSpacing(3), setOf(updateChapterStyle, reloadContent)),
        Case("FontConfigDialog 段距 8,5", ConfigUpdate.ParagraphSpacing(2), setOf(updateChapterStyle, reloadContent)),
        Case("FontConfigDialog 斜体 8,5", ConfigUpdate.TextItalic(true), setOf(updateChapterStyle, reloadContent)),
        Case("ReadStyleDialog 字号 8,5", ConfigUpdate.TextSize(22), setOf(updateChapterStyle, reloadContent)),
        Case("TEXT_COLOR 2,6,9,11", ConfigUpdate.TextColor(0x112233), setOf(updateStyle, updateContent, invalidateTextPage, submitRenderTask)),
        Case(
            "TEXT_ACCENT_COLOR 2,6,9,11",
            ConfigUpdate.TextAccentColor(0x112233),
            setOf(updateStyle, updateContent, invalidateTextPage, submitRenderTask),
        ),

        // ── 阴影 ──
        Case("FontConfigDialog 阴影开关 8,5", ConfigUpdate.TextShadow(true), setOf(updateChapterStyle, reloadContent)),
        Case("ShadowSetDialog 阴影半径 8,5", ConfigUpdate.ShadowRadius(1f), setOf(updateChapterStyle, reloadContent)),
        Case("ShadowSetDialog 阴影 dx 8,5", ConfigUpdate.ShadowDx(1f), setOf(updateChapterStyle, reloadContent)),
        Case("ShadowSetDialog 阴影 dy 8,5", ConfigUpdate.ShadowDy(1f), setOf(updateChapterStyle, reloadContent)),
        Case("S_COLOR 2,6,9,11", ConfigUpdate.ShadowColor(0x112233), setOf(updateStyle, updateContent, invalidateTextPage, submitRenderTask)),

        // ── 标题 ──
        Case("TipConfigDialog 标题字体 8,5", ConfigUpdate.TitleFont(""), setOf(updateChapterStyle, reloadContent)),
        Case("TipConfigDialog 标题字重 8,9,6", ConfigUpdate.TitleBold(600), setOf(updateChapterStyle, invalidateTextPage, updateContent)),
        Case("TipConfigDialog 标题模式 5(+RB)", ConfigUpdate.TitleMode(1), setOf(rebuildWholeBookPageIndex, reloadContent)),
        Case("TipConfigDialog 分段类型 5(+RB)", ConfigUpdate.TitleSegType(1), setOf(rebuildWholeBookPageIndex, reloadContent)),
        Case("TipConfigDialog 分段距离 5(+RB)", ConfigUpdate.TitleSegDistance(1), setOf(rebuildWholeBookPageIndex, reloadContent)),
        Case("TipConfigDialog 分段标志 5(+RB)", ConfigUpdate.TitleSegFlag("　"), setOf(rebuildWholeBookPageIndex, reloadContent)),
        Case("TipConfigDialog 标题缩放 8,5", ConfigUpdate.TitleSegScaling(1f), setOf(updateChapterStyle, reloadContent)),
        Case(
            "TipConfigDialog 标题行距(外) 8,5",
            ConfigUpdate.TitleLineSpacingExtra(1),
            setOf(updateChapterStyle, reloadContent),
        ),
        Case(
            "TipConfigDialog 标题行距(内) 8,5",
            ConfigUpdate.TitleLineSpacingSub(1),
            setOf(updateChapterStyle, reloadContent),
        ),
        Case("TipConfigDialog 标题字号 8,5", ConfigUpdate.TitleSize(24), setOf(updateChapterStyle, reloadContent)),
        Case("TipConfigDialog 标题上距 8,5", ConfigUpdate.TitleTopSpacing(1), setOf(updateChapterStyle, reloadContent)),
        Case("TipConfigDialog 标题下距 8,5", ConfigUpdate.TitleBottomSpacing(1), setOf(updateChapterStyle, reloadContent)),
        Case("TITLE_COLOR 8,5", ConfigUpdate.TitleColor(0x112233), setOf(updateChapterStyle, reloadContent)),
        Case("标题夜间色 同 TITLE_COLOR", ConfigUpdate.TitleColorNight(0x112233), setOf(updateChapterStyle, reloadContent)),

        // ── 页眉页脚 ──
        Case("InfoConfigDialog 页眉项 2,6(+DM)", ConfigUpdate.TipHeaderLeft(1), setOf(updateStyle, updateContent, updateWholeBookPageDemand)),
        Case("InfoConfigDialog 页脚项 2,6(+DM)", ConfigUpdate.TipFooterRight(1), setOf(updateStyle, updateContent, updateWholeBookPageDemand)),
        Case(
            "InfoConfigDialog 自定义页眉项 2,6(+DM)",
            ConfigUpdate.CustomTipHeaderLeft("{x}"),
            setOf(updateStyle, updateContent, updateWholeBookPageDemand),
        ),
        Case("InfoConfigDialog 页眉模式 2", ConfigUpdate.HeaderMode(1), setOf(updateStyle)),
        Case("InfoConfigDialog 页脚模式 2", ConfigUpdate.FooterMode(1), setOf(updateStyle)),
        Case("InfoConfigDialog 页眉字体 2", ConfigUpdate.HeaderFont(""), setOf(updateStyle)),
        Case("InfoConfigDialog 页眉字号 2", ConfigUpdate.HeaderFontSize(12), setOf(updateStyle)),
        Case("TIP_HEADER_COLOR 2", ConfigUpdate.TipHeaderColor(0x112233), setOf(updateStyle)),
        Case("TIP_FOOTER_COLOR 2", ConfigUpdate.TipFooterColor(0x112233), setOf(updateStyle)),
        Case("TIP_DIVIDER_COLOR 2", ConfigUpdate.TipDividerColor(0x112233), setOf(updateStyle)),

        // ── 下划线 ──
        Case(
            "UnderlineConfigDialog 下划线 6,9,11",
            ConfigUpdate.Underline(true),
            setOf(updateContent, invalidateTextPage, submitRenderTask),
        ),
        Case(
            "UnderlineConfigDialog 点线 6,9,11",
            ConfigUpdate.DottedLine(true),
            setOf(updateContent, invalidateTextPage, submitRenderTask),
        ),
        Case(
            "UnderlineConfigDialog 下划线延长 6,9,11",
            ConfigUpdate.UnderlineExtend(true),
            setOf(updateContent, invalidateTextPage, submitRenderTask),
        ),
        Case(
            "UnderlineConfigDialog 下划线高度 8,9,6",
            ConfigUpdate.UnderlineHeight(2),
            setOf(updateChapterStyle, invalidateTextPage, updateContent),
        ),
        Case(
            "UnderlineConfigDialog 下划线间距 8,9,6",
            ConfigUpdate.UnderlinePadding(2),
            setOf(updateChapterStyle, invalidateTextPage, updateContent),
        ),
        Case(
            "BgTextConfigDialog 点线基准 6,8,10",
            ConfigUpdate.DottedBase(0.1f),
            setOf(updateContent, updateChapterStyle, updateLayout),
        ),
        Case(
            "BgTextConfigDialog 点线比例 6,9,11",
            ConfigUpdate.DottedRatio(0.1f),
            setOf(updateContent, invalidateTextPage, submitRenderTask),
        ),
        Case("U_COLOR 2 + 6,9,11", ConfigUpdate.UnderlineColor(0x112233), setOf(updateStyle, updateContent, invalidateTextPage, submitRenderTask)),

        // ── 内边距 ──
        Case("PaddingConfigDialog 上内边距 10,5", ConfigUpdate.PaddingTop(1), setOf(updateLayout, reloadContent)),
        Case("PaddingConfigDialog 下内边距 10,5", ConfigUpdate.PaddingBottom(1), setOf(updateLayout, reloadContent)),
        Case("PaddingConfigDialog 左内边距 10,5", ConfigUpdate.PaddingLeft(1), setOf(updateLayout, reloadContent)),
        Case("PaddingConfigDialog 右内边距 10,5", ConfigUpdate.PaddingRight(1), setOf(updateLayout, reloadContent)),
        Case("PaddingConfigDialog 页眉上内边距 2", ConfigUpdate.HeaderPaddingTop(1), setOf(updateStyle)),
        Case("PaddingConfigDialog 页脚右内边距 2", ConfigUpdate.FooterPaddingRight(1), setOf(updateStyle)),
        Case("PaddingConfigDialog 显示页眉分隔线 2", ConfigUpdate.ShowHeaderLine(true), setOf(updateStyle)),
        Case("PaddingConfigDialog 显示页脚分隔线 2", ConfigUpdate.ShowFooterLine(true), setOf(updateStyle)),

        // ── 背景与系统 UI ──
        Case("BgTextConfigDialog 背景色/图 1", ConfigUpdate.BgStr("#112233"), setOf(updateBackground)),
        Case("BgTextConfigDialog 背景类型 1", ConfigUpdate.BgType(1), setOf(updateBackground)),
        Case("BgTextConfigDialog 背景透明度 3", ConfigUpdate.BgAlpha(80), setOf(updateBackgroundAlpha)),
        Case(
            "BgTextConfigDialog 深色状态栏图标 0",
            ConfigUpdate.StatusIconDark(true),
            setOf(updateSystemUi),
        ),
        Case(
            "MoreConfigDialog 隐藏状态栏 0,2",
            ConfigUpdate.HideStatusBar(true),
            setOf(updateSystemUi, updateStyle),
        ),
        Case(
            "MoreConfigDialog 隐藏导航栏 0,2",
            ConfigUpdate.HideNavigationBar(true),
            setOf(updateSystemUi, updateStyle),
        ),
        Case(
            "MoreConfigDialog 刘海屏内边距 2",
            ConfigUpdate.PaddingDisplayCutouts(true),
            setOf(updateStyle),
        ),

        // ── 排版布局 ──
        Case(
            "ReadStyleDialog 样式方案 1,2,5(+RII,RB,USysUI)",
            ConfigUpdate.StyleSelect(1),
            setOf(updateBackground, updateStyle, refreshInlineImages, rebuildWholeBookPageIndex, reloadContent, updateSystemUi),
        ),
        Case(
            "ReadStyleDialog 分享排版 1,2,5(+RB)",
            ConfigUpdate.ShareLayout(true),
            setOf(updateBackground, updateStyle, rebuildWholeBookPageIndex, reloadContent),
        ),
        Case(
            "ReadStyleDialog 翻页动画 upPageAnim+5(+RB)",
            ConfigUpdate.PageAnim(2),
            setOf(updatePageAnim, rebuildWholeBookPageIndex, reloadContent),
        ),
        Case("MoreConfigDialog 两端对齐 5(+RB)", ConfigUpdate.TextFullJustify(true), setOf(rebuildWholeBookPageIndex, reloadContent)),
        Case(
            "MoreConfigDialog 底部对齐 5(+RB)",
            ConfigUpdate.TextBottomJustify(true),
            setOf(rebuildWholeBookPageIndex, reloadContent),
        ),
        Case("MoreConfigDialog 中文排版 5(+RB)", ConfigUpdate.UseZhLayout(true), setOf(rebuildWholeBookPageIndex, reloadContent)),
        Case(
            "MoreConfigDialog 特殊样式适配 5(+RB)",
            ConfigUpdate.AdaptSpecialStyle(true),
            setOf(rebuildWholeBookPageIndex, reloadContent),
        ),
        Case("MoreConfigDialog 全局下划线 5", ConfigUpdate.UseUnderlineGlobal(true), setOf(reloadContent)),
        Case(
            "MoreConfigDialog 横屏双页 10,5",
            ConfigUpdate.DoubleHorizontalPage("1"),
            setOf(updateLayout, reloadContent),
        ),
        Case("MoreConfigDialog 优化渲染 8,5", ConfigUpdate.OptimizeRender(true), setOf(updateChapterStyle, reloadContent)),
        Case(
            "ReadStyleDialog 简繁转换 5(+RB)",
            ConfigUpdate.ChineseConverterType(1),
            setOf(rebuildWholeBookPageIndex, reloadContent),
        ),
        Case(
            "MoreConfigDialog 正文延伸至刘海 recreate(+RB)",
            ConfigUpdate.ReadBodyToLh(true),
            setOf(rebuildWholeBookPageIndex, reloadContent),
        ),
    )

    @Test
    fun `actions mirror the legacy View UP_CONFIG event codes`() {
        cases.forEach { case ->
            assertEquals(
                "${case.update.javaClass.simpleName}（旧 View ${case.legacy}）",
                case.expected,
                case.update.actions,
            )
        }
    }

    @Test
    fun `single color edits never re-download inline images`() {
        cases.asSequence()
            .filter { it.legacy.endsWith("COLOR 2,6,9,11") || it.legacy.startsWith("TITLE_COLOR") }
            .forEach { case ->
                assertFalse(
                    case.update.javaClass.simpleName,
                    refreshInlineImages in case.update.actions,
                )
            }
    }

    /**
     * 菜单颜色/边框是 Compose 时代新增项（旧 View 无对应事件码），只被阅读菜单读取。
     * 除 `MenuBgColor` 需要刷新状态栏图标外，不得驱动任何正文副作用——尤其不能带
     * `ReloadContent`（改个菜单颜色就重载正文）。
     */
    @Test
    fun `menu appearance updates never reload or repaint the reading content`() {
        listOf(
            ConfigUpdate.MenuBgColor(0x112233),
            ConfigUpdate.MenuBgColorNight(0x112233),
            ConfigUpdate.MenuAccentColor(0x112233),
            ConfigUpdate.MenuAccentColorNight(0x112233),
            ConfigUpdate.MenuContainerColor(0x112233),
            ConfigUpdate.MenuContainerColorNight(0x112233),
            ConfigUpdate.BorderWidth(1),
            ConfigUpdate.BorderColor(0x112233),
            ConfigUpdate.BorderColorNight(0x112233),
        ).forEach { update ->
            assertEquals(
                "${update.javaClass.simpleName} 最多只允许 UpdateSystemUi",
                emptySet<ConfigUpdateAction>(),
                update.actions - updateSystemUi,
            )
        }
    }
}
