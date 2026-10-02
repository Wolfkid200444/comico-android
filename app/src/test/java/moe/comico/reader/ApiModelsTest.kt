package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiModelsTest {
    @Test fun relativeCoverAndOptionalMetadata() {
        val manga = JSONObject("""{"id":"id","title":"A story","coverUrl":"/api/covers/a.jpg","tags":[{"name":"Adventure"}]}""").manga()
        assertEquals("https://comico.moe/api/covers/a.jpg", manga.cover)
        assertEquals(listOf("Adventure"), manga.tags)
        assertEquals("", manga.description)
        assertEquals("safe", manga.rating)
    }
    @Test fun nullExternalLinkStaysEmpty() {
        val chapter = JSONObject("""{"id":"id","number":"1.5","externalUrl":null}""").chapter()
        assertEquals("", chapter.external)
        assertEquals("1.5", chapter.number)
    }
    @Test fun publisherChapterKeepsSourceLink() {
        val chapter = JSONObject("""{"id":"id","externalUrl":"https://mangaplus.shueisha.co.jp/viewer/1030381"}""").chapter()
        assertTrue(chapter.external.startsWith("https://mangaplus.shueisha.co.jp/"))
    }
}
