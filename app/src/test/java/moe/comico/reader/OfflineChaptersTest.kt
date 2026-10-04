package moe.comico.reader

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OfflineChaptersTest {
    private fun archive(vararg names: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip -> names.forEach { name -> zip.putNextEntry(ZipEntry(name)); zip.write(byteArrayOf(1, 2, 3)); zip.closeEntry() } }
    }.toByteArray()
    @Test fun chapterArchivesReadOfflineInPageOrderAndRejectUnsafePaths() {
        val directory = java.nio.file.Files.createTempDirectory("chapter-test").toFile()
        try {
            val pages = extractChapterArchive(ByteArrayInputStream(archive("10.png", "2.jpg", "1.webp")), directory)
            assertEquals(listOf("1.webp", "2.jpg", "10.png"), pages.map { it.name })
            assertArrayEquals(byteArrayOf(1, 2, 3), pages.first().readBytes())
        } finally { directory.deleteRecursively() }
        listOf("../escape.png", "/absolute.png").forEach { name ->
            val target = java.nio.file.Files.createTempDirectory("unsafe-chapter").toFile()
            try {
                try { extractChapterArchive(ByteArrayInputStream(archive(name)), target); fail("Unsafe path accepted") }
                catch (_: IOException) { assertTrue(target.listFiles().isNullOrEmpty()) }
            } finally { target.deleteRecursively() }
        }
    }
    @Test fun rejectsEmptyArchivesDuplicatePageNamesAndOversizedCopies() {
        listOf(archive("readme.txt"), archive("a/1.png", "b/1.png")).forEach { bytes ->
            val directory = java.nio.file.Files.createTempDirectory("invalid-chapter").toFile()
            try { try { extractChapterArchive(ByteArrayInputStream(bytes), directory); fail("Invalid chapter accepted") } catch (_: IOException) {} }
            finally { directory.deleteRecursively() }
        }
        try { copyChapterBytes(ByteArrayInputStream(ByteArray(20)), ByteArrayOutputStream(), 10); fail("Size limit ignored") } catch (_: IOException) {}
    }
    @Test fun downloadUsesWebsiteEndpointAndPreservesQuotaErrors() = runBlocking {
        val server = MockWebServer()
        server.start()
        val file = File.createTempFile("download-test", ".zip")
        try {
            val api = ComicoApi(baseUrl = server.url("/").toString().trimEnd('/'))
            val zip = archive("1.png")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/zip").setBody(Buffer().write(zip)))
            api.downloadChapter("chapter-id", file)
            assertArrayEquals(zip, file.readBytes())
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/downloads/chapter", request.path)
            assertEquals("chapter-id", JSONObject(request.body.readUtf8()).getString("chapterId"))
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"Daily chapter download limit reached"}"""))
            try { api.downloadChapter("chapter-id", file); fail("Quota error ignored") } catch (e: IOException) {
                assertTrue(e.message!!.contains("Daily chapter")); assertFalse(file.exists())
            }
            server.enqueue(MockResponse().setResponseCode(409).setBody("""{"data":{"kind":"already-downloaded"},"message":"Already downloaded"}"""))
            try { api.downloadChapter("chapter-id", file); fail("Duplicate error ignored") } catch (e: IOException) {
                assertTrue(e.message!!.contains("Import")); assertFalse(file.exists())
            }
        } finally { file.delete(); server.shutdown() }
    }
    @Test fun downloadedFilterAndMetadataSurviveStorage() {
        val manga = Manga("m", "Offline", "", "ongoing", "manga", emptyList())
        val other = manga.copy(id = "other", title = "Online")
        val chapter = Chapter("ch", "1", "", "en", "", "")
        val entry = OfflineChapter(manga, chapter, "content://local/ch.zip", "Source")
        assertEquals(entry, JSONObject(entry.toJson().toString()).offlineChapter())
        val options = LibraryOptions(downloadedOnly = true, showDownloads = true)
        assertEquals(options, JSONObject(options.toJson().toString()).libraryOptions())
        assertEquals(1, options.filterCount)
        assertEquals(listOf(manga), visibleLibrary(AppState(library = listOf(other, manga), offlineChapters = listOf(entry), libraryOptions = options)))
    }
}
