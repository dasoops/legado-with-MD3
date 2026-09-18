package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ACCENT = 0xff22aa44.toInt()
private const val TEXT_COLOR = 0xff123456.toInt()

class ReaderPageTest {
    @Test fun hitTestingUsesImmutableElementBounds() {
        val text = ReaderElement.Text(
            bounds = ReaderRect(10f, 20f, 30f, 40f),
            baselinePx = 35f,
            value = "阅",
            style = ReaderTextStyle(colorArgb = 0xff000000.toInt(), fontSizePx = 20f),
            selected = false,
            emphasized = false,
            chapterPosition = 12,
        )
        val page = ReaderPage(
            ReaderPageId(1, 2), "chapter", "text", 100, 200, 20f, 180f, listOf(text), 1,
        )
        assertEquals(text, page.elementAt(20f, 30f))
        assertNull(page.elementAt(5f, 30f))
    }

    @Test fun htmlLinkUsesAccentColorAndUnderlineWithLegacyPriority() {
        val link = text(link = "https://example")

        assertEquals(ACCENT, link.resolvedColorArgb(ACCENT))
        assertTrue(link.drawsLinkUnderline)
    }

    @Test fun ordinaryTextKeepsItsConfiguredColor() {
        val ordinary = text()

        assertEquals(TEXT_COLOR, ordinary.resolvedColorArgb(ACCENT))
        assertFalse(ordinary.drawsLinkUnderline)
    }

    @Test fun searchResultUsesAccentWithoutPretendingToBeAnHtmlLink() {
        val result = text(searchResult = true)

        assertEquals(ACCENT, result.resolvedColorArgb(ACCENT))
        assertFalse(result.drawsLinkUnderline)
    }

    private fun text(
        link: String? = null,
        searchResult: Boolean = false,
    ) = ReaderElement.Text(
        bounds = ReaderRect(0f, 0f, 10f, 20f),
        baselinePx = 15f,
        value = "字",
        style = ReaderTextStyle(colorArgb = TEXT_COLOR, fontSizePx = 20f),
        selected = false,
        emphasized = false,
        searchResult = searchResult,
        link = link,
        chapterPosition = 0,
    )
}
