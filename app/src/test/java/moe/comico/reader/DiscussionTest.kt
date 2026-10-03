package moe.comico.reader
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class DiscussionTest {
    @Test fun targetsKeepMangaAndChapterSeparate() {
        val manga = DiscussionTarget("manga", "Title")
        val chapter = DiscussionTarget("chapter", "Chapter 1", true)
        assertEquals(mapOf("mangaId" to "manga"), manga.query())
        assertEquals(mapOf("chapterId" to "chapter"), chapter.query())
        assertFalse(chapter.payload(" hello ").has("mangaId"))
        assertEquals("hello", chapter.payload(" hello ").getString("body"))
    }
    @Test fun imagesResolveOnlyHttpsAndComicoUploadPaths() {
        assertEquals("$BASE_URL/api/comment-images/test.png", commentImageUrl("/api/comment-images/test.png"))
        assertNotNull(commentImageUrl("https://example.com/image.png"))
        assertNull(commentImageUrl("http://example.com/image.png"))
        assertNull(commentImageUrl("javascript:alert(1)"))
        assertNull(commentImageUrl("/unrelated/image.png"))
        assertNotNull(commentValidation("![image](http://example.com/a.png)"))
        assertNull(commentValidation("![image](/api/comment-images/test.png)"))
        assertNotNull(commentValidation("x".repeat(5001)))
    }
    @Test fun threadParsesAuthorsAndChapterReplies() {
        val obj = JSONObject("""{"id":"1","body":"Hello","createdAt":"2026-10-02T00:00:00Z","author":{"name":"Wolfie"},"replies":[{"id":"2","body":"Reply","author":{"username":"reader"}}]}""")
        val comment = obj.discussionComment()
        assertEquals("Wolfie", comment.author)
        assertEquals("reader", comment.replies.single().author)
        assertEquals("Reply", comment.replies.single().body)
    }
}
