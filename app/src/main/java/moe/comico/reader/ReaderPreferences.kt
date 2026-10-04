package moe.comico.reader

import org.json.JSONObject

enum class ReadingMode(val label: String) { AUTO("Auto by format"), STRIP("Long strip"), SINGLE("Single page"), DOUBLE("Double page") }
enum class PageWidth(val label: String, val fraction: Float) { NARROW("Narrow", .7f), COMFORT("Comfort", .85f), WIDE("Wide", .95f), FULL("Full", 1f) }
enum class ProxyMethod(val label: String, val apiValue: String?) { AUTO("Auto" , null), METHOD_1("Method 1", "all"), METHOD_2("Method 2", "api"), METHOD_3("Method 3", "direct") }
enum class ReaderNavigation(val label: String) { HORIZONTAL("Horizontal swipe"), VERTICAL("Vertical scroll") }
enum class ReadingDirection(val label: String) { LTR("Left to right"), RTL("Right to left") }

data class ReaderPreferences(
    val mode: ReadingMode = ReadingMode.AUTO,
    val width: PageWidth = PageWidth.FULL,
    val proxy: ProxyMethod = ProxyMethod.AUTO,
    val source: String? = null,
    val direction: ReadingDirection = ReadingDirection.LTR,
    val navigation: ReaderNavigation = ReaderNavigation.HORIZONTAL
) {
    fun modeFor(format: String) = if(mode == ReadingMode.AUTO) {
        if(format.lowercase() in listOf("webtoon", "manhwa", "manhua")) ReadingMode.STRIP else ReadingMode.SINGLE
    } else mode
}

data class ReaderOverride(
    val mode: ReadingMode? = null,
    val width: PageWidth? = null,
    val proxy: ProxyMethod? = null,
    val source: String? = null,
    val sourceOverride: Boolean = false,
    val direction: ReadingDirection? = null,
    val navigation: ReaderNavigation? = null,
    val chapterGroups: Map<String, String> = emptyMap()
) {
    fun groupFor(source: String?, language: String) = chapterGroups[org.json.JSONArray(listOf(source, language)).toString()]
    fun withGroup(source: String?, language: String, group: String?): ReaderOverride {
        val key = org.json.JSONArray(listOf(source, language)).toString()
        return copy(chapterGroups = if (group == null) chapterGroups - key else chapterGroups + (key to group))
    }
    fun resolve(global: ReaderPreferences) = ReaderPreferences(mode ?: global.mode, width ?: global.width, proxy ?: global.proxy, if(sourceOverride) source else global.source, direction ?: global.direction, navigation ?: global.navigation)
    fun isEmpty() = mode == null && width == null && proxy == null && !sourceOverride && direction == null && navigation == null && chapterGroups.isEmpty()
}

data class ReaderSource(val id: String, val name: String, val readable: Boolean = true, val provider: String? = null, val chapterCount: Int? = null)
data class ReaderSession(val pages: List<String>, val source: String, val attribution: String, val method: ProxyMethod, val expiresAt: String?)
data class NativeReaderState(val loading: Boolean = false, val session: ReaderSession? = null, val sources: List<ReaderSource> = emptyList(), val error: String? = null, val notice: String? = null, val page: Int = 0, val previous: Chapter? = null, val next: Chapter? = null)

private inline fun <reified T: Enum<T>> parseEnum(value: String): T? = enumValues<T>().firstOrNull { it.name == value }
fun ReaderPreferences.toJson(): JSONObject = JSONObject().put("mode",mode.name).put("width",width.name).put("proxy",proxy.name).put("source",source ?: JSONObject.NULL).put("direction",direction.name).put("navigation",navigation.name)
fun JSONObject.readerPreferences() = ReaderPreferences(parseEnum<ReadingMode>(optString("mode")) ?: ReadingMode.AUTO, parseEnum<PageWidth>(optString("width")) ?: PageWidth.FULL, parseEnum<ProxyMethod>(optString("proxy")) ?: ProxyMethod.AUTO, optString("source").takeUnless { it.isBlank() || it == "null" }, parseEnum<ReadingDirection>(optString("direction")) ?: ReadingDirection.LTR, parseEnum<ReaderNavigation>(optString("navigation")) ?: ReaderNavigation.HORIZONTAL)
fun ReaderOverride.toJson(): JSONObject = JSONObject().put("mode",mode?.name ?: JSONObject.NULL).put("width",width?.name ?: JSONObject.NULL).put("proxy",proxy?.name ?: JSONObject.NULL).put("source",source ?: JSONObject.NULL).put("sourceOverride",sourceOverride).put("direction",direction?.name ?: JSONObject.NULL).put("navigation",navigation?.name ?: JSONObject.NULL).put("chapterGroups", JSONObject(chapterGroups))
fun JSONObject.readerOverride() = ReaderOverride(parseEnum<ReadingMode>(optString("mode")),parseEnum<PageWidth>(optString("width")),parseEnum<ProxyMethod>(optString("proxy")),optString("source").takeUnless { it.isBlank() || it == "null" },optBoolean("sourceOverride"),parseEnum<ReadingDirection>(optString("direction")),parseEnum<ReaderNavigation>(optString("navigation")), optJSONObject("chapterGroups")?.let { groups -> groups.keys().asSequence().associateWith { groups.getString(it) } } ?: emptyMap())

fun spreadPages(pageCount: Int, first: Int, double: Boolean, direction: ReadingDirection): List<Int> {
    val indices = (first until minOf(first + if(double) 2 else 1, pageCount)).toList()
    return if(direction == ReadingDirection.RTL) indices.reversed() else indices
}

fun readingGroupSize(mode: ReadingMode) = if(mode == ReadingMode.DOUBLE) 2 else 1
fun usesVerticalScrolling(mode: ReadingMode, navigation: ReaderNavigation) = mode == ReadingMode.STRIP || navigation == ReaderNavigation.VERTICAL
