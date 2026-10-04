package moe.comico.reader

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StorageSettingsTest {
    @Test fun clearsOnlyTemporaryChapterFilesAndKeepsDownloadsAndOtherCaches() {
        val directory = Files.createTempDirectory("storage-test").toFile()
        try {
            val extracted = File(directory, "offline-session").apply { mkdirs() }
            File(extracted, "1.png").writeBytes(ByteArray(7))
            val validation = File(directory, "validate-session").apply { mkdirs() }
            File(validation, "1.png").writeBytes(ByteArray(3))
            File(directory, "chapter-123.zip").writeBytes(ByteArray(5))
            val saved = File(directory, "saved-chapter.zip").apply { writeBytes(ByteArray(11)) }
            val previews = File(directory, "image_cache").apply { mkdirs() }
            File(previews, "preview").writeBytes(ByteArray(13))
            val update = File(directory, "updates").apply { mkdirs() }
            File(update, "update.apk").writeBytes(ByteArray(17))

            assertEquals(15L, chapterCacheFiles(directory).sumOf { storageBytes(it) })
            clearChapterFiles(directory)
            assertTrue(chapterCacheFiles(directory).isEmpty())
            assertTrue(saved.exists())
            assertEquals(13L, storageBytes(previews))
            assertEquals(17L, storageBytes(update))
        } finally { directory.deleteRecursively() }
    }
}
