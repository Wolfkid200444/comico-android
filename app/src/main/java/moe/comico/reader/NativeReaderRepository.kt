package moe.comico.reader

import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URI

/** Resolves the same authenticated read-session responses used by Comico's reader. */
class NativeReaderRepository(private val api: ComicoApi) {
    suspend fun open(chapterId: String, source: ReaderSource?, method: ProxyMethod): ReaderSession {
        val token = api.get("/api/reader/access/$chapterId").getString("token")
        val methods = if(method == ProxyMethod.AUTO) listOf(ProxyMethod.METHOD_3, ProxyMethod.METHOD_1, ProxyMethod.METHOD_2) else listOf(method)
        var lastFailure: Exception? = null
        for(candidate in methods) {
            try {
                val body = JSONObject().put("proxyMethod",candidate.apiValue)
                source?.let { body.put("readerSourceId",it.id); it.provider?.let { name -> body.put("providerSourceName",name) } }
                val data = api.post("/api/reader/chapters/$chapterId/open",mapOf("d" to token),body)
                if(data.has("browserSources")) return resolveDirect(data, candidate)
                val pages = data.optJSONArray("pages")?.let { pages -> (0 until pages.length()).map { absoluteUrl(pages.getString(it)) } }.orEmpty()
                if(pages.isEmpty()) throw IOException("This source returned no chapter images.")
                if(data.optJSONArray("scrambled")?.let { flags -> (0 until flags.length()).any { flags.optBoolean(it) } } == true) {
                    throw IOException("This source uses a protected image format that the native reader does not support yet. Choose another source.")
                }
                return ReaderSession(pages, data.optString("providerSourceName").takeUnless { it.isBlank() || it == "null" } ?: data.optString("readerSourceId"),data.optString("attribution"),candidate,data.optString("expiresAt"))
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { lastFailure = e }
        }
        throw lastFailure ?: IOException("No readable source was found for this chapter.")
    }

    private suspend fun resolveDirect(data: JSONObject, method: ProxyMethod): ReaderSession {
        var failure: Exception? = null
        for(source in data.optJSONArray("browserSources").objects()) {
            try {
                val pages = directPages(source)
                if(pages.isEmpty() || pages.size > 1000) throw IOException("The source returned no usable images.")
                val hosts = source.getJSONArray("imageHosts").let { array -> (0 until array.length()).map { array.getString(it) } }
                val validated = validatePageUrls(pages,hosts)
                return ReaderSession(validated,source.optString("providerSourceName",source.getString("readerSourceId")),source.optString("attribution"),method,null)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { failure = e }
        }
        throw failure ?: IOException("This source needs Method 1 or Method 2 to load images.")
    }

    private suspend fun directPages(source: JSONObject): List<String> {
        val id = source.getString("readerSourceId")
        val external = source.getString("externalChapterId")
        return when(id) {
            "mangadex" -> {
                require(Regex("[0-9a-fA-F-]{36}").matches(external))
                val data = JSONObject(api.requestText("https://api.mangadex.org/at-home/server/$external"))
                val chapter = data.getJSONObject("chapter")
                chapter.getJSONArray("data").strings().map { "${data.getString("baseUrl")}/data/${chapter.getString("hash")}/$it" }
            }
            "atsu" -> {
                val ids = external.split(':'); require(ids.size == 2)
                val url = "https://atsu.moe/api/read/chapter".toHttpUrl().newBuilder().addQueryParameter("mangaId",ids[0]).addQueryParameter("chapterId",ids[1]).build()
                JSONObject(api.requestText(url.toString())).getJSONObject("readChapter").getJSONArray("pages").objects().sortedBy { it.optInt("number") }.map {
                    val image = it.getString("image")
                    if(image.startsWith("https://")) image else {
                        val path = image.trimStart('/')
                        "https://cdn.atsu.moe/${if(path.startsWith("static/")) path else "static/$path"}"
                    }
                }
            }
            "comicklive" -> {
                require(Regex("/comic/[\\w-]+/[\\w-]+").matches(external))
                val html = api.requestText("https://comick.live$external")
                val json = Regex("<script[^>]*id=[\"']sv-data[\"'][^>]*>([\\s\\S]*?)</script>").find(html)?.groupValues?.get(1) ?: throw IOException("ComickLive didn't return chapter image data. Try another proxy method.")
                JSONObject(json).getJSONObject("chapter").getJSONArray("images").objects().map { it.getString("url") }
            }
            "nato" -> {
                val ids = external.split(':');require(ids.size == 2 && ids.all { Regex("[\\w-]+").matches(it) })
                val html = api.requestText("https://www.manganato.art/manga/${ids[0]}/${ids[1]}")
                val images = Regex("var\\s+chapterImages\\s*=\\s*(\\[[\\s\\S]*?\\])\\s*;").find(html)?.groupValues?.get(1) ?: throw IOException("Manganato returned no images.")
                val cdns = Regex("var\\s+cdns\\s*=\\s*(\\[[\\s\\S]*?\\])\\s*;").find(html)?.groupValues?.get(1)
                val cdn = cdns?.let { JSONArray(it).optString(0) }.orEmpty()
                JSONArray(images).strings().filter { it.isNotBlank() }.map { if(cdn.isBlank()) it else "${cdn.trimEnd('/')}/${it.trimStart('/')}" }
            }
            "mangaball" -> {
                require(Regex("[a-f0-9]{24}(?:-[a-z]+)?").matches(external))
                val html = api.requestText("https://mangaball.net/chapter-detail/${external.substringBefore('-')}/")
                val json = Regex("const\\s+chapterImages\\s*=\\s*JSON\\.parse\\(`([^`]+)`\\)").find(html)?.groupValues?.get(1) ?: throw IOException("MangaBall returned no images.")
                JSONArray(json).strings()
            }
            "xcomic" -> {
                require(Regex("[a-z0-9]{4,8}").matches(external))
                val body = JSONObject().put("query","query(\$id: ID!) { get_chapterNode(id: \$id) { id data { imageUrls } } }").put("variables",JSONObject().put("id",external))
                JSONObject(api.requestText("https://xcomic.me/query/",body)).getJSONObject("data").getJSONObject("get_chapterNode").getJSONObject("data").getJSONArray("imageUrls").strings().map { URI("https://xcomic.me").resolve(it).toString() }
            }
            else -> throw IOException("${source.optString("providerSourceName",id)} requires Method 1 or Method 2.")
        }
    }
}

fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
fun validatePageUrls(pages: List<String>, allowedHosts: List<String>): List<String> = pages.map { value ->
    val uri = URI(value)
    val host = uri.host?.lowercase() ?: throw IOException("The source returned an invalid image address.")
    if(uri.scheme != "https" || uri.userInfo != null || allowedHosts.none { host == it.lowercase() || host.endsWith(".${it.lowercase()}") }) throw IOException("The source returned an image outside its approved hosts.")
    value
}
