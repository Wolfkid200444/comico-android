package moe.comico.reader

import org.junit.Assert.*
import org.junit.Test

class BadgeArtworkTest {
    @Test fun resolvesAllSixAwardedBadgesWithoutScrapingHtml() {
        val badges = awardedBadgeArtwork(listOf("supporter", "discord-linked", "first-page", "completionist", "polyglot", "early-adopter"), true)
        assertEquals(6, badges.size)
        assertEquals(3, badges.count { it.drawable != null })
        assertEquals(3, badges.count { it.url.endsWith("-50.png") })
    }
    @Test fun doesNotAwardCatalogBadgesToTheUser() {
        assertTrue(awardedBadgeArtwork(emptyList(), false).isEmpty())
        assertEquals(1, awardedBadgeArtwork(emptyList(), true).size)
        assertEquals(38, badgeCatalog.size)
    }
}
