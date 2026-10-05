package moe.comico.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class OfflineChapter(val manga: Manga, val chapter: Chapter, val archive: String, val source: String) {
    fun toJson() = JSONObject().put("manga", manga.toJson()).put("chapter", chapter.toJson()).put("archive", archive).put("source", source)
}
fun JSONObject.offlineChapter() = OfflineChapter(getJSONObject("manga").manga(), getJSONObject("chapter").chapter(),
    getString("archive"), getString("source"))

private const val MAX_CHAPTER_BYTES = 512L * 1024 * 1024

fun copyChapterBytes(input: InputStream, output: OutputStream, limit: Long = MAX_CHAPTER_BYTES, checkActive: () -> Unit = {}): Long {
    val buffer = ByteArray(8192)
    var total = 0L
    while (true) {
        checkActive()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > limit) throw IOException("This chapter exceeds the offline storage size limit.")
        output.write(buffer, 0, count)
    }
    return total
}

fun extractChapterArchive(input: InputStream, directory: File, checkActive: () -> Unit = {}): List<File> {
    val pages = mutableListOf<File>()
    var bytes = 0L
    var entries = 0
    ZipInputStream(input).use { zip ->
        while (true) {
            checkActive()
            val entry = zip.nextEntry ?: break
            if (++entries > 2000) throw IOException("This archive contains too many entries.")
            val name = entry.name.replace('\\', '/')
            if (name.startsWith('/') || name.split('/').any { it == ".." }) throw IOException("The chapter archive contains an unsafe path.")
            if (entry.isDirectory) continue
            val extension = name.substringAfterLast('.', "").lowercase()
            if (extension !in listOf("jpg", "jpeg", "png", "webp", "gif", "avif")) {
                bytes += copyChapterBytes(zip, object : OutputStream() { override fun write(value: Int) {} override fun write(buffer: ByteArray, offset: Int, length: Int) {} }, MAX_CHAPTER_BYTES - bytes, checkActive)
                continue
            }
            if (pages.size >= 1000) throw IOException("This chapter contains too many images.")
            val file = File(directory, name.substringAfterLast('/').take(120))
            if (file.exists()) throw IOException("The chapter archive contains duplicate page names.")
            file.outputStream().use { bytes += copyChapterBytes(zip, it, MAX_CHAPTER_BYTES - bytes, checkActive) }
            if (file.length() == 0L) throw IOException("The chapter contains an empty image.")
            pages += file
        }
    }
    if (pages.isEmpty()) throw IOException("The download contains no chapter images.")
    return pages.sortedWith(compareBy<File> { it.name.substringBeforeLast('.').toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.name })
}

class OfflineChapterStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("offlineChapters", 0)
    private val writes = Mutex()
    fun folder(owner: String?): String? = prefs.getString(accountStorageKey("folder", owner), null)
    fun chooseFolder(owner: String?, uri: Uri) {
        require(uri.authority == "com.android.externalstorage.documents") { "Choose a folder in internal storage or on an SD card so chapters remain available offline." }
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs.edit().putString(accountStorageKey("folder", owner), uri.toString()).apply()
    }
    fun resetFolder(owner: String?) { prefs.edit().remove(accountStorageKey("folder", owner)).apply() }
    fun entries(owner: String?): List<OfflineChapter> = runCatching {
        JSONArray(prefs.getString(accountStorageKey("chapters", owner), "[]")).objects().map { it.offlineChapter() }
    }.getOrDefault(emptyList())
    private fun persist(owner: String?, entries: List<OfflineChapter>) {
        prefs.edit().putString(accountStorageKey("chapters", owner), JSONArray(entries.map { it.toJson() }).toString()).apply()
    }
    suspend fun save(owner: String?, manga: Manga, chapter: Chapter, source: String, archive: File): OfflineChapter = writes.withLock { withContext(Dispatchers.IO) {
        val chosenFolder = folder(owner)
        // Validate every page before publishing the ZIP to the offline library.
        val validation = File(context.cacheDir, "validate-${UUID.randomUUID()}").apply { mkdirs() }
        try { archive.inputStream().use { extractChapterArchive(it, validation) { coroutineContext.ensureActive() } } } finally { validation.deleteRecursively() }
        coroutineContext.ensureActive()
        val name = manga.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(60) + " - Chapter " +
            chapter.number.replace(Regex("[^0-9A-Za-z._-]"), "_").take(20) + ".zip"
        if (chosenFolder == null) {
            val ownerDirectory = File(context.filesDir, "offline-chapters/" + UUID.nameUUIDFromBytes((owner ?: "guest").toByteArray()))
            check(ownerDirectory.mkdirs() || ownerDirectory.isDirectory) { "Couldn't create chapter storage." }
            val destination = File(ownerDirectory, "${UUID.randomUUID()}.zip")
            try {
                archive.inputStream().use { input -> destination.outputStream().use { copyChapterBytes(input, it) { coroutineContext.ensureActive() } } }
                coroutineContext.ensureActive()
                val entry = OfflineChapter(manga, chapter, Uri.fromFile(destination).toString(), source)
                check(prefs.edit().putString(accountStorageKey("chapters", owner), JSONArray((entries(owner).filterNot { it.chapter.id == chapter.id } + entry).map { it.toJson() }).toString()).commit()) { "Could not save the offline chapter index." }
                return@withContext entry
            } catch (e: Exception) { destination.delete(); throw e }
        }
        val tree = Uri.parse(chosenFolder)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val document = DocumentsContract.createDocument(context.contentResolver, parent, "application/zip", name)
            ?: throw IOException("Couldn't create a file in the chosen folder.")
        try {
            context.contentResolver.openOutputStream(document, "w")?.use { output ->
                archive.inputStream().use { copyChapterBytes(it, output) }
            } ?: throw IOException("The chosen folder is no longer writable. Choose it again.")
            coroutineContext.ensureActive()
            val entry = OfflineChapter(manga, chapter, document.toString(), source)
            check(prefs.edit().putString(accountStorageKey("chapters", owner), JSONArray((entries(owner).filterNot { it.chapter.id == chapter.id } + entry).map { it.toJson() }).toString()).commit()) { "Could not save the offline chapter index." }
            entry
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, document) }
            throw e
        }
    } }
    suspend fun session(entry: OfflineChapter): ReaderSession = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.filter { it.name.startsWith("offline-") }?.forEach { it.deleteRecursively() }
        val directory = File(context.cacheDir, "offline-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val pages = context.contentResolver.openInputStream(Uri.parse(entry.archive))?.use { extractChapterArchive(it, directory) { coroutineContext.ensureActive() } }
                ?: throw IOException("The downloaded file is unavailable. Restore access to its folder or download it again.")
            coroutineContext.ensureActive()
            ReaderSession(pages.map { Uri.fromFile(it).toString() }, entry.source, "Downloaded chapter · Offline", ProxyMethod.AUTO, null)
        } catch (e: Exception) { directory.deleteRecursively(); throw e }
    }
    suspend fun delete(owner: String?, entry: OfflineChapter) = writes.withLock { withContext(Dispatchers.IO) {
        val uri = Uri.parse(entry.archive)
        val deleted = if (uri.scheme == "file") {
            val file = File(uri.path ?: throw IOException("Invalid download path."))
            require(file.canonicalPath.startsWith(File(context.filesDir, "offline-chapters").canonicalPath + File.separator)) { "Invalid download location." }
            file.delete() || !file.exists()
        } else DocumentsContract.deleteDocument(context.contentResolver, uri)
        if (!deleted) throw IOException("Couldn't delete this download.")
        persist(owner, entries(owner).filterNot { it.archive == entry.archive })
    } }
}
