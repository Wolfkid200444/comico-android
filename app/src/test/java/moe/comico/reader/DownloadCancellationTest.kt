package moe.comico.reader

import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadCancellationTest {
    @Test fun stopCancelsAnActiveChapterBodyAndRemovesPartialZip() = runBlocking {
        val server = MockWebServer()
        val file = File.createTempFile("chapter-cancel", ".zip")
        server.start()
        try {
            server.enqueue(MockResponse().setBody("x".repeat(100_000))
                .throttleBody(10, 1, TimeUnit.SECONDS))
            val api = ComicoApi(baseUrl = server.url("/").toString().removeSuffix("/"))
            val download = launch { api.downloadChapter("chapter", file) }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(5, TimeUnit.SECONDS)) }
            withTimeout(3000) { download.cancelAndJoin() }
            assertFalse(file.exists())
        } finally { server.shutdown(); file.delete() }
    }
}
