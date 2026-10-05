package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApiModelsTest {
    @Test fun latestChapterDateAndNumberSurviveCaching() {
        val manga = JSONObject("""{"id":"id","title":"Story","updatedAt":"2026-01-01T00:00:00Z","latestChapter":{"number":"12.5","publishedAt":"2026-10-04T10:00:00Z"}}""").manga()
        assertEquals("2026-10-04T10:00:00Z", manga.updatedAt)
        assertEquals("12.5", manga.latestChapterNumber)
        assertEquals(manga, JSONObject(manga.toJson().toString()).manga())
        val missing = JSONObject("""{"id":"id","title":"Story","latestChapter":{"number":null,"publishedAt":null}}""").manga()
        assertEquals("", missing.updatedAt)
        assertEquals("", missing.latestChapterNumber)
    }
    @Test fun relativeCoverAndOptionalMetadata() {
        val manga = JSONObject("""{"id":"id","title":"A story","coverUrl":"/api/covers/a.jpg","tags":[{"name":"Adventure"}]}""").manga()
        assertEquals("https://comico.moe/api/covers/a.jpg", manga.cover)
        assertEquals(listOf("Adventure"), manga.tags)
        assertEquals("", manga.description)
        assertEquals("safe", manga.rating)
    }
    @Test fun authorAndArtistCreditsSurviveSavedTitleSerialization() {
        val manga = JSONObject("""{"id":"id","title":"Story","authors":[{"name":"Writer","role":"author"},{"name":"Illustrator","role":"artist"}]}""").manga()
        assertEquals(listOf(MangaCredit("Writer", "author"), MangaCredit("Illustrator", "artist")), manga.credits)
        assertEquals(manga, JSONObject(manga.toJson().toString()).manga())
        assertTrue(JSONObject("""{"id":"id","title":"Story"}""").manga().credits.isEmpty())
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
