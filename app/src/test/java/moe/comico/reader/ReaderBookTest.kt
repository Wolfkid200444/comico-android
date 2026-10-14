package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReaderBookTest {
    @Test fun hingePanesKeepContentAwayFromPhysicalGap() {
        val panes = readerBookPanes(800f, 390f, 410f)!!
        assertEquals(BookPanes(386f, 28f, 386f), panes)
        assertEquals(800f, panes.left + panes.gap + panes.right, 0f)
        assertEquals(BookPanes(396f, 8f, 396f), readerBookPanes(800f, 400f, 400f))
        assertNull(readerBookPanes(400f, 200f, 200f))
        assertNull(readerBookPanes(800f, 50f, 60f))
        assertNull(readerBookPanes(800f, -10f, 10f))
        assertNull(readerBookPanes(800f, 400f, 390f))
    }

    @Test fun bookViewHonorsToggleAndLeavesLongStripsAlone() {
        val panes = readerBookPanes(800f, 400f, 400f)
        assertTrue(useFoldableBook(true, ReadingMode.SINGLE, panes))
        assertFalse(useFoldableBook(false, ReadingMode.SINGLE, panes))
        assertFalse(useFoldableBook(true, ReadingMode.STRIP, panes))
        assertFalse(useFoldableBook(true, ReadingMode.SINGLE, null))
        assertEquals(listOf(0, 1), bookSpreadPages(3, 0, ReadingDirection.LTR))
        assertEquals(listOf(1, 0), bookSpreadPages(3, 0, ReadingDirection.RTL))
        assertEquals(listOf(2, null), bookSpreadPages(3, 2, ReadingDirection.LTR))
        assertEquals(listOf(null, 2), bookSpreadPages(3, 2, ReadingDirection.RTL))
    }

    @Test fun bookPreferencePersistsAndCanBeOverriddenPerManga() {
        val off = ReaderPreferences(foldableBook = false)
        assertEquals(off, JSONObject(off.toJson().toString()).readerPreferences())
        val override = ReaderOverride(foldableBook = true)
        assertEquals(override, JSONObject(override.toJson().toString()).readerOverride())
        assertTrue(override.resolve(off).foldableBook)
        assertFalse(ReaderOverride().resolve(off).foldableBook)
        assertFalse(ReaderOverride(foldableBook = false).isEmpty())
        assertTrue(JSONObject("{}").readerPreferences().foldableBook)
        assertNull(JSONObject("{}").readerOverride().foldableBook)
    }
}
