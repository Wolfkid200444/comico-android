package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CollectionContractProbeTest {
    @Test fun collectionsPreserveMembershipAcrossStorage() {
        val original = LibraryCollection("favorites", "Favorites", setOf("m1", "m2"))
        assertEquals(original, JSONObject(original.toJson().toString()).libraryCollection())
        assertEquals(emptySet<String>(), JSONObject("""{"id":"new","name":"New"}""").libraryCollection().mangaIds)
    }
    @Test fun collectionNamesRejectBlankLongAndDuplicateNames() {
        val existing = listOf(LibraryCollection("1", "Favorites"))
        assertNotNull(collectionNameError("  ", existing))
        assertNotNull(collectionNameError("a".repeat(61), existing))
        assertNotNull(collectionNameError(" favorites ", existing))
        assertNull(collectionNameError("Favorites", existing, "1"))
        assertNull(collectionNameError("New", existing))
    }
}
