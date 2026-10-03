package moe.comico.reader

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class ProfileEdit(
    val image: String = "",
    val banner: String = "",
    val bio: String = "",
    val links: List<String> = emptyList()
)

fun JSONObject.profileEdit() = ProfileEdit(
    optString("image").takeUnless { it == "null" }.orEmpty(),
    optString("banner").takeUnless { it == "null" }.orEmpty(),
    optString("bio").takeUnless { it == "null" }.orEmpty(),
    optJSONArray("links")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
)

fun validProfileUrl(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    uri.scheme?.lowercase() in listOf("https", "http") &&
        !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
        value.trim().none { it.isWhitespace() }
}.getOrDefault(false)

fun ProfileEdit.validationError(): String? = when {
    image.isNotBlank() && !validProfileUrl(image) -> "Enter a valid avatar URL starting with https:// or http://."
    banner.isNotBlank() && !validProfileUrl(banner) -> "Enter a valid banner URL starting with https:// or http://."
    bio.length > 500 -> "Keep your bio within 500 characters."
    links.count { it.isNotBlank() } > 5 -> "You can add up to 5 social links."
    links.any { it.isNotBlank() && (!validProfileUrl(it) || it.trim().length > 200) } ->
        "Each social link must be a valid http:// or https:// URL, up to 200 characters."
    else -> null
}

fun ProfileEdit.payload(): JSONObject {
    require(validationError() == null) { validationError().orEmpty() }
    return JSONObject()
        .put("image", image.trim().ifBlank { null } ?: JSONObject.NULL)
        .put("banner", banner.trim().ifBlank { null } ?: JSONObject.NULL)
        .put("bio", bio.trim().ifBlank { null } ?: JSONObject.NULL)
        // The website sends links as newline-separated text.
        .put("links", links.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n"))
}
