package moe.comico.reader

fun chapterDownloadQueue(chapters: List<Chapter>, saved: List<OfflineChapter>): List<Chapter> =
    chapters.distinctBy { it.id }.filter { chapter -> chapter.external.isBlank() && saved.none { it.chapter.id == chapter.id } }

fun AppState.chapterRead(chapter: Chapter): Boolean = chapterReadMarks[chapter.id]
    ?: history.any { it.chapter.id == chapter.id && it.pageCount > 0 && it.page >= it.pageCount - 1 }

fun chapterSubtitle(chapter: Chapter): String? = chapter.title.trim().takeIf {
    it.isNotEmpty() && !it.equals("Chapter ${chapter.number}", ignoreCase = true)
}

fun discussionCommentCount(comments: List<DiscussionComment>): Int =
    comments.sumOf { 1 + discussionCommentCount(it.replies) }

// -1 means comments exist, but the response doesn't provide a total.
fun commentIndicator(response: org.json.JSONObject): Int =
    if (response.has("total") && !response.isNull("total")) response.optInt("total").coerceAtLeast(0)
    else if ((response.optJSONArray("items")?.length() ?: 0) > 0) -1 else 0
