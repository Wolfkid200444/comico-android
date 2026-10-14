package moe.comico.reader

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CollectionApiTest {
    @Test fun createIsPrivateAndMembershipUsesSingleMangaId() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = ComicoApi(baseUrl = server.url("/").toString().removeSuffix("/"))
            server.enqueue(MockResponse().setBody("""{"id":"remote"}"""))
            assertEquals("remote", api.createCollection("Favorites"))
            val create = server.takeRequest()
            assertEquals("POST", create.method)
            val body = JSONObject(create.body.readUtf8())
            assertEquals("Favorites", body.getString("name"))
            assertFalse(body.getBoolean("isPublic"))
            server.enqueue(MockResponse().setBody("{}"))
            api.changeCollectionMember("remote", "manga", true)
            val add = server.takeRequest()
            assertEquals("/api/collections/remote/items", add.path)
            assertEquals("POST", add.method)
            assertEquals("manga", JSONObject(add.body.readUtf8()).getString("mangaId"))
            server.enqueue(MockResponse().setBody("{}"))
            api.changeCollectionMember("remote", "manga", false)
            val remove = server.takeRequest()
            assertEquals("DELETE", remove.method)
            assertEquals("manga", JSONObject(remove.body.readUtf8()).getString("mangaId"))
        } finally { server.shutdown() }
    }
    @Test fun collectionPagesAndMembershipKeepServerIds() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = ComicoApi(baseUrl = server.url("/").toString().removeSuffix("/"))
            server.enqueue(MockResponse().setBody("""{"items":[{"id":"remote","name":"Favorites"}],"total":1}"""))
            val collection = api.collections().single()
            assertEquals("remote", collection.remoteId)
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"items":[{"mangaId":"one"},{"manga":{"id":"two","title":"Two"}}],"total":2}"""))
            assertEquals(setOf("one", "two"), api.collectionMembers(collection).mangaIds)
            assertTrue(server.takeRequest().path!!.startsWith("/api/collections/remote?"))
        } finally { server.shutdown() }
    }
    @Test fun unrecognizedMembershipFailsRatherThanErasingLocalData() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = ComicoApi(baseUrl = server.url("/").toString().removeSuffix("/"))
            server.enqueue(MockResponse().setBody("""{"items":[{"unexpected":"value"}]}"""))
            assertTrue(runCatching { api.collectionMembers(LibraryCollection("remote", "Favorites", remoteId = "remote")) }.isFailure)
        } finally { server.shutdown() }
    }
}
