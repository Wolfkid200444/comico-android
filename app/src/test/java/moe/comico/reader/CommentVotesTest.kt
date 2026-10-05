package moe.comico.reader

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CommentVotesTest {
    @Test fun parsesVotesForCommentsAndReplies() {
        val comment = JSONObject("""{"id":"c","votes":{"likes":12,"dislikes":3,"mine":1},"replies":[{"id":"r","votes":{"likes":2,"dislikes":1,"mine":-1}}]}""").discussionComment()
        assertEquals(12, comment.likes)
        assertEquals(3, comment.dislikes)
        assertEquals(1, comment.vote)
        assertEquals(-1, comment.replies.single().vote)
        assertEquals(2, comment.replies.single().likes)
        val old = JSONObject("""{"id":"old"}""").discussionComment()
        assertEquals(0, old.likes)
        assertEquals(0, old.vote)
        assertTrue(JSONObject("""{"id":"deleted","deleted":true}""").discussionComment().deleted)
    }

    @Test fun accountCommentsPreserveVotesAndDeletedState() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""[{"id":"mine","body":"Hello","context":"Chapter 1","votes":{"likes":5,"dislikes":2,"mine":-1}},{"id":"deleted","body":"Hidden","deleted":true}]"""))
            val comments = ComicoApi(baseUrl = server.url("/").toString().trimEnd('/')).comments("reader")
            val comment = comments.first()
            assertEquals(5, comment.likes)
            assertEquals(2, comment.dislikes)
            assertEquals(-1, comment.vote)
            assertEquals("Chapter 1", comment.context)
            assertTrue(comments.last().deleted)
            assertEquals("Comment deleted", comments.last().body)
            assertEquals("/api/users/reader/comments?limit=50", server.takeRequest().path)
        } finally { server.shutdown() }
        val old = JSONObject("""{"id":"old","body":"Hello"}""").accountComment()
        assertEquals(0, old.likes)
        assertEquals(0, old.dislikes)
        assertEquals(0, old.vote)
    }

    @Test fun sendsServerVoteStringsIncludingRepeatedVotes() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val api = ComicoApi(baseUrl = server.url("/").toString().trimEnd('/'))
            for (vote in listOf("like", "like", "dislike")) {
                server.enqueue(MockResponse().setBody("{}"))
                api.voteDiscussion("comment", vote)
                val request = server.takeRequest()
                assertEquals("PUT", request.method)
                assertEquals("/api/comments/comment/vote", request.path)
                assertEquals(vote, JSONObject(request.body.readUtf8()).getString("vote"))
            }
            try {
                api.voteDiscussion("comment", "0")
                fail("Only server-supported vote strings may be sent.")
            } catch (_: IllegalArgumentException) { }
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun propagatesVoteFailureAndBlocksOfflineRequests() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"statusMessage":"Voting is unavailable"}"""))
            val address = server.url("/").toString().trimEnd('/')
            try {
                ComicoApi(baseUrl = address).voteDiscussion("comment", "like")
                fail("Failed votes must not be reported as successful.")
            } catch (e: IOException) { assertEquals("Voting is unavailable", e.message) }
            try {
                ComicoApi(baseUrl = address, networkAvailable = { false }).voteDiscussion("comment", "dislike")
                fail("Offline votes must not send a request.")
            } catch (_: IllegalStateException) { }
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
}
