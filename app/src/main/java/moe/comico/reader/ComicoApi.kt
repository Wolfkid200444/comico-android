package moe.comico.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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
data class Manga(val id: String, val title: String, val cover: String, val status: String, val format: String, val tags: List<String>, val description: String = "", val rating: String = "safe", val credits: List<MangaCredit> = emptyList(), val updatedAt: String = "", val latestChapterNumber: String = "")
data class MangaCredit(val name: String, val role: String)
data class Chapter(val id: String, val number: String, val title: String, val language: String, val group: String, val external: String, val groupId: String = "", val sourceId: String = "")
data class PageResult<T>(val items: List<T>, val total: Int)

fun JSONObject.manga() = Manga(getString("id"), getString("title"), absoluteUrl(optString("coverUrl")), optString("status"), optString("format"), optJSONArray("tags").objects().map { it.optString("name") }, optString("description"), optString("contentRating", "safe"), optJSONArray("authors").objects().mapNotNull {
    val name = it.optString("name").trim().takeUnless { name -> name.isEmpty() || name == "null" }
    name?.let { name -> MangaCredit(name, it.optString("role", "author")) }
}, updatedAt = optJSONObject("latestChapter")?.optString("publishedAt")?.takeUnless { it.isBlank() || it == "null" }
    ?: optString("updatedAt").takeUnless { it == "null" }.orEmpty(),
    latestChapterNumber = optJSONObject("latestChapter")?.optString("number")?.takeUnless { it == "null" }.orEmpty())
fun JSONObject.chapter() = Chapter(getString("id"), optString("number", "?"), optString("title"), optString("language"), optString("scanlationGroup", "Unknown group"), optString("externalUrl").takeUnless { it == "null" }.orEmpty(), optString("scanlationGroupId").takeUnless { it == "null" }.orEmpty(), optString("sourceId").takeUnless { it == "null" }.orEmpty())
fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
fun absoluteUrl(value: String): String = if (value.startsWith("/")) BASE_URL + value else value

