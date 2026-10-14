package moe.comico.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReadingActivityTest {
    private val zone = ZoneId.of("America/Puerto_Rico")
    @Test fun splitsMidnightAndDeduplicatesPagesAcrossTicksAndRanges() {
        val start = Instant.parse("2026-10-13T03:59:58Z")
        val days = recordReadingTime(emptyList(), start, start.plusSeconds(5), zone, "session", "chapter", listOf(0, 1))
        assertEquals(listOf(2000L, 3000L), days.map { it.millis })
        val all = readingInsights(days, InsightRange.ALL, LocalDate.of(2026, 10, 13))
        assertEquals(5000L, all.millis)
        assertEquals(1, all.sessions)
        assertEquals(2, all.pages)
        assertEquals(1, all.chapters)
        assertEquals(2, all.currentStreak)
        assertEquals(2, all.bestStreak)
        assertEquals(days.first(), JSONObject(days.first().toJson().toString()).readingDay())
    }
    @Test fun rejectsStaleIntervalsAndDoesNotTreatMissingDaysAsReading() {
        val start = Instant.parse("2026-10-13T12:00:00Z")
        assertTrue(recordReadingTime(emptyList(), start, start.plusSeconds(60), zone, "s", "c", listOf(0)).isEmpty())
        assertTrue(recordReadingTime(emptyList(), start, start.minusSeconds(1), zone, "s", "c", listOf(0)).isEmpty())
        val today = LocalDate.of(2026, 10, 13)
        val days = listOf(ReadingDay(today.minusDays(10), 5000), ReadingDay(today.minusDays(2), 5000),
            ReadingDay(today.minusDays(1), 5000))
        val stats = readingInsights(days, InsightRange.WEEK, today)
        assertEquals(7, stats.daily.size)
        assertEquals(10000L, stats.millis)
        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.bestStreak)
        assertEquals(0L, stats.daily.last().second)
        assertEquals(0, readingInsights(days, InsightRange.WEEK, today.plusDays(1)).currentStreak)
    }
    @Test fun repeatedPagesAndSessionsAreDistinctAndEmptyDataIsSafe() {
        val start = Instant.parse("2026-10-13T12:00:00Z")
        val first = recordReadingTime(emptyList(), start, start.plusSeconds(5), zone, "s", "c", listOf(0, 1))
        val days = recordReadingTime(first, start.plusSeconds(5), start.plusSeconds(10), zone, "s", "c", listOf(1, 2))
        val stats = readingInsights(days, InsightRange.MONTH, LocalDate.of(2026, 10, 13))
        assertEquals(10000L, stats.millis)
        assertEquals(3, stats.pages)
        assertEquals(1, stats.sessions)
        assertEquals(30, stats.daily.size)
        val empty = readingInsights(emptyList(), InsightRange.ALL, LocalDate.of(2026, 10, 13))
        assertEquals(0L, empty.millis)
        assertEquals(1, empty.daily.size)
        assertEquals(0, empty.bestStreak)
    }
}
