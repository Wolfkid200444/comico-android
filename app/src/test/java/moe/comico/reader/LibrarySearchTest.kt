package moe.comico.reader

import org.junit.Assert.*
import org.junit.Test

class LibrarySearchTest {
    private val saved = listOf(Manga("one","One Piece","","ongoing","manga",emptyList()),Manga("two","Blue Lock","","ongoing","manga",emptyList()))
    @Test fun filtersOnlySavedTitlesAndIgnoresCaseAndOuterWhitespace() {
        assertEquals(listOf(saved[0]),filterLibrary(saved," PIECE "))
        assertTrue(filterLibrary(saved,"Unsaved title").isEmpty())
        assertEquals(saved,filterLibrary(saved,"   "))
        assertEquals(2,saved.size)
    }
}
