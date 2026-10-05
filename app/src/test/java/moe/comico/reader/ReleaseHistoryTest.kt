package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseHistoryTest {
    @Test fun releaseMetadataPreservesStatusDateAndMissingNotes() {
        val release = JSONObject("""{"tag_name":"v0.7.0","published_at":"2026-10-03T10:00:00Z","body":null,"prerelease":true}""").releaseHistory()!!
        assertEquals("0.7.0", release.version)
        assertTrue(release.prerelease)
        assertEquals("2026-10-03T10:00:00Z", release.publishedAt)
        assertEquals("No changelog was provided for this release.", releaseNotes(release.notes))
        assertNull(JSONObject("""{"tag_name":"v0.8.0","draft":true}""").releaseHistory())
        assertNull(JSONObject().releaseHistory())
    }
}
