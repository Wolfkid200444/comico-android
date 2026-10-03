package moe.comico.reader

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AccountDataTest {
    private val manga = Manga("m1","Story","","ongoing","manga",emptyList())
    private val chapter = Chapter("c1","1","","en","group","")
    private fun entry(page: Int, time: String) = HistoryEntry(manga,chapter,page,10,time)
    @Test fun historyKeepsNewerProgressEvenWhenRereadingEarlierPages() {
        val remote = entry(9,"2026-10-01T10:00:00Z")
        val local = entry(1,"2026-10-02T10:00:00Z")
        assertEquals(listOf(local),mergeHistory(listOf(remote),listOf(local)))
        assertEquals(listOf(local),mergeHistory(listOf(local),listOf(remote)))
        assertEquals(local,JSONObject(local.toJson().toString()).historyEntry())
    }
    @Test fun remoteHistoryDeletionDoesNotResurrectCachedEntries() {
        val cached = entry(3,"2026-10-01T10:00:00Z")
        assertTrue(accountHistorySnapshot(emptyList(),emptyList(),emptyList(),listOf(cached)).isEmpty())
        assertEquals(listOf(cached),accountHistorySnapshot(emptyList(),emptyList(),listOf(cached),listOf(cached)))
        val localOnly = cached.copy(pageCount = 0)
        assertEquals(listOf(localOnly),accountHistorySnapshot(emptyList(),emptyList(),emptyList(),listOf(localOnly)))
    }
    @Test fun parsesWebsiteHistoryWithChapterIdOutsideChapterSummary() {
        val json = JSONObject().put("manga",manga.toJson()).put("chapterId","c1").put("chapter",JSONObject().put("number","1").put("title",""))
            .put("page",3).put("pageCount",10).put("readAt","2026-10-02T10:00:00Z")
        assertEquals("c1",json.historyEntry().chapter.id)
        assertEquals("m1",json.historyEntry().manga.id)
        assertEquals(3,json.historyEntry().page)
    }
    @Test fun parsesFlatWebsiteFollowedUpdates() {
        val json = JSONObject().put("mangaId","m1").put("mangaTitle","Story").put("coverUrl","/cover.jpg").put("chapterId","c1")
        assertEquals("m1",json.followedUpdateManga().id)
        assertEquals("Story",json.followedUpdateManga().title)
        assertEquals("https://comico.moe/cover.jpg",json.followedUpdateManga().cover)
    }
    @Test fun accountCachesAndGuestDataUseSeparateKeys() {
        for(key in listOf("library","history","progress","readerPage:c1","pending:bookmarks","pending:progress")) {
            assertEquals(key,accountStorageKey(key,null))
            assertNotEquals(accountStorageKey(key,"alice"),accountStorageKey(key,"bob"))
            assertNotEquals(accountStorageKey(key,"alice"),accountStorageKey(key,null))
        }
    }
    @Test fun requestsMatchWebsiteLibraryAndProgressContracts() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = ComicoApi(baseUrl = server.url("/").toString().trimEnd('/'))
            server.enqueue(MockResponse().setBody("{}"))
            api.changeBookmark("m1",true)
            var request = server.takeRequest()
            assertEquals("PUT",request.method)
            assertEquals("/api/library/m1",request.path)
            assertEquals("plan_to_read",JSONObject(request.body.readUtf8()).getString("status"))
            server.enqueue(MockResponse().setResponseCode(204))
            api.changeBookmark("m1",false)
            request = server.takeRequest()
            assertEquals("DELETE",request.method)
            assertEquals("/api/library/m1",request.path)
            server.enqueue(MockResponse().setBody("{}"))
            api.saveProgress(entry(9,"2026-10-02T10:00:00Z"))
            request = server.takeRequest()
            assertEquals("POST",request.method)
            assertEquals("/api/progress/m1",request.path)
            val payload = JSONObject(request.body.readUtf8())
            assertEquals("c1",payload.getString("chapterId"))
            assertEquals(9,payload.getInt("page"))
            assertEquals(10,payload.getInt("pageCount"))
            assertTrue(payload.getBoolean("completed"))
            assertFalse(entry(0,"2026-10-02T10:00:00Z").copy(pageCount = 0).progressPayload().getBoolean("completed"))
        }
    }
    @Test fun importsAccountLibraryAndServerHistory() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = ComicoApi(baseUrl = server.url("/").toString().trimEnd('/'))
            server.enqueue(MockResponse().setBody(JSONObject().put("items",org.json.JSONArray().put(JSONObject().put("manga",manga.toJson()))).put("total",1).toString()))
            assertEquals(listOf(manga),api.library())
            assertEquals("/api/library?limit=100&offset=0&sort=recent",server.takeRequest().path)
            val history = entry(3,"2026-10-02T10:00:00Z")
            server.enqueue(MockResponse().setBody(org.json.JSONArray().put(history.toJson()).toString()))
            assertEquals(listOf(history),api.history())
            assertEquals("/api/history",server.takeRequest().path)
        }
    }
    @Test fun libraryImportLoadsAllPagesWithoutATotalField() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = ComicoApi(baseUrl = server.url("/").toString().trimEnd('/'))
            val batch = org.json.JSONArray()
            repeat(100) { batch.put(JSONObject().put("manga",manga.copy(id = "m$it").toJson())) }
            server.enqueue(MockResponse().setBody(JSONObject().put("items",batch).toString()))
            server.enqueue(MockResponse().setBody(JSONObject().put("items",org.json.JSONArray().put(JSONObject().put("manga",manga.copy(id = "m100").toJson()))).toString()))
            assertEquals(101,api.library().size)
            assertEquals("0",server.takeRequest().requestUrl!!.queryParameter("offset"))
            assertEquals("100",server.takeRequest().requestUrl!!.queryParameter("offset"))
        }
    }
    @Test fun discoverFeedsUseDistinctWebsiteSorts() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = ComicoApi(baseUrl = server.url("/").toString().trimEnd('/'))
            for(feed in DiscoverFeed.entries.filterNot { it.accountOnly }) {
                server.enqueue(MockResponse().setBody("[]"))
                api.discover(feed,SearchFilters())
                assertEquals(feed.sort,server.takeRequest().requestUrl!!.queryParameter("sort"))
            }
            assertEquals(4,DiscoverFeed.entries.filterNot { it.accountOnly }.map { it.sort }.distinct().size)
        }
    }
}
