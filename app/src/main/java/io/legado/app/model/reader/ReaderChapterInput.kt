package io.legado.app.model.reader

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.help.book.BookContent

/** Processed chapter input published before any View-era page layout is consumed. */
data class ReaderChapterInput(
    val book: Book,
    val chapter: BookChapter,
    val displayTitle: String,
    val content: BookContent,
    val source: ReaderChapterSource,
    /** Precomputed off the main thread; used to deduplicate Compose pagination requests. */
    val contentHash: Int,
    val contentProcessesHash: Int,
    val sourceHash: Int,
    val pageEstimateGeneration: Long,
)

data class ReaderChapterInputWindow(
    val previous: ReaderChapterInput? = null,
    val current: ReaderChapterInput? = null,
    val next: ReaderChapterInput? = null,
)
