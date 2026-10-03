package moe.comico.reader

import org.junit.Assert.*
import org.junit.Test

class BadgeCatalogProbeTest {
    @Test fun catalogIncludesEveryWebsiteBadgeFamily() {
        listOf("staff", "translator", "contributor", "early-adopter", "discord-linked", "supporter",
            "bookworm-gold", "scanlator-gold", "veteran-gold", "lunar-new-year").forEach { id ->
            assertNotNull("Missing $id", badgeCatalog[id])
        }
    }
}
