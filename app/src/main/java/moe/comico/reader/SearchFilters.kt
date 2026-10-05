package moe.comico.reader

import org.json.JSONObject

enum class SearchSort(val label: String, val apiValue: String) { RELEVANCE("Relevance", "relevance"), LATEST("Recently updated", "latest"), POPULAR("Most followed", "popular"), TITLE("Title A–Z", "title") }
enum class ContentFilter(val label: String, val apiValue: String) { SAFE("Safe only", "safe"), SUGGESTIVE("Up to suggestive", "suggestive"), EROTICA("Up to erotica", "erotica"), ALL("All ratings", "pornographic") }
enum class MangaType(val label: String, val apiValue: String) { MANGA("Manga", "manga"), MANHWA("Manhwa", "manhwa"), MANHUA("Manhua", "manhua"), WEBTOON("Webtoon", "webtoon") }
enum class Demographic(val label: String, val apiValue: String) { SHOUNEN("Shounen", "shounen"), SHOUJO("Shoujo", "shoujo"), SEINEN("Seinen", "seinen"), JOSEI("Josei", "josei") }
enum class ReleaseStatus(val label: String, val apiValue: String) { ONGOING("Ongoing", "ongoing"), COMPLETED("Completed", "completed"), HIATUS("Hiatus", "hiatus"), CANCELLED("Cancelled", "cancelled") }
data class ComickTag(val id: String, val name: String)
data class SearchFilters(val sort: SearchSort = SearchSort.RELEVANCE, val content: ContentFilter = ContentFilter.SAFE, val type: MangaType? = null, val demographic: Demographic? = null, val status: ReleaseStatus? = null, val tags: List<String> = emptyList()) {
    val activeCount: Int get() = listOf(sort != SearchSort.RELEVANCE, content != ContentFilter.SAFE,type != null,demographic != null,status != null,tags.isNotEmpty()).count { it }
    fun allowedRatings() = ContentFilter.entries.take(content.ordinal + 1).map { it.apiValue }
    fun query(query: String, offset: Int): List<Pair<String,String>> = buildList {
        add("limit" to "30");add("offset" to offset.toString())
        val search = query.isNotBlank() || tags.isNotEmpty()
        if(query.isNotBlank()) add("q" to query.trim().take(200))
        if(search) add("source" to "comick")
        add("sort" to if(!search && sort == SearchSort.RELEVANCE) "latest" else sort.apiValue)
        allowedRatings().forEach { add("contentRating" to it) }
        type?.let { add("format" to it.apiValue) }
        demographic?.let { add("demographic" to it.apiValue) }
        status?.let { add("status" to it.apiValue) }
        tags.distinct().take(20).forEach { add("comickTags" to it) }
    }
    fun toJson() = JSONObject().put("sort",sort.name).put("content",content.name).put("type",type?.name ?: JSONObject.NULL).put("demographic",demographic?.name ?: JSONObject.NULL).put("status",status?.name ?: JSONObject.NULL).put("tags",org.json.JSONArray(tags))
}
fun JSONObject.searchFilters() = SearchFilters(SearchSort.entries.find { it.name == optString("sort") } ?: SearchSort.RELEVANCE,ContentFilter.entries.find { it.name == optString("content") } ?: ContentFilter.SAFE,MangaType.entries.find { it.name == optString("type") },Demographic.entries.find { it.name == optString("demographic") },ReleaseStatus.entries.find { it.name == optString("status") },optJSONArray("tags")?.strings()?.distinct()?.take(20).orEmpty())
