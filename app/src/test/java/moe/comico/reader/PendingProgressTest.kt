package moe.comico.reader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PendingProgressTest {
    private val entry = HistoryEntry(Manga("m", "Title", "", "ongoing", "manga", emptyList()),
        Chapter("c", "1", "", "en", "Group", ""), 2, 20, "2026-10-03T00:00:00Z")
    @Test fun clearsUploadedProgressEvenIfJsonOrderOrOptionalMetadataDiffers() {
        val saved = entry.toJson()
        saved.getJSONObject("manga").remove("authors")
        val reordered = JSONObject()
        saved.keys().asSequence().toList().reversed().forEach { reordered.put(it, saved.get(it)) }
        assertTrue(matchesPendingProgress(reordered, entry))
    }
    @Test fun preservesNewerProgressWrittenDuringSync() {
        assertFalse(matchesPendingProgress(entry.copy(page = 3).toJson(), entry))
        assertFalse(matchesPendingProgress(entry.copy(readAt = "2026-10-03T00:01:00Z").toJson(), entry))
        assertFalse(matchesPendingProgress(null, entry))
        assertFalse(matchesPendingProgress(JSONObject(), entry))
    }
}
