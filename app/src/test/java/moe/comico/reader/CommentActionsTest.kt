package moe.comico.reader

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CommentActionsTest {
    @Test fun profileEndpointWithoutCountsIsHydratedFromItsDiscussion() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""[{"id":"c","body":"Hello","context":"Chapter 1","href":"/reader/chapter"},{"id":"r","body":"Reply","context":"Chapter 1","href":"/reader/chapter"}]"""))
            server.enqueue(MockResponse().setBody("""{"items":[{"id":"c","body":"Hello","votes":{"likes":1,"dislikes":0,"mine":1},"isOwn":true,"reactions":[{"emoji":"😮","count":1,"reacted":true}],"replies":[{"id":"r","body":"Reply","votes":{"likes":3,"dislikes":1,"mine":-1}}]}],"total":1}"""))
            val api = ComicoApi(baseUrl = server.url("/").toString().trimEnd('/'))
            val comments = api.comments("reader")
            assertTrue(comments.first().votesLoaded)
            assertEquals(1, comments.first().likes)
            assertEquals(1, comments.first().vote)
            assertEquals(CommentReaction("😮", 1, true), comments.first().reactions.single())
            assertEquals(3, comments.last().likes)
            assertEquals("c", comments.last().parentId)
            assertEquals(2, server.requestCount)
            server.takeRequest()
            val request = server.takeRequest()
            assertEquals("chapter", request.requestUrl?.queryParameter("chapterId"))
            assertNull(request.requestUrl?.queryParameter("mangaId"))

            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody("""{"items":[{"id":"c","votes":{"likes":2,"dislikes":0,"mine":1}}],"total":1}"""))
            api.voteDiscussion("c", "like")
            assertEquals(2, api.refreshAccountComment(comments.first()).likes)
            assertEquals(4, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun unavailableDetailsRemainUnknownAndOnlyComicoTargetsAreAccepted() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""[{"id":"c","href":"/reader/chapter"}]"""))
            server.enqueue(MockResponse().setResponseCode(503))
            val comments = ComicoApi(baseUrl = server.url("/").toString().trimEnd('/')).comments("reader")
            assertFalse(comments.single().votesLoaded)
        } finally { server.shutdown() }
        assertNull(AccountComment("c", "", "", "", href = "https://other.example/reader/c").discussionTarget())
        assertNull(AccountComment("c", "", "", "", href = "/settings").discussionTarget())
        assertFalse(AccountComment("c", "", "", "", href = "/manga/m").discussionTarget()!!.chapter)
    }

    @Test fun reactionDeleteAndReplyUseWebsiteContracts() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = ComicoApi(baseUrl = server.url("/").toString().trimEnd('/'))
            server.enqueue(MockResponse().setBody("{}"))
            api.reactDiscussion("comment", "😮")
            server.takeRequest().let {
                assertEquals("PUT", it.method)
                assertEquals("/api/comments/comment/reaction", it.path)
                assertEquals("😮", JSONObject(it.body.readUtf8()).getString("emoji"))
            }
            server.enqueue(MockResponse().setBody("{}"))
            api.deleteDiscussion("comment")
            server.takeRequest().let {
                assertEquals("DELETE", it.method)
                assertEquals("/api/comments/comment", it.path)
            }
            server.enqueue(MockResponse().setBody("{}"))
            api.postDiscussion(DiscussionTarget("chapter", "Chapter 1", true), " Reply ", "parent")
            val reply = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("Reply", reply.getString("body"))
            assertEquals("parent", reply.getString("parentId"))
            assertEquals("chapter", reply.getString("chapterId"))
            assertFalse(reply.has("mangaId"))
        } finally { server.shutdown() }
    }

    @Test fun discussionCanFindOldCommentsBeyondTheFirstPage() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val items = org.json.JSONArray()
            repeat(100) { items.put(JSONObject().put("id", "c$it")) }
            server.enqueue(MockResponse().setBody(JSONObject().put("items", items).put("total", 101).toString()))
            server.enqueue(MockResponse().setBody("""{"items":[{"id":"old","votes":{"likes":7}}],"total":101}"""))
            val comments = ComicoApi(baseUrl = server.url("/").toString().trimEnd('/')).discussion(DiscussionTarget("m", "Manga"))
            assertEquals(7, comments.findComment("old")!!.likes)
            assertEquals(2, server.requestCount)
            assertEquals("0", server.takeRequest().requestUrl?.queryParameter("offset"))
            assertEquals("100", server.takeRequest().requestUrl?.queryParameter("offset"))
        } finally { server.shutdown() }
    }
}
