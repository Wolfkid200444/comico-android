package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SearchFiltersTest {
    @Test fun ratingsAreSentAsRepeatedParameters() {
        val query = SearchFilters(content = ContentFilter.SUGGESTIVE).query("one piece",0)
        assertEquals(listOf("safe","suggestive"),query.filter { it.first == "contentRating" }.map { it.second })
        assertFalse(query.any { it.second == "safe,suggestive" })
    }
    @Test fun combinesEveryFilterWithComickSearch() {
        val filters = SearchFilters(SearchSort.POPULAR,ContentFilter.SAFE,MangaType.MANGA,Demographic.SHOUNEN,ReleaseStatus.ONGOING,listOf("friendship","worldtravel"))
        val query = filters.query("one piece",30)
        assertTrue(query.contains("source" to "comick"))
        assertTrue(query.contains("format" to "manga"))
        assertTrue(query.contains("demographic" to "shounen"))
        assertTrue(query.contains("status" to "ongoing"))
        assertTrue(query.contains("sort" to "popular"))
        assertTrue(query.contains("offset" to "30"))
        assertEquals(listOf("friendship","worldtravel"),query.filter { it.first == "comickTags" }.map { it.second })
    }
    @Test fun tagsCanSearchWithoutATitle() {
        val query = SearchFilters(tags = listOf("friendship")).query("",0)
        assertTrue(query.contains("source" to "comick"))
        assertFalse(query.any { it.first == "q" })
    }
    @Test fun unfilteredDiscoveryUsesLatestAndSafeOnly() {
        val query = SearchFilters().query("",0)
        assertTrue(query.contains("sort" to "latest"))
        assertFalse(query.any { it.first == "source" })
        assertEquals(listOf("safe"),query.filter { it.first == "contentRating" }.map { it.second })
    }
    @Test fun selectionsSurviveRestartAndTagLimitIsEnforced() {
        val filters = SearchFilters(SearchSort.TITLE,ContentFilter.SUGGESTIVE,MangaType.MANHWA,Demographic.JOSEI,ReleaseStatus.COMPLETED,listOf("friendship"))
        assertEquals(filters,JSONObject(filters.toJson().toString()).searchFilters())
        assertEquals(20,SearchFilters(tags = (1..25).map { "tag$it" }).query("",0).count { it.first == "comickTags" })
    }
}
