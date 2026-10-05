package moe.comico.reader

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReaderImageCacheTest {
    @Test fun cachesEveryPageAndRemovesPartialArchiveOnFailure() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val archive = File.createTempFile("reader-test", ".zip")
            val directory = kotlin.io.path.createTempDirectory().toFile()
            try {
                val session = ReaderSession(listOf(server.url("/1").toString(), server.url("/2").toString()), "Test", "", ProxyMethod.AUTO, null)
                val api = ComicoApi()
                server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("first-page"))
                server.enqueue(MockResponse().setHeader("Content-Type", "image/webp").setBody("second-page"))
                api.cacheReaderImages(session, archive)
                val pages = archive.inputStream().use { extractChapterArchive(it, directory) }
                assertEquals(listOf("first-page", "second-page"), pages.map { it.readText() })
                assertEquals("$BASE_URL/", server.takeRequest().getHeader("Referer"))
                server.takeRequest()
                server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody("first-page"))
                server.enqueue(MockResponse().setResponseCode(500))
                assertTrue(runCatching { api.cacheReaderImages(session, archive) }.isFailure)
                assertFalse(archive.exists())
                val offlineApi = ComicoApi(networkAvailable = { false })
                assertTrue(runCatching { offlineApi.requestText(server.url("/blocked").toString()) }.isFailure)
                assertEquals(4, server.requestCount)
            } finally { archive.delete(); directory.deleteRecursively() }
        }
    }
}
