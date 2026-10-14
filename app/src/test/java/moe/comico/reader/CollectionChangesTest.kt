package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CollectionChangesTest {
    @Test fun offlineEditsComposeWithoutOverwritingUnrelatedServerEdits() {
        val before = LibraryCollection("id", "Original", setOf("one", "two"))
        val edited = before.copy(name = "Renamed", mangaIds = setOf("two", "three"))
        val pending = queueCollectionChanges(listOf(before), listOf(edited), emptyMap())
        val latestRemote = before.copy(mangaIds = before.mangaIds + "website-added")
        assertEquals(setOf("two", "three", "website-added"), applyCollectionChange(latestRemote, pending.getValue("id"))!!.mangaIds)
        val back = edited.copy(mangaIds = setOf("one", "two"))
        val composed = queueCollectionChanges(listOf(edited), listOf(back), pending).getValue("id")
        assertEquals(setOf("one", "two", "website-added"), applyCollectionChange(latestRemote, composed)!!.mangaIds)
        assertEquals("Renamed", composed.name)
        assertEquals(composed, JSONObject(composed.toJson().toString()).collectionChange())
    }
    @Test fun deletionsStayDeletedAndReorderingDoesNotQueueRemoteEdits() {
        val one = LibraryCollection("one", "One", setOf("title"))
        val two = LibraryCollection("two", "Two")
        assertTrue(queueCollectionChanges(listOf(one, two), listOf(two, one), emptyMap()).isEmpty())
        val pending = queueCollectionChanges(listOf(one), emptyList(), emptyMap()).getValue("one")
        assertTrue(pending.deleted)
        assertNull(applyCollectionChange(one, pending))
    }
    @Test fun guestCollectionsMergeByNameAndDeletionKeepsItsRemoteId() {
        val account = LibraryCollection("local", "Favorites", setOf("one"), remoteId = "server")
        val guest = LibraryCollection("guest", "favorites", setOf("two"))
        val merged = mergeGuestCollections(listOf(account), listOf(guest)).single()
        assertEquals("server", merged.remoteId)
        assertEquals(setOf("one", "two"), merged.mangaIds)
        val deletion = queueCollectionChanges(listOf(merged), emptyList(), emptyMap()).getValue("local")
        assertEquals("server", deletion.remoteId)
        assertEquals(deletion, JSONObject(deletion.toJson().toString()).collectionChange())
    }
    @Test fun newCollectionsKeepAllTheirOfflineMembersInTheQueue() {
        val created = LibraryCollection("local", "New", setOf("title"))
        val change = queueCollectionChanges(emptyList(), listOf(created), emptyMap()).getValue("local")
        assertEquals("New", change.name)
        assertEquals(setOf("title"), change.added)
    }
}
