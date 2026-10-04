package moe.comico.reader

import org.json.JSONObject

enum class LibrarySort(val label: String) {
    TITLE("Alphabetically"), SAVED("Library order"), READ("Last read"), CHAPTER("Chapter progress")
}
enum class LibraryView(val label: String) { GRID("Comfortable grid"), COMPACT("Compact grid"), COVER("Cover-only grid"), LIST("List") }
enum class LibraryProgress(val label: String) { ALL("Any progress"), UNREAD("Unread"), STARTED("Started reading") }

data class LibraryOptions(
    val sort: LibrarySort = LibrarySort.TITLE,
    val descending: Boolean = false,
    val view: LibraryView = LibraryView.GRID,
    val format: String? = null,
    val status: String? = null,
    val progress: LibraryProgress = LibraryProgress.ALL,
    val collection: String? = null,
    val columns: Int = 0,
    val showProgress: Boolean = true,
    val showLanguage: Boolean = false,
    val showContinue: Boolean = false,
    val showTabs: Boolean = true,
    val showCounts: Boolean = false,
    val downloadedOnly: Boolean = false,
    val showDownloads: Boolean = false
) {
    val filterCount get() = listOf(format, status).count { it != null } + (if (progress != LibraryProgress.ALL) 1 else 0) + (if (downloadedOnly) 1 else 0)
    fun toJson() = JSONObject().put("sort", sort.name).put("descending", descending).put("view", view.name)
        .put("format", format ?: JSONObject.NULL).put("status", status ?: JSONObject.NULL)
        .put("progress", progress.name).put("collection", collection ?: JSONObject.NULL)
        .put("columns", columns).put("showProgress", showProgress).put("showLanguage", showLanguage)
        .put("showContinue", showContinue).put("showTabs", showTabs).put("showCounts", showCounts)
        .put("downloadedOnly", downloadedOnly).put("showDownloads", showDownloads)
}
fun JSONObject.libraryOptions(): LibraryOptions {
    fun text(key: String) = optString(key).takeUnless { it.isBlank() || it == "null" }
    return LibraryOptions(
        LibrarySort.entries.firstOrNull { it.name == text("sort") } ?: LibrarySort.TITLE,
        optBoolean("descending"),
        LibraryView.entries.firstOrNull { it.name == text("view") } ?: LibraryView.GRID,
        text("format"), text("status"),
        LibraryProgress.entries.firstOrNull { it.name == text("progress") } ?: LibraryProgress.ALL,
        text("collection"), optInt("columns").coerceIn(0, 6), optBoolean("showProgress", true),
        optBoolean("showLanguage"), optBoolean("showContinue"), optBoolean("showTabs", true), optBoolean("showCounts"), optBoolean("downloadedOnly"), optBoolean("showDownloads")
    )
}

fun visibleLibrary(state: AppState, collectionIds: Set<String>? = null): List<Manga> {
    val options = state.libraryOptions
    val readTimes = state.history.groupBy { it.manga.id }.mapValues { (_, entries) -> entries.maxOf { it.readAt } }
    val items = filterLibrary(state.library, state.libraryQuery).filter {
        (!options.downloadedOnly || state.offlineChapters.any { saved -> saved.manga.id == it.id }) &&
        (collectionIds == null || it.id in collectionIds) &&
            (options.format == null || it.format.equals(options.format, true)) &&
            (options.status == null || it.status.equals(options.status, true)) &&
            when (options.progress) {
                LibraryProgress.ALL -> true
                LibraryProgress.UNREAD -> it.id !in state.progress && it.id !in readTimes
                LibraryProgress.STARTED -> it.id in state.progress || it.id in readTimes
            }
    }
    val sorted = when (options.sort) {
        LibrarySort.TITLE -> items.sortedWith(compareBy<Manga> { it.title.lowercase() }.thenBy { it.id })
        LibrarySort.SAVED -> items
        LibrarySort.READ -> items.sortedWith(compareBy<Manga> { readTimes[it.id].orEmpty() }.thenBy { it.title.lowercase() })
        LibrarySort.CHAPTER -> items.sortedWith(compareBy<Manga> { state.progress[it.id]?.number?.toDoubleOrNull() ?: -1.0 }.thenBy { it.title.lowercase() })
    }
    return if (options.descending) sorted.reversed() else sorted
}
