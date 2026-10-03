package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PublicProfileTest {
    @Test fun parsesAwardedBadgesAndStats() {
        val profile = JSONObject("""{"image":null,"level":4,"exp":2234,"supporter":true,"icons":[{"iconId":"first-page"},{"iconId":"discord-linked"},{"iconId":"first-page"}],"stats":{"chaptersRead":15,"titlesFollowed":219,"comments":2}}""").publicProfile()
        assertEquals("", profile.image)
        assertEquals(listOf("first-page", "discord-linked"), profile.badges)
        assertEquals(15, profile.chaptersRead)
        assertEquals(219, profile.titlesFollowed)
        assertEquals(2234L, profile.exp)
        assertTrue(profile.supporter)
    }
    @Test fun handlesMissingOptionalProfileData() {
        val profile = JSONObject("{}").publicProfile()
        assertTrue(profile.badges.isEmpty())
        assertEquals("", profile.bio)
        assertEquals(0, profile.comments)
        assertFalse(profile.supporter)
    }
}
