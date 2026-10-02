package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ReaderPreferencesTest {
    @Test fun inheritsEachGlobalSettingIndependently() {
        val global = ReaderPreferences(mode = ReadingMode.DOUBLE, width = PageWidth.NARROW, proxy = ProxyMethod.METHOD_2, source = "atsu", direction = ReadingDirection.RTL)
        val resolved = ReaderOverride(width = PageWidth.FULL).resolve(global)
        assertEquals(ReadingMode.DOUBLE,resolved.mode)
        assertEquals(PageWidth.FULL,resolved.width)
        assertEquals("atsu",resolved.source)
        assertEquals(ProxyMethod.METHOD_2,resolved.proxy)
        assertEquals(ReadingDirection.RTL,resolved.direction)
    }
    @Test fun explicitAutoSourceOverridesGlobalPreference() {
        val global = ReaderPreferences(source = "mangadex")
        assertNull(ReaderOverride(sourceOverride = true).resolve(global).source)
        assertEquals("mangadex",ReaderOverride().resolve(global).source)
    }
    @Test fun automaticModeUsesMangaFormat() {
        val settings = ReaderPreferences()
        assertEquals(ReadingMode.SINGLE,settings.modeFor("manga"))
        assertEquals(ReadingMode.STRIP,settings.modeFor("webtoon"))
        assertEquals(ReadingMode.STRIP,settings.modeFor("manhwa"))
        assertEquals(ReadingMode.DOUBLE,settings.copy(mode = ReadingMode.DOUBLE).modeFor("webtoon"))
    }
    @Test fun preferencesAndOverridesSurviveSerialization() {
        val global = ReaderPreferences(ReadingMode.DOUBLE,PageWidth.COMFORT,ProxyMethod.METHOD_3,"comicklive",ReadingDirection.RTL,ReaderNavigation.VERTICAL)
        assertEquals(global,JSONObject(global.toJson().toString()).readerPreferences())
        val override = ReaderOverride(proxy = ProxyMethod.METHOD_1,sourceOverride = true,navigation = ReaderNavigation.VERTICAL)
        assertEquals(override,JSONObject(override.toJson().toString()).readerOverride())
    }
    @Test fun oldOrUnknownPreferencesHaveSafeDefaults() {
        assertEquals(ReaderPreferences(),JSONObject("""{"mode":"unknown","width":"unknown"}""").readerPreferences())
        assertTrue(JSONObject("{}").readerOverride().isEmpty())
    }
    @Test fun oddDoublePageSpreadsNeverIncludeInvalidIndex() {
        assertEquals(listOf(4),spreadPages(5,4,true,ReadingDirection.LTR))
        assertEquals(listOf(1,0),spreadPages(5,0,true,ReadingDirection.RTL))
        assertEquals(listOf(2),spreadPages(5,2,false,ReadingDirection.RTL))
    }
    @Test fun scrollingAndSpreadSizeRespectLayout() {
        assertTrue(usesVerticalScrolling(ReadingMode.STRIP,ReaderNavigation.HORIZONTAL))
        assertTrue(usesVerticalScrolling(ReadingMode.DOUBLE,ReaderNavigation.VERTICAL))
        assertFalse(usesVerticalScrolling(ReadingMode.SINGLE,ReaderNavigation.HORIZONTAL))
        assertEquals(2,readingGroupSize(ReadingMode.DOUBLE))
        assertEquals(1,readingGroupSize(ReadingMode.STRIP))
        assertEquals(ReaderNavigation.VERTICAL,ReaderOverride().resolve(ReaderPreferences(navigation = ReaderNavigation.VERTICAL)).navigation)
    }
    @Test fun proxyMethodsMatchComicoIdentifiers() {
        assertEquals("all",ProxyMethod.METHOD_1.apiValue)
        assertEquals("api",ProxyMethod.METHOD_2.apiValue)
        assertEquals("direct",ProxyMethod.METHOD_3.apiValue)
    }
    @Test fun acceptsOnlyTheProvidersApprovedImageHosts() {
        val url = "https://cdn.example.org/page.jpg"
        assertEquals(listOf(url),validatePageUrls(listOf(url),listOf("example.org")))
        for(invalid in listOf("http://cdn.example.org/page.jpg","https://example.org.evil.test/page.jpg","https://user:password@example.org/page.jpg","https://evil-example.org/page.jpg")) {
            try { validatePageUrls(listOf(invalid),listOf("example.org")); fail("Accepted unapproved image URL") } catch(_: IOException) { }
        }
    }
}
