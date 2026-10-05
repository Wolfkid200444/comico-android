package moe.comico.reader

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class DownloadQueueTest {
    private val manga = Manga("one", "First manga", "", "", "", emptyList())
    private val chapter = Chapter("chapter", "1", "", "en", "", "", sourceId = "source")

    @Test fun airplaneModeWithStaleVpnIsOffline() {
        assertFalse(internetAvailable(true, true, false))
        assertFalse(internetAvailable(false, false, false))
        assertTrue(internetAvailable(true, true, true))
        assertTrue(internetAvailable(true, false, true))
    }

    @Test fun persistedQueueKeepsOwnerSourceAndRemainingChapters() {
        val task = MangaDownload("task", "owner", manga, listOf(chapter), source = "source",
            group = "group", completed = 3, status = "Waiting for internet")
        assertEquals(task, JSONObject(task.toJson().toString()).mangaDownload())
        assertTrue(task.active)
        assertFalse(task.copy(status = "Stopped").active)
    }

    @Test fun failureDoesNotAppearOnAnotherManga() {
        val failed = MangaDownload("task", "owner", manga, listOf(chapter), completed = 39,
            status = "Failed", error = "Daily download limit reached")
        assertTrue(chapterDownloadMessage(listOf(failed), "one")!!.contains("Daily download limit"))
        assertNull(chapterDownloadMessage(listOf(failed), "two"))
        assertNull(chapterDownloadMessage(listOf(failed), null))
    }
}
