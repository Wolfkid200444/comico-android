package moe.comico.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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

class ComicoApi(private val accountCookies: AccountCookieJar? = null) {
    private val client = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).apply { accountCookies?.let { cookieJar(it) } }.build()
    suspend fun requestText(url: String, body: JSONObject? = null): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("User-Agent", "ComicoAndroid/${BuildConfig.VERSION_NAME} (https://github.com/Wolfkid200444/comico-android)")
            .apply { if(url.toHttpUrl().host == "comico.moe" && url.toHttpUrl().encodedPath.startsWith("/api/auth/")) header("Origin",BASE_URL) }
            .apply { if(body != null) post(body.toString().toRequestBody("application/json".toMediaType())) }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && url.toHttpUrl().host == "comico.moe" && url.toHttpUrl().encodedPath.startsWith("/api/auth/")) {
                val error = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                throw IOException(accountErrorMessage(error?.optString("code").orEmpty(),error?.optString("message")?.takeIf { it.isNotBlank() } ?: "Account request failed with HTTP ${response.code}. Try again."))
            }
            if (!response.isSuccessful) throw IOException(when(response.code) {
                429 -> "Too many requests. Wait a minute and try again."
                404 -> "This title or chapter is no longer available."
                410 -> "The reader session expired. Reload this chapter."
                else -> "${url.toHttpUrl().host} returned HTTP ${response.code}. Try another proxy method or source."
            })
            response.body?.string() ?: throw IOException("Empty response from the source.")
        }
    }
    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        val url = (BASE_URL + path).toHttpUrl().newBuilder().apply { query.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
        return JSONObject(requestText(url.toString()))
    }
    suspend fun post(path: String, query: Map<String, String>, body: JSONObject): JSONObject {
        val url = (BASE_URL + path).toHttpUrl().newBuilder().apply { query.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
        return JSONObject(requestText(url.toString(), body))
    }
    suspend fun mangaSources(id: String) = JSONArray(requestText("$BASE_URL/api/manga/$id/sources")).objects().map {
        ReaderSource(it.getString("sourceId"), it.optString("name", it.getString("sourceId")), it.optInt("readableChapterCount") > 0)
    }
    suspend fun readerSources(id: String) = JSONArray(requestText("$BASE_URL/api/reader/chapters/$id/sources?direct=true")).objects().map {
        ReaderSource(it.getString("readerSourceId"), it.optString("name", it.getString("readerSourceId")), it.optBoolean("readable", true), it.optString("providerSourceName").takeUnless { name -> name.isBlank() || name == "null" })
    }.distinctBy { it.id }
    suspend fun catalog(query: String, offset: Int, filters: SearchFilters): PageResult<Manga> {
        val search = query.isNotBlank() || filters.tags.isNotEmpty()
        val url = (BASE_URL + if(search) "/api/search" else "/api/manga").toHttpUrl().newBuilder()
        filters.query(query,offset).forEach { (key,value) -> url.addQueryParameter(key,value) }
        val data = JSONObject(requestText(url.build().toString()))
        return PageResult(data.optJSONArray("items").objects().map { it.manga() }.filter { it.rating in filters.allowedRatings() },data.optInt("total"))
    }
    suspend fun comickTags() = JSONArray(requestText("$BASE_URL/api/tags?source=comick-metadata")).objects().map { ComickTag(it.getString("id"),it.getString("name")) }.sortedBy { it.name.lowercase() }
    suspend fun detail(id: String) = get("/api/manga/$id").manga()
    suspend fun chapters(id: String, language: String, offset: Int, source: String? = null): PageResult<Chapter> {
        val data = get("/api/manga/$id/chapters", mapOf("language" to language, "limit" to "50", "offset" to offset.toString(), "order" to "desc", "sort" to "number") + (source?.let { mapOf("source" to it) } ?: emptyMap()))
        return PageResult(data.optJSONArray("items").objects().map { it.chapter() }, data.optInt("total"))
    }
    suspend fun firstChapter(id: String, language: String, source: String?): Chapter? {
        val data = get("/api/manga/$id/chapters",mapOf("language" to language,"limit" to "1","offset" to "0","order" to "asc","sort" to "number") + (source?.let { mapOf("source" to it) } ?: emptyMap()))
        return data.optJSONArray("items").objects().firstOrNull()?.chapter()
    }
    suspend fun account(): AccountUser? {
        val text = requestText("$BASE_URL/api/auth/get-session?disableCookieCache=true")
        if(text.trim() == "null") return null
        return JSONObject(text).optJSONObject("user")?.accountUser()
    }
    suspend fun signIn(identifier: String, password: String): AccountUser {
        val (path,body) = signInPayload(identifier,password)
        post(path,emptyMap(),body)
        return account() ?: throw IOException("Sign-in completed, but the session could not be loaded. Try signing in again.")
    }
    suspend fun register(username: String, name: String, email: String, password: String) {
        require(password.length >= 8) { "Use a password with at least 8 characters." }
        post("/api/auth/sign-up/email",emptyMap(),JSONObject().put("username",username.trim()).put("name",name.trim().ifBlank { username.trim() }).put("email",email.trim()).put("password",password))
    }
    suspend fun signOut() { post("/api/auth/sign-out",emptyMap(),JSONObject()) }

}
