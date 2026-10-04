package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LocalHistorySyncTest {
    private fun entry(mangaId: String, chapterId: String) = HistoryEntry(
        Manga(mangaId, "Title", "", "", "", emptyList()),
        Chapter(chapterId, "1", "", "en", "", ""), 0, 10, "2026-10-03T00:00:00Z")

    @Test fun oldLocalProgressIsRemovedWithoutChangingRealProgress() {
        val real = entry("manga", "chapter")
        val queued = JSONObject().put("chapter", real.toJson())
            .put("local-fixture", entry("local-fixture", "local-fixture").toJson())
            .put("other", entry("local-file", "other").toJson())
        val clean = remoteProgressQueue(queued)
        assertEquals(1, clean.length())
        assertEquals(real, clean.getJSONObject("chapter").historyEntry())
        assertEquals(3, queued.length())
        assertTrue(entry("local-file", "chapter").isLocalChapter())
        assertTrue(entry("manga", "local-file").isLocalChapter())
        assertFalse(real.isLocalChapter())
    }
}
