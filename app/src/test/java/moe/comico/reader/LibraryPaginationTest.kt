package moe.comico.reader
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LibraryPaginationTest {
    @Test fun repeatedServerPageStopsInsteadOfRefreshingForever() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val items = JSONArray()
            repeat(100) { items.put(JSONObject().put("manga", JSONObject().put("id", "m$it").put("title", "Title $it"))) }
            val response = JSONObject().put("items", items).toString()
            repeat(2) { server.enqueue(MockResponse().setBody(response)) }
            try {
                ComicoApi(baseUrl = server.url("/").toString().trimEnd('/')).library()
                fail("Repeated pages must stop the sync.")
            } catch(e: IOException) { assertTrue(e.message.orEmpty().contains("pagination")) }
            assertEquals(2, server.requestCount)
            assertEquals("0", server.takeRequest().requestUrl?.queryParameter("offset"))
            assertEquals("100", server.takeRequest().requestUrl?.queryParameter("offset"))
        } finally { server.shutdown() }
    }
}
