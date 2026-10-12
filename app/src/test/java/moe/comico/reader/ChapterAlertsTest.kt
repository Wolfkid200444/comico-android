package moe.comico.reader

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.concurrent.TimeUnit

class ChapterAlertsTest {
    @Test fun skipsBacklogReadItemsAndPreviouslyDeliveredIds() {
        val old = ChapterAlert("old", "Old", "", 9)
        val boundary = ChapterAlert("boundary", "Boundary", "", 10)
        val delivered = ChapterAlert("seen", "Seen", "", 11)
        val read = ChapterAlert("read", "Read", "", 12, read = true)
        val newest = ChapterAlert("new", "New", "", 13)
        val items = listOf(newest, read, delivered, old, boundary, newest)
        assertEquals(listOf(boundary, newest), newChapterAlerts(items, 10, setOf("seen")))
        assertTrue(newChapterAlerts(items, 14, emptySet()).isEmpty())
        assertTrue(newChapterAlerts(items, 10, setOf("boundary", "new", "seen")).isEmpty())
    }

    @Test fun aFailedCheckCanRetryWithoutLosingNewItems() {
        val alert = ChapterAlert("chapter", "Story", "Chapter 12.5", 100)
        // Only successful delivery advances the persisted set.
        assertEquals(listOf(alert), newChapterAlerts(listOf(alert), 90, emptySet()))
        assertEquals(listOf(alert), newChapterAlerts(listOf(alert), 90, emptySet()))
        assertTrue(newChapterAlerts(listOf(alert), 90, setOf(alert.id)).isEmpty())
    }

    @Test fun parserUsesWebsiteNotificationFieldsAndRejectsMalformedDates() {
        val alert = JSONObject("""{"id":"event","title":"Story","detail":"Chapter 12.5","createdAt":"2026-10-12T12:00:00Z","read":false}""").chapterAlert()!!
        assertEquals("event", alert.id)
        assertEquals("Chapter 12.5", alert.detail)
        assertEquals(Instant.parse("2026-10-12T12:00:00Z").toEpochMilli(), alert.createdAt)
        assertNull(JSONObject("""{"id":"event","title":"Story","createdAt":"invalid"}""").chapterAlert())
        assertNull(JSONObject("""{"title":"Story","createdAt":"2026-10-12T12:00:00Z"}""").chapterAlert())
        assertEquals("", JSONObject("""{"id":"event","title":"Story","detail":null,"createdAt":"2026-10-12T12:00:00Z"}""").chapterAlert()!!.detail)
    }

    @Test fun requestsOnlyComicNotificationsAndDoesNotMarkThemRead() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"items":[{"id":"event","title":"Story","detail":"Chapter 2","createdAt":"2026-10-12T12:00:00Z","read":false}]}"""))
            val result = ComicoApi(baseUrl = server.url("/").toString().removeSuffix("/")).chapterNotifications()
            assertEquals(1, result.size)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("GET", request.method)
            assertEquals("/api/notifications", request.requestUrl!!.encodedPath)
            assertEquals("comics", request.requestUrl!!.queryParameter("tab"))
            assertEquals("100", request.requestUrl!!.queryParameter("limit"))
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
}
