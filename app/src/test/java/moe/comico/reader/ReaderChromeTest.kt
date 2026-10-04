package moe.comico.reader

import org.junit.Assert.*
import org.junit.Test

class ReaderChromeTest {
    private fun chapter(id: String) = Chapter(id, id, "", "en", "Group", "")
    @Test fun usesNeighborsFromReaderContextBeforePartialChapterList() {
        val current = chapter("50")
        val previous = chapter("49")
        val next = chapter("51")
        val state = AppState(reader = current, chapters = listOf(current),
            nativeReader = NativeReaderState(previous = previous, next = next))
        assertEquals(previous to next, readerAdjacentChapters(state))
    }
    @Test fun fallbackListIsNewestFirstAndHasSafeBoundaries() {
        val chapters = listOf(chapter("3"), chapter("2"), chapter("1"))
        assertEquals(chapters[2] to chapters[0], readerAdjacentChapters(AppState(reader = chapters[1], chapters = chapters)))
        assertEquals(chapters[1] to null, readerAdjacentChapters(AppState(reader = chapters[0], chapters = chapters)))
        assertEquals(null to chapters[1], readerAdjacentChapters(AppState(reader = chapters[2], chapters = chapters)))
        assertEquals(null to null, readerAdjacentChapters(AppState(reader = chapter("unknown"), chapters = chapters)))
    }
}
