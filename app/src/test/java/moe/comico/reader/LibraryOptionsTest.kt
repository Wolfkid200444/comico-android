package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LibraryOptionsTest {
    private val a = Manga("a", "Alpha", "", "ongoing", "manga", emptyList())
    private val b = Manga("b", "Beta", "", "completed", "manhwa", emptyList())
    private val c = Manga("c", "Charlie", "", "ongoing", "manga", emptyList())
    private val chapter = Chapter("ch", "12.5", "", "en", "", "")
    private val state = AppState(library = listOf(c, b, a), progress = mapOf("b" to chapter),
        history = listOf(HistoryEntry(a, chapter, 0, 20, "2026-10-03T12:00:00Z")))
    @Test fun filtersCombineSearchCollectionFormatStatusAndReadingProgress() {
        val filtered = state.copy(libraryQuery = " a ", libraryOptions = LibraryOptions(format = "MANGA", status = "ongoing",
            progress = LibraryProgress.STARTED))
        assertEquals(listOf(a), visibleLibrary(filtered, setOf("a", "b")))
        assertEquals(listOf(c), visibleLibrary(state.copy(libraryOptions = LibraryOptions(progress = LibraryProgress.UNREAD))))
        assertEquals(listOf(a, b), visibleLibrary(state.copy(libraryOptions = LibraryOptions(progress = LibraryProgress.STARTED))))
        assertTrue(visibleLibrary(state, emptySet()).isEmpty())
    }
    @Test fun sortAndReverseUseNumericChapterProgressAndReadHistory() {
        assertEquals(listOf(a, b, c), visibleLibrary(state))
        assertEquals(listOf(c, b, a), visibleLibrary(state.copy(libraryOptions = LibraryOptions(descending = true))))
        assertEquals(listOf(c, b, a), visibleLibrary(state.copy(libraryOptions = LibraryOptions(sort = LibrarySort.SAVED))))
        assertEquals(a, visibleLibrary(state.copy(libraryOptions = LibraryOptions(sort = LibrarySort.READ, descending = true))).first())
        assertEquals(b, visibleLibrary(state.copy(libraryOptions = LibraryOptions(sort = LibrarySort.CHAPTER, descending = true))).first())
    }
    @Test fun collectionReorderingPreservesMembershipAndRejectsInvalidMoves() {
        val first = LibraryCollection("first", "First", setOf("a", "b"))
        val second = LibraryCollection("second", "Second", setOf("c"))
        val collections = listOf(first, second)
        assertEquals(listOf(second, first), moveLibraryCollection(collections, "first", 1))
        assertEquals(collections, moveLibraryCollection(collections, "first", -1))
        assertEquals(collections, moveLibraryCollection(collections, "missing", 1))
        assertEquals(collections, moveLibraryCollection(collections, "second", 1))
        val restored = org.json.JSONArray(moveLibraryCollection(collections, "second", -1).map { it.toJson() }.toString())
        assertEquals(second, restored.getJSONObject(0).libraryCollection())
        assertEquals(first, restored.getJSONObject(1).libraryCollection())
    }
    @Test fun displaySettingsPersistAndOlderPreferencesKeepUsableDefaults() {
        val options = LibraryOptions(view = LibraryView.COVER, columns = 4, showProgress = false,
            showLanguage = true, showContinue = true, showTabs = false, showCounts = true)
        assertEquals(options, JSONObject(options.toJson().toString()).libraryOptions())
        val older = JSONObject("""{"view":"COMPACT"}""").libraryOptions()
        assertEquals(LibraryView.COMPACT, older.view)
        assertTrue(older.showProgress)
        assertTrue(older.showTabs)
        assertEquals(0, older.columns)
        assertEquals(6, JSONObject("""{"columns":100}""").libraryOptions().columns)
        assertEquals(0, JSONObject("""{"columns":-5}""").libraryOptions().columns)
    }
    @Test fun viewFiltersAndSelectedCollectionSurviveStorage() {
        val options = LibraryOptions(LibrarySort.CHAPTER, true, LibraryView.LIST, "manga", "ongoing", LibraryProgress.STARTED, "favorites")
        assertEquals(options, JSONObject(options.toJson().toString()).libraryOptions())
        assertEquals(3, options.filterCount)
        assertEquals(LibraryOptions(), JSONObject("""{"view":"future-value","sort":"invalid"}""").libraryOptions())
    }
}
