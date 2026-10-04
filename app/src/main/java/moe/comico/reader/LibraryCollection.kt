package moe.comico.reader

import org.json.JSONArray
import org.json.JSONObject

data class LibraryCollection(val id: String, val name: String, val mangaIds: Set<String> = emptySet()) {
    fun toJson() = JSONObject().put("id", id).put("name", name).put("mangaIds", JSONArray(mangaIds.toList()))
}
fun JSONObject.libraryCollection(): LibraryCollection {
    val ids = optJSONArray("mangaIds") ?: JSONArray()
    return LibraryCollection(getString("id"), getString("name"), (0 until ids.length()).map { ids.getString(it) }.toSet())
}
fun moveLibraryCollection(collections: List<LibraryCollection>, id: String, direction: Int): List<LibraryCollection> {
    val from = collections.indexOfFirst { it.id == id }
    val to = from + direction.coerceIn(-1, 1)
    if (from < 0 || to !in collections.indices || from == to) return collections
    return collections.toMutableList().apply { add(to, removeAt(from)) }
}

fun collectionNameError(name: String, collections: List<LibraryCollection>, editingId: String? = null): String? = when {
    name.trim().isEmpty() -> "Enter a collection name."
    name.trim().length > 60 -> "Use 60 characters or fewer."
    collections.any { it.id != editingId && it.name.equals(name.trim(), true) } -> "A collection with that name already exists."
    else -> null
}
