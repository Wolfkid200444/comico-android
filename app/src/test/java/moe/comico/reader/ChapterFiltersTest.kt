package moe.comico.reader
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ChapterFiltersTest {
    private fun chapter(id: String, group: String, groupId: String) =
        Chapter(id, id, "", "en", group, "", groupId, "comicklive")
    @Test fun groupsStaySeparateAndFilterWholeRoster() {
        val roster = listOf(chapter("3", "Team A", "a"), chapter("2", "Team B", "b"), chapter("1", "Team A", "a"))
        assertEquals(listOf(ChapterGroup("a", "Team A", 2), ChapterGroup("b", "Team B", 1)), chapterGroups(roster))
        assertEquals(listOf("3", "1"), filterChapterGroup(roster, "a").map { it.id })
        assertEquals(roster, filterChapterGroup(roster, null))
        assertTrue(filterChapterGroup(roster, "unknown").isEmpty())
    }
    @Test fun missingGroupsDoNotCreatePlaceholderChoices() {
        val roster = listOf(chapter("3", "Unknown group", ""), chapter("2", "null", ""), chapter("1", "", ""))
        assertTrue(chapterGroups(roster).isEmpty())
        assertEquals(roster, filterChapterGroup(roster, null))
        assertEquals(listOf(ChapterGroup("new-team", "New Team", 1)),
            chapterGroups(roster + chapter("4", "New Team", "new-team")))
    }
    @Test fun selectedGroupsSurviveStorageAndStayScopedToSourceAndLanguage() {
        val saved = ReaderOverride().withGroup("new-source", "en", "team-a")
            .withGroup("other-source", "en", "team-b").withGroup("new-source", "es", "team-c")
        val restored = JSONObject(saved.toJson().toString()).readerOverride()
        assertEquals("team-a", restored.groupFor("new-source", "en"))
        assertEquals("team-b", restored.groupFor("other-source", "en"))
        assertEquals("team-c", restored.groupFor("new-source", "es"))
        assertFalse(restored.isEmpty())
        assertNull(restored.withGroup("new-source", "en", null).groupFor("new-source", "en"))
        assertTrue(JSONObject("{}").readerOverride().chapterGroups.isEmpty())
    }
    @Test fun readerNavigationKeepsSelectedGroup() {
        val roster = listOf(chapter("3", "Team A", "a"), chapter("2", "Team B", "b"), chapter("1", "Team A", "a"))
        val state = AppState(reader = roster[2], chapterGroup = "a", chapterRoster = roster,
            nativeReader = NativeReaderState(next = roster[1]))
        assertEquals(null to roster[0], readerAdjacentChapters(state))
    }
    @Test fun chapterSourceAndGroupIdsPersist() {
        val chapter = chapter("1", "Team A", "a")
        assertEquals(chapter, chapter.toJson().chapter())
        val minimal = JSONObject("""{"id":"1","number":"1","sourceId":"mangadex","scanlationGroupId":null}""").chapter()
        assertEquals("mangadex", minimal.sourceId)
        assertEquals("", minimal.groupId)
    }
    @Test fun sourceChoiceDoesNotDependOnNativeReadability() {
        val state = AppState(readerPreferences = ReaderPreferences(source = "external"),
            mangaSources = listOf(ReaderSource("external", "External", readable = false, chapterCount = 100)))
        assertEquals("external", state.chapterSource())
        assertNull(state.copy(mangaSources = emptyList()).chapterSource())
    }
}
