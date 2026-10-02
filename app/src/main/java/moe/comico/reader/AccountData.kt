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
fun Manga.toJson() = JSONObject().put("id",id).put("title",title).put("coverUrl",cover).put("status",status).put("format",format).put("description",description).put("contentRating",rating).put("tags",JSONArray(tags.map { JSONObject().put("name",it) }))
fun Chapter.toJson() = JSONObject().put("id",id).put("number",number).put("title",title).put("language",language).put("scanlationGroup",group).put("externalUrl",external)
fun JSONObject.historyEntry() = HistoryEntry(getJSONObject("manga").manga(),getJSONObject("chapter").chapter(),optInt("page").coerceAtLeast(0),optInt("pageCount").coerceAtLeast(0),getString("readAt"))
fun mergeHistory(remote: List<HistoryEntry>, local: List<HistoryEntry>) = (remote + local).groupBy { it.chapter.id }.values.map { entries -> entries.maxBy { runCatching { Instant.parse(it.readAt) }.getOrDefault(Instant.EPOCH) } }.sortedByDescending { runCatching { Instant.parse(it.readAt) }.getOrDefault(Instant.EPOCH) }
data class LeaderboardEntry(val id: String, val name: String, val username: String, val image: String, val level: Int, val exp: Long)
fun JSONObject.leaderboardEntry() = LeaderboardEntry(getString("id"),optString("name"),optString("username"),optString("image").takeUnless { it == "null" }.orEmpty(),optInt("level"),optLong("exp"))
data class AccountComment(val id: String, val body: String, val createdAt: String, val mangaTitle: String)
fun JSONObject.accountComment() = AccountComment(getString("id"),optString("body"),optString("createdAt"),optJSONObject("manga")?.optString("title").orEmpty())

fun accountStorageKey(key: String, owner: String?) = owner?.let { "$key:account:$it" } ?: key

fun accountHistorySnapshot(remote: List<HistoryEntry>, uploaded: List<HistoryEntry>, queued: List<HistoryEntry>, local: List<HistoryEntry>) =
    mergeHistory(remote,uploaded + queued + local.filter { it.pageCount == 0 })
