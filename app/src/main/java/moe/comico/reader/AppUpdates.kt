package moe.comico.reader

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

const val UPDATE_REPOSITORY = "Wolfkid200444/comico-android"
const val UPDATE_CHECK_INTERVAL = 24 * 60 * 60 * 1000L
data class AppRelease(val version: String, val apkUrl: String, val pageUrl: String,
    val notes: String = "", val size: Long = 0, val sha256: String? = null)
fun releaseNotes(notes: String) = notes.trim().takeIf { it.isNotEmpty() && it != "null" }
    ?: "No changelog was provided for this release."
data class UpdateState(val automatic: Boolean = true, val checking: Boolean = false,
    val release: AppRelease? = null, val prompt: Boolean = false, val message: String? = null,
    val downloading: Boolean = false, val progress: Float? = null, val downloaded: java.io.File? = null, val error: String? = null)
fun versionParts(value: String): List<Int>? {
    val match = Regex("v?([0-9]+)\\.([0-9]+)\\.([0-9]+)").matchEntire(value) ?: return null
    return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
}
fun newerVersion(candidate: String, installed: String): Boolean {
    val next = versionParts(candidate) ?: return false
    val current = versionParts(installed) ?: return false
    for(i in 0..2) if(next[i] != current[i]) return next[i] > current[i]
    return false
}
fun JSONObject.appRelease(installed: String): AppRelease? {
    if(optBoolean("draft") || optBoolean("prerelease")) return null
    val tag = optString("tag_name")
    if(!newerVersion(tag, installed)) return null
    val version = tag.removePrefix("v")
    val asset = optJSONArray("assets").objects().firstOrNull {
        it.optString("name") == "comico-android-$version.apk" && it.optString("state") == "uploaded"
    } ?: return null
    val download = asset.optString("browser_download_url").toHttpUrlOrNull() ?: return null
    val page = optString("html_url").toHttpUrlOrNull() ?: return null
    if(download.scheme != "https" || download.host != "github.com" ||
        !download.encodedPath.startsWith("/$UPDATE_REPOSITORY/releases/download/") ||
        page.scheme != "https" || page.host != "github.com" ||
        !page.encodedPath.startsWith("/$UPDATE_REPOSITORY/releases/tag/")) return null
    val digest = asset.optString("digest").takeIf { it.isNotBlank() && it != "null" }
    if(digest != null && !Regex("sha256:[a-fA-F0-9]{64}").matches(digest)) return null
    return AppRelease(version, download.toString(), page.toString(), optString("body").takeUnless { it == "null" }.orEmpty(),
        asset.optLong("size"), digest?.substringAfter(":"))
}
class AppUpdateController(private val app: Application, private val scope: CoroutineScope) {
    private val prefs = app.getSharedPreferences("appUpdates", 0)
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
    private val mutable = MutableStateFlow(UpdateState(automatic = prefs.getBoolean("automatic", true)))
    val state = mutable.asStateFlow()
    private var lastAttempt = 0L
    fun automatic(enabled: Boolean) {
        prefs.edit().putBoolean("automatic", enabled).apply()
        mutable.update { it.copy(automatic = enabled) }
        if(enabled) check()
    }
    private var downloadJob: Job? = null
    fun dismiss() {
        downloadJob?.cancel()
        mutable.update { it.copy(prompt = false, downloading = false) }
    }
    fun showPrompt() { mutable.update { it.copy(prompt = it.release != null) } }
    fun installError(message: String) { mutable.update { it.copy(error = message) } }
    fun download() {
        val release = mutable.value.release ?: return
        if(mutable.value.downloading) return
        mutable.update { it.copy(downloading = true, progress = null, downloaded = null, error = null) }
        downloadJob = scope.launch {
            try {
                val file = downloadUpdate(app, release) { progress -> mutable.update { it.copy(progress = progress) } }
                mutable.update { it.copy(downloading = false, downloaded = file, progress = 1f) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(downloading = false, error = e.message ?: "Download failed. Try again.") } }
        }
    }
    fun check(manual: Boolean = false) {
        if(mutable.value.checking || mutable.value.downloading || (!manual && !mutable.value.automatic)) return
        val now = System.currentTimeMillis()
        val last = maxOf(lastAttempt, prefs.getLong("lastSuccess", 0))
        if(!manual && now >= last && now - last < UPDATE_CHECK_INTERVAL) return
        lastAttempt = now
        mutable.update { it.copy(checking = true, message = null) }
        scope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    val request = Request.Builder().url("https://api.github.com/repos/$UPDATE_REPOSITORY/releases/latest")
                        .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2026-03-10")
                        .header("User-Agent", "ComicoAndroid/${BuildConfig.VERSION_NAME}").build()
                    client.newCall(request).execute().use { response ->
                        if(response.code == 404) return@use null
                        if(!response.isSuccessful) throw IOException(if(response.code in listOf(403,429))
                            "GitHub is limiting update checks. Try again later." else "Could not check updates (HTTP ${response.code}).")
                        JSONObject(response.body?.string() ?: throw IOException("Empty update response.")).appRelease(BuildConfig.VERSION_NAME)
                    }
                }
                prefs.edit().putLong("lastSuccess", System.currentTimeMillis()).apply()
                mutable.update { it.copy(checking = false, release = release, prompt = release != null,
                    message = if(release != null) "Version ${release.version} is available." else "You're up to date.") }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(checking = false, message = if(manual) e.message ?: "Could not check updates. Check your connection." else null) } }
        }
    }
}
