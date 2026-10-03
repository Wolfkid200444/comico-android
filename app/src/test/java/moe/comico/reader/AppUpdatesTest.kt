package moe.comico.reader
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
class AppUpdatesTest {
    @Test fun changelogIsOptional() {
        assertEquals("Fixed reading bugs.", releaseNotes(" Fixed reading bugs. "))
        assertEquals("No changelog was provided for this release.", releaseNotes(" "))
        assertEquals("No changelog was provided for this release.", releaseNotes("null"))
        assertEquals("Hello", release().put("body", "Hello").appRelease("0.6.0")?.notes)
        assertEquals("", release().put("body", JSONObject.NULL).appRelease("0.6.0")?.notes)
    }
    @Test fun validatesReleaseChecksumMetadata() {
        val obj = release()
        obj.getJSONArray("assets").getJSONObject(0).put("digest", "sha256:" + "a".repeat(64))
        assertEquals("a".repeat(64), obj.appRelease("0.6.0")?.sha256)
        obj.getJSONArray("assets").getJSONObject(0).put("digest", "sha256:invalid")
        assertNull(obj.appRelease("0.6.0"))
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest("test".toByteArray())
        assertTrue(matchesUpdateHash(null, digest))
        assertFalse(matchesUpdateHash("a".repeat(64), digest))
        assertTrue(matchesUpdateHash(digest.joinToString("") { "%02x".format(it) }, digest))
    }
    @Test fun versionsUseNumericComparison() {
        assertTrue(newerVersion("v0.10.0", "0.6.0"))
        assertTrue(newerVersion("v1.0.0", "0.99.99"))
        assertFalse(newerVersion("v0.6.0", "0.6.0"))
        assertFalse(newerVersion("v0.5.9", "0.6.0"))
        assertFalse(newerVersion("v0.7.0-beta", "0.6.0"))
        assertFalse(newerVersion("v99999999999.0.0", "0.6.0"))
    }
    private fun release() = JSONObject("""{"tag_name":"v0.7.0","html_url":"https://github.com/Wolfkid200444/comico-android/releases/tag/v0.7.0","assets":[{"name":"comico-android-0.7.0.apk","state":"uploaded","browser_download_url":"https://github.com/Wolfkid200444/comico-android/releases/download/v0.7.0/comico-android-0.7.0.apk"}]}""")
    @Test fun acceptsOnlyPublishedNewReleaseWithApk() {
        assertEquals("0.7.0", release().appRelease("0.6.0")?.version)
        assertNull(release().appRelease("0.7.0"))
        assertNull(release().put("draft", true).appRelease("0.6.0"))
        assertNull(release().put("prerelease", true).appRelease("0.6.0"))
        assertNull(release().put("assets", org.json.JSONArray()).appRelease("0.6.0"))
        val pending = release()
        pending.getJSONArray("assets").getJSONObject(0).put("state", "new")
        assertNull(pending.appRelease("0.6.0"))
    }
    @Test fun downloadMustBelongToTheGitHubRepository() {
        val bad = release()
        bad.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://example.com/app.apk")
        assertNull(bad.appRelease("0.6.0"))
        assertNull(release().put("html_url", "http://github.com/$UPDATE_REPOSITORY/releases/tag/v0.7.0").appRelease("0.6.0"))
    }
}
