package moe.comico.reader

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

enum class DiscoverFeed(val label: String, val sort: String = "", val accountOnly: Boolean = false) {
    UPDATED("Recently updated","latest"), POPULAR("Recent popular","popular"), RELEASES("New releases","release"), FOLLOWED("Most followed","follows"),
    UPDATES("New chapters from followed comics",accountOnly = true), HISTORY("Reading history",accountOnly = true)
}
data class HistoryEntry(val manga: Manga, val chapter: Chapter, val page: Int, val pageCount: Int, val readAt: String) {
    fun progressPayload() = JSONObject().put("chapterId",chapter.id).put("page",page).put("pageCount",pageCount).put("completed",pageCount > 0 && page >= pageCount - 1)
    fun toJson() = JSONObject().put("manga",manga.toJson()).put("chapter",chapter.toJson()).put("page",page).put("pageCount",pageCount).put("readAt",readAt)
}
fun Manga.toJson() = JSONObject().put("latestChapter", JSONObject().put("number",latestChapterNumber).put("publishedAt",updatedAt)).put("updatedAt",updatedAt).put("id",id).put("title",title).put("coverUrl",cover).put("status",status).put("format",format).put("description",description).put("contentRating",rating).put("tags",JSONArray(tags.map { JSONObject().put("name",it) })).put("authors",JSONArray(credits.map { JSONObject().put("name",it.name).put("role",it.role) }))
fun Chapter.toJson() = JSONObject().put("id",id).put("number",number).put("title",title).put("language",language).put("scanlationGroup",group).put("externalUrl",external).put("scanlationGroupId",groupId).put("sourceId",sourceId)
fun JSONObject.historyEntry(): HistoryEntry {
    val manga = JSONObject(getJSONObject("manga").toString()).apply {
        if(!has("id")) put("id",this@historyEntry.getString("mangaId"))
    }.manga()
    val chapter = JSONObject(getJSONObject("chapter").toString()).apply {
        if(!has("id")) put("id",this@historyEntry.getString("chapterId"))
    }.chapter()
    return HistoryEntry(manga,chapter,optInt("page").coerceAtLeast(0),optInt("pageCount").coerceAtLeast(0),getString("readAt"))
}
fun JSONObject.followedUpdateManga(): Manga {
    val manga = optJSONObject("manga")?.manga() ?: Manga(getString("mangaId"),getString("mangaTitle"),absoluteUrl(optString("coverUrl")),optString("status"),optString("format"),emptyList(),rating = optString("contentRating","safe"))
    return manga.copy(updatedAt = manga.updatedAt.ifBlank { optString("updatedAt").takeUnless { it == "null" }.orEmpty() })
}
fun mergeHistory(remote: List<HistoryEntry>, local: List<HistoryEntry>) = (remote + local).groupBy { it.chapter.id }.values.map { entries -> entries.maxBy { runCatching { Instant.parse(it.readAt) }.getOrDefault(Instant.EPOCH) } }.sortedByDescending { runCatching { Instant.parse(it.readAt) }.getOrDefault(Instant.EPOCH) }
data class LeaderboardEntry(val id: String, val name: String, val username: String, val image: String, val level: Int, val exp: Long)
fun JSONObject.leaderboardEntry() = LeaderboardEntry(getString("id"),optString("name"),optString("username"),optString("image").takeUnless { it == "null" }.orEmpty(),optInt("level"),optLong("exp"))
data class AccountComment(
    val id: String, val body: String, val createdAt: String, val mangaTitle: String,
    val context: String = "", val coverUrl: String = "", val href: String = ""
)
fun JSONObject.accountComment(): AccountComment {
    fun text(key: String) = optString(key).takeUnless { it == "null" }.orEmpty()
    val mangaTitle = optJSONObject("manga")?.optString("title").orEmpty()
    val chapter = optJSONObject("chapter")
    val chapterNumber = chapter?.optString("number")?.takeUnless { it == "null" }.orEmpty()
    val fallback = listOf(mangaTitle, chapterNumber.takeIf { it.isNotBlank() }?.let { "Chapter $it" }.orEmpty())
        .filter { it.isNotBlank() }.joinToString(" · ")
    return AccountComment(getString("id"),text("body"),text("createdAt"),mangaTitle,
        text("context").ifBlank { fallback }, absoluteUrl(text("coverUrl")), text("href"))
}

fun HistoryEntry.isLocalChapter() = manga.id.startsWith("local-") || chapter.id.startsWith("local-")

fun remoteProgressQueue(queued: JSONObject): JSONObject = JSONObject(queued.toString()).apply {
    keys().asSequence().toList().forEach { id ->
        val entry = optJSONObject(id)
        if (id.startsWith("local-") || entry?.optJSONObject("chapter")?.optString("id")?.startsWith("local-") == true ||
            entry?.optJSONObject("manga")?.optString("id")?.startsWith("local-") == true) remove(id)
    }
}

fun matchesPendingProgress(queued: JSONObject?, uploaded: HistoryEntry): Boolean =
    queued != null && runCatching { queued.historyEntry() == uploaded }.getOrDefault(false)

fun accountStorageKey(key: String, owner: String?) = owner?.let { "$key:account:$it" } ?: key

fun accountHistorySnapshot(remote: List<HistoryEntry>, uploaded: List<HistoryEntry>, queued: List<HistoryEntry>, local: List<HistoryEntry>) =
    mergeHistory(remote,uploaded + queued + local.filter { it.pageCount == 0 })

data class DiscoverSection(val items: List<Manga> = emptyList(), val loading: Boolean = false, val error: String? = null)
fun visibleDiscoverFeeds(signedIn: Boolean): List<DiscoverFeed> =
    (if(signedIn) listOf(DiscoverFeed.UPDATES,DiscoverFeed.HISTORY) else emptyList()) + DiscoverFeed.entries.filterNot { it.accountOnly }

fun filterLibrary(library: List<Manga>, query: String): List<Manga> = library.filter { query.isBlank() || it.title.contains(query.trim(),ignoreCase = true) }

fun updateAge(value: String, now: Instant = Instant.now()): String? {
    val updated = runCatching { Instant.parse(value) }.getOrNull() ?: return null
    if (updated.isAfter(now.plusSeconds(60)))
        return updated.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    val minutes = java.time.Duration.between(updated, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        minutes < 10080 -> "${minutes / 1440}d ago"
        else -> updated.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    }
}
