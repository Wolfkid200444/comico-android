package moe.comico.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

const val BASE_URL = "https://comico.moe"
data class Manga(val id: String, val title: String, val cover: String, val status: String, val format: String, val tags: List<String>, val description: String = "", val rating: String = "safe")
data class Chapter(val id: String, val number: String, val title: String, val language: String, val group: String, val external: String)
data class PageResult<T>(val items: List<T>, val total: Int)

fun JSONObject.manga() = Manga(getString("id"), getString("title"), absoluteUrl(optString("coverUrl")), optString("status"), optString("format"), optJSONArray("tags").objects().map { it.optString("name") }, optString("description"), optString("contentRating", "safe"))
fun JSONObject.chapter() = Chapter(getString("id"), optString("number", "?"), optString("title"), optString("language"), optString("scanlationGroup", "Unknown group"), optString("externalUrl").takeUnless { it == "null" }.orEmpty())
fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
fun absoluteUrl(value: String): String = if (value.startsWith("/")) BASE_URL + value else value

class ComicoApi {
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject = withContext(Dispatchers.IO) {
        val url = (BASE_URL + path).toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val request = Request.Builder().url(url).header("User-Agent", "ComicoAndroid/0.1.0 (Kotlin Android reader; https://comico.moe)").header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(when(response.code) { 429 -> "Too many requests. Wait a minute and try again."; 404 -> "This title or chapter is no longer available."; else -> "Comico returned HTTP ${response.code}. Try again." })
            JSONObject(response.body?.string() ?: throw IOException("Empty response from Comico."))
        }
    }
    suspend fun catalog(query: String, offset: Int, format: String): PageResult<Manga> {
        val params = mutableMapOf("limit" to "30", "offset" to offset.toString(), "contentRating" to "safe")
        if (query.isNotBlank()) params["q"] = query
        if (format != "All") params["format"] = format.lowercase()
        val data = get(if(query.isBlank()) "/api/manga" else "/api/search", params)
        return PageResult(data.optJSONArray("items").objects().map { it.manga() }.filter { it.rating in listOf("safe", "suggestive") }, data.optInt("total"))
    }
    suspend fun detail(id: String) = get("/api/manga/$id").manga()
    suspend fun chapters(id: String, language: String, offset: Int): PageResult<Chapter> {
        val data = get("/api/manga/$id/chapters", mapOf("language" to language, "limit" to "50", "offset" to offset.toString(), "order" to "desc", "sort" to "number"))
        return PageResult(data.optJSONArray("items").objects().map { it.chapter() }, data.optInt("total"))
    }
}
