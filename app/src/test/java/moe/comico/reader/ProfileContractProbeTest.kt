package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileContractProbeTest {
    @Test fun validatesWebLinksBeforeCreatingPayload() {
        assertTrue(validProfileUrl("https://example.com/profile?tab=about"))
        listOf("not a link", "javascript:alert(1)", "https://", "https://example.com/a b",
            "https://user:password@example.com").forEach { assertFalse(it, validProfileUrl(it)) }
        assertNotNull(ProfileEdit(links = List(6) { "https://example.com/$it" }).validationError())
        assertNotNull(ProfileEdit(banner = "invalid").validationError())
    }
    @Test fun serializesGravatarAndLinksLikeWebsite() {
        val payload = ProfileEdit(links = listOf(" https://example.com ", "", "https://example.org")).payload()
        assertTrue(payload.isNull("image"))
        assertTrue(payload.isNull("banner"))
        assertEquals("https://example.com\nhttps://example.org", payload.getString("links"))
    }
    @Test fun loadsEditableSettings() {
        val edit = JSONObject("""{"image":null,"banner":"https://example.com/banner.png","bio":"Hello","links":["https://example.com"]}""").profileEdit()
        assertEquals("", edit.image)
        assertEquals("https://example.com/banner.png", edit.banner)
        assertEquals(listOf("https://example.com"), edit.links)
    }
}
