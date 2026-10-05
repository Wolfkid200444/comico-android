package moe.comico.reader

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.launch

data class MangaDownload(
    val id: String, val owner: String, val manga: Manga,
    val chapters: List<Chapter>, val language: String = "en", val source: String? = null,
    val group: String? = null, val all: Boolean = false,
    val completed: Int = 0, val skipped: Int = 0,
    val status: String = "Queued", val error: String? = null
) {
    val active get() = status in listOf("Queued", "Downloading", "Waiting for internet")
    fun toJson() = JSONObject().put("id", id).put("owner", owner).put("manga", manga.toJson())
        .put("chapters", JSONArray(chapters.map { it.toJson() })).put("language", language)
        .put("source", source).put("group", group).put("all", all).put("completed", completed)
        .put("skipped", skipped).put("status", status).put("error", error)
}
fun JSONObject.mangaDownload() = MangaDownload(
    getString("id"), getString("owner"), getJSONObject("manga").manga(),
    optJSONArray("chapters").objects().map { it.chapter() }, optString("language", "en"),
    optString("source").takeUnless { it.isBlank() || it == "null" },
    optString("group").takeUnless { it.isBlank() || it == "null" }, optBoolean("all"),
    optInt("completed"), optInt("skipped"), optString("status", "Queued"),
    optString("error").takeUnless { it.isBlank() || it == "null" }
)
fun chapterDownloadMessage(tasks: List<MangaDownload>, mangaId: String?): String? =
    tasks.lastOrNull { it.manga.id == mangaId }?.let {
        "${it.completed} saved · ${it.skipped} skipped · ${it.status}" + (it.error?.let { error -> ". $error" } ?: "")
    }

/** One serial queue respects Comico's quota and avoids competing chapter ZIP requests. */
class ChapterDownloads private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("chapterDownloadQueue", 0)
    private val ownerPrefs = context.getSharedPreferences("reader", 0)
    private val store = OfflineChapterStore(context)
    private val mutable = MutableStateFlow(runCatching {
        JSONArray(prefs.getString("tasks", "[]")).objects().map { it.mangaDownload() }
            .map { if (it.active) it.copy(status = "Queued") else it }
    }.getOrDefault(emptyList()))
    val tasks = mutable.asStateFlow()
    private var currentId: String? = null
    private var currentJob: Job? = null
    private fun update(id: String, change: (MangaDownload) -> MangaDownload) {
        mutable.value = mutable.value.map { if (it.id == id) change(it) else it }
        persist()
    }
    private fun persist() { prefs.edit().putString("tasks", JSONArray(mutable.value.map { it.toJson() }).toString()).apply() }
    fun enqueue(task: MangaDownload) {
        val existing = mutable.value.firstOrNull { it.owner == task.owner && it.manga.id == task.manga.id && it.active }
        if (existing != null) return
        mutable.value = mutable.value.filterNot { it.owner == task.owner && it.manga.id == task.manga.id } + task
        persist()
        start()
    }
    fun start() {
        if (mutable.value.none { it.active }) return
        try { context.startForegroundService(Intent(context, ChapterDownloadService::class.java)) }
        catch (e: Exception) {
            mutable.value.filter { it.active }.forEach { task ->
                update(task.id) { it.copy(status = "Paused", error = "Open the app and tap Retry to continue downloads.") }
            }
        }
    }
    fun stop(id: String) {
        update(id) { it.copy(status = "Stopped", error = null) }
        if (currentId == id) currentJob?.cancel()
    }
    fun retry(id: String) { update(id) { it.copy(status = "Queued", error = null) }; start() }
    fun cancelOtherOwners(owner: String?) {
        mutable.value.filter { it.owner != owner && it.active }.forEach { stop(it.id) }
    }
    fun pauseAll() {
        mutable.value.filter { it.active }.forEach { task ->
            update(task.id) { it.copy(status = "Paused", error = "Android stopped background work. Open Downloads and tap Retry.") }
        }
        currentJob?.cancel()
    }
    suspend fun runQueue() = kotlinx.coroutines.supervisorScope {
        while (true) {
            val task = mutable.value.firstOrNull { it.active } ?: break
            currentId = task.id
            currentJob = launchDownload(task)
            currentJob?.join()
            currentJob = null
            currentId = null
        }
    }
    private fun kotlinx.coroutines.CoroutineScope.launchDownload(task: MangaDownload) = launch {
        try {
            suspend fun waitForInternet() {
                while (!hasInternet(context)) {
                    update(task.id) { it.copy(status = "Waiting for internet", error = null) }
                    delay(2000)
                }
                ensureActive()
                check(ownerPrefs.getString("localLibraryOwner", null) == task.owner) { "Account changed. Sign in to this account before retrying." }
                update(task.id) { it.copy(status = "Downloading", error = null) }
            }
            waitForInternet()
            val api = ComicoApi(AccountCookieJar(context), networkAvailable = { hasInternet(context) })
            suspend fun <T> onlineRequest(request: suspend () -> T): T {
                while (true) {
                    waitForInternet()
                    try {
                        return request()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (hasInternet(context)) throw e
                    }
                }
            }
            check(onlineRequest { api.account() }?.id == task.owner) { "Sign in to this account before retrying." }
            val chapters = if (task.all) onlineRequest {
                filterChapterGroup(api.chapterRoster(task.manga.id, task.language, task.source), task.group)
            } else task.chapters
            update(task.id) { it.copy(chapters = chapters, all = false) }
            for (chapter in chapters) {
                ensureActive()
                if (store.entries(task.owner).any { it.chapter.id == chapter.id }) {
                    update(task.id) { it.copy(skipped = it.skipped + 1, chapters = it.chapters.filterNot { pending -> pending.id == chapter.id }) }
                    continue
                }
                while (true) {
                    waitForInternet()
                    val file = withContext(Dispatchers.IO) { File.createTempFile("chapter-", ".zip", context.cacheDir) }
                    try {
                        api.downloadChapter(chapter.id, file)
                        ensureActive()
                        check(ownerPrefs.getString("localLibraryOwner", null) == task.owner) { "Account changed." }
                        store.save(task.owner, task.manga, chapter, chapter.sourceId, file)
                        update(task.id) { it.copy(completed = it.completed + 1, chapters = it.chapters.filterNot { pending -> pending.id == chapter.id }) }
                        break
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (!hasInternet(context)) continue
                        if (e.message?.startsWith("Comico already marks") == true) {
                            update(task.id) { it.copy(skipped = it.skipped + 1, chapters = it.chapters.filterNot { pending -> pending.id == chapter.id }) }
                            break
                        }
                        throw e
                    } finally { file.delete() }
                }
            }
            update(task.id) { it.copy(status = "Completed", error = null) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { update(task.id) { it.copy(status = "Failed", error = e.message ?: "Download failed.") } }
    }
    companion object {
        @Suppress("StaticFieldLeak") // Holds only the application context.
        @Volatile private var instance: ChapterDownloads? = null
        fun get(context: Context): ChapterDownloads = instance ?: synchronized(this) {
            instance ?: ChapterDownloads(context.applicationContext).also { instance = it }
        }
    }
}
