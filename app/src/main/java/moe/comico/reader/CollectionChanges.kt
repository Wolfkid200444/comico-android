package moe.comico.reader

import org.json.JSONArray
import org.json.JSONObject

// Local change records are separate from the server's request payload.
data class CollectionChange(val id: String, val name: String? = null,
    val added: Set<String> = emptySet(), val removed: Set<String> = emptySet(), val deleted: Boolean = false, val remoteId: String? = null) {
    fun toJson() = JSONObject().put("id", id).put("name", name ?: JSONObject.NULL)
        .put("added", JSONArray(added.toList())).put("removed", JSONArray(removed.toList())).put("deleted", deleted).put("remoteId", remoteId ?: JSONObject.NULL)
}
fun JSONObject.collectionChange(): CollectionChange {
    fun ids(key: String) = optJSONArray(key)?.let { array ->
        (0 until array.length()).map { array.getString(it) }.toSet()
    } ?: emptySet()
    return CollectionChange(getString("id"), optString("name").takeUnless { it.isBlank() || it == "null" },
        ids("added"), ids("removed"), optBoolean("deleted"), optString("remoteId").takeUnless { it.isBlank() || it == "null" })
}
fun queueCollectionChanges(before: List<LibraryCollection>, after: List<LibraryCollection>,
    pending: Map<String, CollectionChange>): Map<String, CollectionChange> {
    val result = pending.toMutableMap()
    val previous = before.associateBy { it.id }
    val current = after.associateBy { it.id }
    (previous.keys + current.keys).forEach { id ->
        val old = previous[id]
        val new = current[id]
        if (old == new) return@forEach
        if (new == null) {
            result[id] = CollectionChange(id, deleted = true, remoteId = old?.remoteId ?: result[id]?.remoteId)
        } else {
            val change = result[id]?.takeUnless { it.deleted } ?: CollectionChange(id)
            val added = new.mangaIds - old?.mangaIds.orEmpty()
            val removed = old?.mangaIds.orEmpty() - new.mangaIds
            result[id] = change.copy(name = if (old?.name != new.name) new.name else change.name,
                added = (change.added + added) - removed,
                removed = (change.removed + removed) - added, deleted = false, remoteId = new.remoteId ?: change.remoteId)
        }
    }
    return result
}
fun mergeGuestCollections(account: List<LibraryCollection>, guest: List<LibraryCollection>): List<LibraryCollection> {
    val merged = account.toMutableList()
    guest.forEach { collection ->
        val existing = merged.indexOfFirst { it.id == collection.id || it.name.equals(collection.name, true) }
        if (existing < 0) merged += collection.copy(remoteId = null)
        else merged[existing] = merged[existing].copy(mangaIds = merged[existing].mangaIds + collection.mangaIds)
    }
    return merged
}
fun applyCollectionChange(remote: LibraryCollection, change: CollectionChange): LibraryCollection? =
    if (change.deleted) null else remote.copy(name = change.name ?: remote.name,
        mangaIds = (remote.mangaIds + change.added) - change.removed)
