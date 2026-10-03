package moe.comico.reader

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException

data class DiscussionTarget(val id: String, val title: String, val chapter: Boolean = false) {
    fun query() = mapOf((if(chapter) "chapterId" else "mangaId") to id)
    fun payload(body: String) = JSONObject(query()).put("body", body.trim())
}
data class DiscussionComment(val id: String, val body: String, val author: String, val createdAt: String, val replies: List<DiscussionComment> = emptyList())
fun JSONObject.discussionComment(): DiscussionComment {
    val author = optJSONObject("author")
    return DiscussionComment(getString("id"), if(optBoolean("deleted")) "Comment deleted" else optString("body"),
        author?.optString("name")?.takeIf { it.isNotBlank() } ?: author?.optString("username").orEmpty(),
        optString("createdAt"), optJSONArray("replies").objects().map { it.discussionComment() })
}
data class DiscussionState(val target: DiscussionTarget? = null, val comments: List<DiscussionComment> = emptyList(),
    val draft: String = "", val loading: Boolean = false, val posting: Boolean = false, val uploading: Boolean = false, val error: String? = null)
const val COMMENT_LIMIT = 5000
const val COMMENT_IMAGE_LIMIT = 10 * 1024 * 1024
fun commentImageUrl(value: String): String? {
    val url = if(value.startsWith("/api/comment-images/")) BASE_URL + value else value
    return runCatching { url.toHttpUrl().takeIf { it.scheme == "https" }?.toString() }.getOrNull()
}
val commentImagePattern = Regex("!\\[([^\\]]*)\\]\\(([^\\s)]+)\\)")
fun commentValidation(body: String): String? = when {
    body.isBlank() -> "Write a comment first."
    body.length > COMMENT_LIMIT -> "Comments can contain up to 5,000 characters."
    commentImagePattern.findAll(body).any { commentImageUrl(it.groupValues[2]) == null } -> "Images must use HTTPS URLs."
    else -> null
}
class DiscussionController(private val api: ComicoApi, private val scope: CoroutineScope, private val app: Application) {
    private val mutable = MutableStateFlow(DiscussionState())
    val state = mutable.asStateFlow()
    private var loadJob: Job? = null
    private var actionJob: Job? = null
    fun open(target: DiscussionTarget) {
        loadJob?.cancel(); actionJob?.cancel()
        mutable.value = DiscussionState(target = target)
        refresh()
    }
    fun close() {
        loadJob?.cancel(); actionJob?.cancel()
        mutable.value = DiscussionState()
    }
    fun draft(value: String) { mutable.update { it.copy(draft = value, error = null) } }
    fun refresh() {
        val target = mutable.value.target ?: return
        loadJob?.cancel()
        loadJob = scope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val result = api.discussion(target)
                mutable.update { if(it.target == target) it.copy(comments = result, loading = false) else it }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(loading = false, error = e.message ?: "Could not load comments.") } }
        }
    }
    fun post() {
        val before = mutable.value
        val target = before.target ?: return
        if(before.posting || before.uploading) return
        commentValidation(before.draft)?.let { error -> mutable.update { it.copy(error = error) }; return }
        actionJob = scope.launch {
            mutable.update { it.copy(posting = true, error = null) }
            try {
                api.postDiscussion(target, before.draft)
                mutable.update { it.copy(posting = false, draft = "") }
                refresh()
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(posting = false, error = e.message ?: "Could not post comment.") } }
        }
    }
    fun upload(uri: Uri) {
        if(mutable.value.uploading || mutable.value.posting || mutable.value.target == null) return
        actionJob = scope.launch {
            mutable.update { it.copy(uploading = true, error = null) }
            try {
                val (bytes, type) = withContext(Dispatchers.IO) {
                    val mime = app.contentResolver.getType(uri) ?: throw IOException("Could not identify this image.")
                    require(mime in listOf("image/png", "image/apng", "image/webp", "image/avif", "image/jpeg", "image/gif")) { "Choose a PNG, JPEG, WebP, AVIF or GIF image." }
                    val data = app.contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while(true) {
                            val count = input.read(buffer)
                            if(count < 0) break
                            require(out.size() + count <= COMMENT_IMAGE_LIMIT) { "Choose an image smaller than 10 MB." }
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    } ?: throw IOException("Could not open this image.")
                    data to mime
                }
                val url = api.uploadCommentImage(bytes, type)
                require(commentImageUrl(url) != null) { "The server returned an invalid image URL." }
                mutable.update { it.copy(uploading = false, draft = it.draft.trimEnd() + "\n![image]($url)\n") }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(uploading = false, error = e.message ?: "Image upload failed.") } }
        }
    }
}
