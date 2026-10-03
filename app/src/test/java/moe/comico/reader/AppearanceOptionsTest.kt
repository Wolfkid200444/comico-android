package moe.comico.reader

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceOptionsTest {
    private val now = LocalDate.of(2026, 10, 2)
    private val zone = ZoneId.of("UTC")
    @Test fun formatsDefaultAndPaddedDates() {
        val stamp = "2026-10-02T12:00:00Z"
        assertEquals("10/2/26", formatAppDate(stamp, AppearanceOptions(), now, zone))
        assertEquals("10/02/26", formatAppDate(stamp, AppearanceOptions(dateFormat = "MM/dd/yy"), now, zone))
        assertEquals("2026-10-02", formatAppDate(stamp, AppearanceOptions(dateFormat = "yyyy-MM-dd"), now, zone))
    }
    @Test fun relativeDatesUseLocalDay() {
        val options = AppearanceOptions(relativeDates = true)
        assertEquals("Today", formatAppDate("2026-10-02T12:00:00Z", options, now, zone))
        assertEquals("Yesterday", formatAppDate("2026-10-01T12:00:00Z", options, now, zone))
        assertEquals("9/30/26", formatAppDate("2026-09-30T12:00:00Z", options, now, zone))
        assertEquals("Yesterday", formatAppDate("2026-10-02T01:00:00Z", options, now, ZoneId.of("America/Puerto_Rico")))
        assertEquals("invalid", formatAppDate("invalid", options, now, zone))
    }
}
