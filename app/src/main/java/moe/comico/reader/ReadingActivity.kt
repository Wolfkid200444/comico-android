package moe.comico.reader

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReadingDay(val date: LocalDate, val millis: Long = 0,
    val sessions: Set<String> = emptySet(), val pages: Set<String> = emptySet(), val chapters: Set<String> = emptySet()) {
    fun toJson() = JSONObject().put("date", date.toString()).put("millis", millis)
        .put("sessions", JSONArray(sessions.toList())).put("pages", JSONArray(pages.toList()))
        .put("chapters", JSONArray(chapters.toList()))
}
fun JSONObject.readingDay(): ReadingDay {
    fun strings(key: String): Set<String> = optJSONArray(key)?.let { a ->
        (0 until a.length()).map { a.getString(it) }.toSet()
    } ?: emptySet()
    return ReadingDay(LocalDate.parse(getString("date")), optLong("millis").coerceAtLeast(0),
        strings("sessions"), strings("pages"), strings("chapters"))
}

fun recordReadingTime(days: List<ReadingDay>, start: Instant, end: Instant, zone: ZoneId,
    session: String, chapter: String, pages: List<Int>): List<ReadingDay> {
    if (!end.isAfter(start) || java.time.Duration.between(start, end).toMillis() > 15_000) return days
    val result = days.associateBy { it.date }.toMutableMap()
    var cursor = start
    while (cursor.isBefore(end)) {
        val date = cursor.atZone(zone).toLocalDate()
        val next = minOf(end, date.plusDays(1).atStartOfDay(zone).toInstant())
        val day = result[date] ?: ReadingDay(date)
        result[date] = day.copy(millis = day.millis + java.time.Duration.between(cursor, next).toMillis(),
            sessions = day.sessions + session, chapters = day.chapters + chapter,
            pages = day.pages + pages.filter { it >= 0 }.map { JSONArray(listOf(chapter, it)).toString() })
        cursor = next
    }
    return result.values.sortedBy { it.date }
}

enum class InsightRange(val label: String, val days: Long?) {
    WEEK("7 days", 7), MONTH("30 days", 30), YEAR("Year", 365), ALL("All time", null)
}
data class ReadingInsights(val millis: Long, val sessions: Int, val pages: Int, val chapters: Int,
    val readingDays: Int, val currentStreak: Int, val bestStreak: Int, val daily: List<Pair<LocalDate, Long>>)

fun readingInsights(days: List<ReadingDay>, range: InsightRange, today: LocalDate): ReadingInsights {
    val start = (range.days?.let { today.minusDays(it - 1) } ?: days.minOfOrNull { it.date } ?: today).coerceAtMost(today)
    val selected = days.filter { it.date in start..today && it.millis > 0 }
    val active = days.filter { it.millis > 0 && it.date <= today }.map { it.date }.distinct().sorted()
    var best = 0
    var streak = 0
    var previous: LocalDate? = null
    active.forEach { date ->
        streak = if (previous?.plusDays(1) == date) streak + 1 else 1
        best = maxOf(best, streak)
        previous = date
    }
    val activeSet = active.toSet()
    var cursor = if (today in activeSet) today else today.minusDays(1)
    var current = 0
    while (cursor in activeSet) { current++; cursor = cursor.minusDays(1) }
    val byDate = selected.associate { it.date to it.millis }
    // Fill missing dates with zero so breaks in reading remain visible.
    val daily = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= today }
        .map { it to (byDate[it] ?: 0L) }.toList()
    return ReadingInsights(selected.sumOf { it.millis }, selected.flatMap { it.sessions }.distinct().size,
        selected.flatMap { it.pages }.distinct().size, selected.flatMap { it.chapters }.distinct().size,
        selected.size, current, best, daily)
}
fun readingDuration(millis: Long): String {
    val seconds = millis / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}min"
        else -> "${seconds / 3600}h ${seconds / 60 % 60}min"
    }
}
