package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentContextTest {
    @Test fun readsWebsiteCommentContext() {
        val comment = JSONObject("""{"id":"c","body":"Hello","context":"A story · Ch.12","coverUrl":"/cover.jpg","href":"/reader/chapter"}""").accountComment()
        assertEquals("A story · Ch.12", comment.context)
        assertEquals("$BASE_URL/cover.jpg", comment.coverUrl)
    }
    @Test fun fallsBackToNestedMangaAndChapter() {
        val comment = JSONObject("""{"id":"c","manga":{"title":"A story"},"chapter":{"number":"1.5"},"coverUrl":null}""").accountComment()
        assertEquals("A story · Chapter 1.5", comment.context)
        assertEquals("", comment.coverUrl)
    }
}