class ComicoApi(private val accountCookies: AccountCookieJar? = null, private val baseUrl: String = BASE_URL, private val networkAvailable: () -> Boolean = { true }) {
    private val client = OkHttpClient.Builder().callTimeout(40, TimeUnit.SECONDS).apply { accountCookies?.let { cookieJar(it) } }.build()
    suspend fun requestText(url: String, body: JSONObject? = null, method: String = if(body == null) "GET" else "POST"): String = withContext(Dispatchers.IO) {
        check(networkAvailable()) { "Offline. Connect to use this feature." }
        val request = Request.Builder().url(url)
            .header("User-Agent", "ComicoAndroid/${BuildConfig.VERSION_NAME} (https://github.com/Wolfkid200444/comico-android)")
            .apply { if(url.toHttpUrl().host == "comico.moe" && url.toHttpUrl().encodedPath.startsWith("/api/")) header("Origin",BASE_URL) }
            .apply { if(method != "GET") method(method,if(method == "DELETE") null else (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType())) }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && url.toHttpUrl().host == "comico.moe" && url.toHttpUrl().encodedPath.startsWith("/api/auth/")) {
                val error = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                throw IOException(accountErrorMessage(error?.optString("code").orEmpty(),error?.optString("message")?.takeIf { it.isNotBlank() } ?: "Account request failed with HTTP ${response.code}. Try again."))
            }
            val accountRoute = url.toHttpUrl().host == "comico.moe" && listOf("/api/library","/api/history","/api/progress","/api/settings").any { url.toHttpUrl().encodedPath.startsWith(it) }
            if (!response.isSuccessful && accountRoute && response.code in listOf(401,403)) throw IOException("Your account session is unavailable. Sign out and sign in again.")
            if (!response.isSuccessful && url.toHttpUrl().encodedPath.startsWith("/api/comments")) {
                val error = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                throw IOException(error?.optString("statusMessage")?.takeIf { it.isNotBlank() }
                    ?: if(response.code in listOf(401,403)) "Sign in with an account allowed to comment."
                    else "Comment request failed (HTTP ${response.code}). Try again.")
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
    suspend fun downloadChapter(id: String, destination: java.io.File) = withContext(Dispatchers.IO) {
        check(networkAvailable()) { "Offline. Connect to download chapters." }
        val request = Request.Builder().url("$baseUrl/api/downloads/chapter")
            .header("Origin", BASE_URL).header("User-Agent", "ComicoAndroid/${BuildConfig.VERSION_NAME} (https://github.com/Wolfkid200444/comico-android)")
            .post(JSONObject().put("chapterId", id).toString().toRequestBody("application/json".toMediaType())).build()
        try {
            client.newBuilder().callTimeout(3, TimeUnit.MINUTES).build().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                    if (error?.optJSONObject("data")?.optString("kind") == "already-downloaded")
                        throw IOException("Comico already marks this chapter as downloaded. Import its existing ZIP to read it offline here.")
                    throw IOException(error?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: if (response.code == 401) "Sign in to download chapters." else "Download failed (HTTP ${response.code}).")
                }
                val body = response.body ?: throw IOException("The server returned an empty download.")
                destination.outputStream().use { output -> body.byteStream().use { copyChapterBytes(it, output) } }
            }
        } catch (e: Exception) { destination.delete(); throw e }
    }
    suspend fun cacheReaderImages(session: ReaderSession, archive: java.io.File) = withContext(Dispatchers.IO) {
        require(session.pages.size in 1..1000) { "Invalid chapter page count." }
        var bytes = 0L
        val downloadContext = coroutineContext
        try {
            java.util.zip.ZipOutputStream(archive.outputStream()).use { zip ->
                session.pages.forEachIndexed { index, url ->
                    downloadContext.ensureActive()
                    check(networkAvailable()) { "Offline. Chapter saving paused." }
                    val request = Request.Builder().url(url).header("Referer", "$BASE_URL/")
                        .header("User-Agent", "ComicoAndroid/${BuildConfig.VERSION_NAME}").build()
                    client.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "Couldn't cache page ${index + 1}." }
                        val body = response.body ?: error("Empty chapter image.")
                        val extension = when (body.contentType()?.subtype?.lowercase()) {
                            "jpeg", "jpg" -> "jpg"
                            "png" -> "png"
                            "webp" -> "webp"
                            "gif" -> "gif"
                            "avif" -> "avif"
                            else -> error("The source returned an unsupported image.")
                        }
                        zip.putNextEntry(java.util.zip.ZipEntry("${index.toString().padStart(4, '0')}.$extension"))
                        body.byteStream().use {
                            bytes += copyChapterBytes(it, zip, 512L * 1024 * 1024 - bytes) {
                                downloadContext.ensureActive()
                    check(networkAvailable()) { "Offline. Chapter saving paused." }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
        } catch (e: Exception) { archive.delete(); throw e }
    }
    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JSONObject {
        val url = (baseUrl + path).toHttpUrl().newBuilder().apply { query.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
        return JSONObject(requestText(url.toString()))
    }
    suspend fun post(path: String, query: Map<String, String>, body: JSONObject): JSONObject {
        val url = (baseUrl + path).toHttpUrl().newBuilder().apply { query.forEach { (k,v) -> addQueryParameter(k,v) } }.build()
        return JSONObject(requestText(url.toString(), body))
    }
    suspend fun discussion(target: DiscussionTarget): List<DiscussionComment> =
        get("/api/comments", target.query() + ("sort" to "newest")).optJSONArray("items").objects().map { it.discussionComment() }
    suspend fun postDiscussion(target: DiscussionTarget, body: String) {
        requestText("$baseUrl/api/comments", target.payload(body))
    }
    suspend fun uploadCommentImage(bytes: ByteArray, type: String): String = withContext(Dispatchers.IO) {
        val body = okhttp3.MultipartBody.Builder().setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("file", "image." + when(type) { "image/jpeg" -> "jpg"; "image/apng" -> "png"; else -> type.substringAfter("/") },
                bytes.toRequestBody(type.toMediaType())).build()
        val request = Request.Builder().url("$baseUrl/api/comments/images").header("Origin", BASE_URL)
            .header("User-Agent", "ComicoAndroid/${BuildConfig.VERSION_NAME}").post(body).build()
        client.newCall(request).execute().use { response ->
            val data = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
            if(!response.isSuccessful) throw IOException(data?.optString("statusMessage")?.takeIf { it.isNotBlank() }
                ?: "Image upload failed (HTTP ${response.code}).")
            data?.getString("url") ?: throw IOException("The server returned no image URL.")
        }
    }
    suspend fun mangaSources(id: String) = JSONArray(requestText("$baseUrl/api/manga/$id/sources")).objects().map {
        ReaderSource(it.getString("sourceId"), it.optString("name", it.getString("sourceId")), it.optInt("readableChapterCount") > 0, chapterCount = it.optInt("chapterCount"))
    }
    suspend fun readerSources(id: String) = JSONArray(requestText("$baseUrl/api/reader/chapters/$id/sources?direct=true")).objects().map {
        ReaderSource(it.getString("readerSourceId"), it.optString("name", it.getString("readerSourceId")), it.optBoolean("readable", true), it.optString("providerSourceName").takeUnless { name -> name.isBlank() || name == "null" })
    }.distinctBy { it.id }
    suspend fun catalog(query: String, offset: Int, filters: SearchFilters): PageResult<Manga> {
        val search = query.isNotBlank() || filters.tags.isNotEmpty()
        val url = (baseUrl + if(search) "/api/search" else "/api/manga").toHttpUrl().newBuilder()
        filters.query(query,offset).forEach { (key,value) -> url.addQueryParameter(key,value) }
        val data = JSONObject(requestText(url.build().toString()))
        return PageResult(data.optJSONArray("items").objects().map { it.manga() }.filter { it.rating in filters.allowedRatings() },data.optInt("total"))
    }
    suspend fun comickTags() = JSONArray(requestText("$baseUrl/api/tags?source=comick-metadata")).objects().map { ComickTag(it.getString("id"),it.getString("name")) }.sortedBy { it.name.lowercase() }
    suspend fun detail(id: String) = get("/api/manga/$id").manga()
    suspend fun chapters(id: String, language: String, offset: Int, source: String? = null, limit: Int = 50): PageResult<Chapter> {
        val data = get("/api/manga/$id/chapters", mapOf("language" to language, "limit" to limit.toString(), "offset" to offset.toString(), "order" to "desc", "sort" to "number") + (source?.let { mapOf("source" to it) } ?: emptyMap()))
        return PageResult(data.optJSONArray("items").objects().map { it.chapter() }, data.optInt("total"))
    }
    suspend fun chapterRoster(id: String, language: String, source: String?): List<Chapter> {
        val entries = linkedMapOf<String, Chapter>()
        var offset = 0
        do {
            val result = chapters(id, language, offset, source, limit = 100)
            val previousSize = entries.size
            result.items.forEach { entries[it.id] = it }
            offset += result.items.size
            if(result.items.isNotEmpty() && entries.size == previousSize) throw IOException("Chapter pages stopped advancing. Try again.")
            if(result.items.size < 100 || offset >= result.total) break
        } while(true)
        return entries.values.toList()
    }
    suspend fun library(): List<Manga> {
        val entries = mutableListOf<Manga>()
        var offset = 0
        while(true) {
            val data = get("/api/library",mapOf("limit" to "100","offset" to offset.toString(),"sort" to "recent"))
            val batch = data.getJSONArray("items").objects()
            val titles = batch.map { it.getJSONObject("manga").manga() }
            if(titles.isNotEmpty() && titles.none { title -> entries.none { it.id == title.id } })
                throw IOException("Library pagination stopped advancing. Pull down to retry.")
            entries += titles
            offset += batch.size
            if(batch.size < 100 || (data.has("total") && offset >= data.getInt("total"))) break
        }
        return entries.distinctBy { it.id }
    }
    suspend fun changeBookmark(id: String, saved: Boolean) {
        requestText("$baseUrl/api/library/$id",if(saved) JSONObject().put("status","plan_to_read") else null,if(saved) "PUT" else "DELETE")
    }
    suspend fun history() = JSONArray(requestText("$baseUrl/api/history")).objects().map { it.historyEntry() }
    suspend fun saveProgress(entry: HistoryEntry) {
        if (entry.isLocalChapter()) return
        requestText("$baseUrl/api/progress/${entry.manga.id}",entry.progressPayload())
    }
    suspend fun clearHistory() { requestText("$baseUrl/api/history",method = "DELETE") }
    suspend fun leaderboard() = JSONArray(requestText("$baseUrl/api/stats/levels?limit=50")).objects().map { it.leaderboardEntry() }
    suspend fun followedUpdates() = get("/api/library/updates",mapOf("limit" to "50")).getJSONArray("items").objects().map { it.followedUpdateManga() }.distinctBy { it.id }
    suspend fun discover(feed: DiscoverFeed, filters: SearchFilters): List<Manga> {
        val url = "$baseUrl/api/stats/titles".toHttpUrl().newBuilder().addQueryParameter("sort",feed.sort).addQueryParameter("limit","50")
        filters.allowedRatings().forEach { url.addQueryParameter("contentRating",it) }
        filters.type?.let { url.addQueryParameter("formats",it.apiValue) }
        filters.demographic?.let { url.addQueryParameter("demographic",it.apiValue) }
        return JSONArray(requestText(url.build().toString())).objects().map { it.manga() }.filter { it.rating in filters.allowedRatings() }
    }
    suspend fun discoverPage(feed: DiscoverFeed, filters: SearchFilters, offset: Int): DiscoverPage {
        val limit = 30
        val url = (baseUrl + if(feed == DiscoverFeed.UPDATES) "/api/library/updates" else "/api/stats/titles")
            .toHttpUrl().newBuilder().addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
        if(feed != DiscoverFeed.UPDATES) {
            url.addQueryParameter("sort", feed.sort)
            filters.allowedRatings().forEach { url.addQueryParameter("contentRating", it) }
            filters.type?.let { url.addQueryParameter("formats", it.apiValue) }
            filters.demographic?.let { url.addQueryParameter("demographic", it.apiValue) }
        }
        val body = requestText(url.build().toString())
        val rows = if(feed == DiscoverFeed.UPDATES) JSONObject(body).getJSONArray("items").objects()
            else JSONArray(body).objects()
        val items = rows.map { if(feed == DiscoverFeed.UPDATES) it.followedUpdateManga() else it.manga() }
            .filter { it.rating in filters.allowedRatings() }.distinctBy { it.id }
        return DiscoverPage(items, offset + rows.size, rows.size == limit)
    }
    suspend fun publicProfile(username: String): PublicProfile {
        val url = "$baseUrl/api/users".toHttpUrl().newBuilder().addPathSegment(username).build()
        val profile = JSONObject(requestText(url.toString())).publicProfile()
        return profile.copy(badgeArtwork = awardedBadgeArtwork(profile.badges, profile.supporter))
    }
    suspend fun profileSettings() = get("/api/settings")
    suspend fun saveProfile(name: String, username: String, profile: ProfileEdit) {
        val settings = profile.payload()
        requestText("$baseUrl/api/auth/update-user",JSONObject().put("name",name.trim()).put("username",username.trim()))
        requestText("$baseUrl/api/settings",settings,"PATCH")
    }
    suspend fun comments(username: String): List<AccountComment> {
        val url = "$baseUrl/api/users".toHttpUrl().newBuilder().addPathSegment(username).addPathSegment("comments").addQueryParameter("limit","50").build()
        return JSONArray(requestText(url.toString())).objects().map { it.accountComment() }
    }
    suspend fun firstChapter(id: String, language: String, source: String?): Chapter? {
        val data = get("/api/manga/$id/chapters",mapOf("language" to language,"limit" to "1","offset" to "0","order" to "asc","sort" to "number") + (source?.let { mapOf("source" to it) } ?: emptyMap()))
        return data.optJSONArray("items").objects().firstOrNull()?.chapter()
    }
    suspend fun account(): AccountUser? {
        val text = requestText("$baseUrl/api/auth/get-session?disableCookieCache=true")
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
