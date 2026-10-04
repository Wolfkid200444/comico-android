package moe.comico.reader

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ChapterActionsTest {
    private fun chapter(id: String, title: String = "", external: String = "") =
        Chapter(id, "12", title, "en", "Group", external)

    @Test fun downloadQueueSkipsSavedExternalAndDuplicateEntries() {
        val saved = chapter("saved")
        val pending = chapter("pending")
        val manga = Manga("manga", "Title", "", "", "", emptyList())
        assertEquals(listOf(pending), chapterDownloadQueue(
            listOf(saved, pending, pending, chapter("external", external = "https://example.com")),
            listOf(OfflineChapter(manga, saved, "content://zip", "Source"))))
    }

    @Test fun explicitUnreadOverridesCompletedHistory() {
        val chapter = chapter("read")
        val manga = Manga("manga", "Title", "", "", "", emptyList())
        val state = AppState(history = listOf(HistoryEntry(manga, chapter, 9, 10, "")))
        assertTrue(state.chapterRead(chapter))
        assertFalse(state.copy(chapterReadMarks = mapOf(chapter.id to false)).chapterRead(chapter))
        assertTrue(AppState(chapterReadMarks = mapOf(chapter.id to true)).chapterRead(chapter))
        assertFalse(AppState().chapterRead(chapter))
    }

    @Test fun compactRowsHideOnlyRedundantTitles() {
        assertNull(chapterSubtitle(chapter("1", " Chapter 12 ")))
        assertNull(chapterSubtitle(chapter("1", "")))
        assertEquals("A new beginning", chapterSubtitle(chapter("1", "A new beginning")))
    }

    @Test fun commentBadgesDistinguishKnownTotalsFromPresence() {
        assertEquals(7, commentIndicator(JSONObject("""{"total":7,"items":[{}]}""")))
        assertEquals(-1, commentIndicator(JSONObject("""{"items":[{}]}""")))
        assertEquals(0, commentIndicator(JSONObject("""{"items":[]}""")))
        val reply = DiscussionComment("2", "", "", "")
        assertEquals(2, discussionCommentCount(listOf(DiscussionComment("1", "", "", "", listOf(reply)))))
    }
}
