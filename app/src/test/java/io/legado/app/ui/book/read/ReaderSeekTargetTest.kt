package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSeekTargetTest {
    @Test
    fun snapsToLastPageWhenDragStopsNearTrackEnd() {
        assertEquals(74, snapPageSeekTarget(71.3f, 74))
        assertEquals(74, snapPageSeekTarget(71f, 74))
        assertEquals(74, snapPageSeekTarget(74f, 74))
    }

    @Test
    fun keepsIntermediatePageWhenDragIsClearlyInside() {
        assertEquals(70, snapPageSeekTarget(70f, 74))
        assertEquals(15, snapPageSeekTarget(15f, 17))
    }

    @Test
    fun singlePageChapterAlwaysTargetsFirstPage() {
        assertEquals(0, snapPageSeekTarget(0f, 0))
        assertEquals(0, snapPageSeekTarget(1f, 0))
    }
}
