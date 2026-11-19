package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderHiddenImagesTest {
    private val shaper =
        ReaderTextShaper { text ->
            GlyphClusters(text.map(Char::toString), List(text.length) { 10f })
        }
    private val style =
        ReaderChapterMeasureStyle(
            ReaderTextStyle(0, 10f),
            ReaderTextStyle(0, 10f),
            2,
            ReaderTextAlignment.START,
            ReaderTextAlignment.START,
            imageLayoutMode = ReaderImageLayoutMode.NONE,
        )
    private val config = ReaderPaginationConfig(0, "", 120, 240, 10f, 20f, 10f, 20f, 10f, 8f)

    private suspend fun measure(vararg paragraphs: String) = ReaderChapterBlockMeasurer(
        shaper,
        shaper,
        { ReaderImageDimensions(100f, 50f) },
    ).measure(ReaderChapterSourceParser.parse(0, "", paragraphs.toList(), false, false), style)
        as ReaderChapterMeasureResult.Success

    @Test fun hiddenImagesLeaveNoStandaloneOrInlinePlaceholders() = runBlocking {
        val measured = measure("　　<img src=\"a\">甲", "<img src=\"b\">", "乙<img src=\"c\">")
        val images =
            measured.blocks.flatMap { block ->
                when (block) {
                    is ReaderMeasuredBlock.Image -> listOf(block)
                    is ReaderMeasuredBlock.InlineParagraph ->
                        block.items.filterIsInstance<ReaderMeasuredInlineItem.Image>()
                    else -> emptyList()
                }
            }
        assertTrue(images.isEmpty())

        assertEquals(2, measured.blocks.size)
        val pages = ReaderPaginator.paginateBlocks(measured.blocks, config)
        assertEquals(1, pages.size)
        assertTrue(pages.flatMap { it.elements }.none { it is ReaderElement.Image })
        assertEquals("　　甲\n乙", pages.single().text)
    }
}
